package com.babyenderdragon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Debug-only seat self-check collector for the rider weld.
 *
 * <p>Enabled with {@code -Dbabyenderdragon.selfcheck=true}. With the property off, every hook
 * returns on the first line and nothing here runs - behavior and cost are unchanged.
 *
 * <p>Two hooks feed it, both in the REAL render path:
 * <ul>
 *   <li><b>H1</b> ({@link #onDragonPose}) - called by the dragon renderer after the roll pivot is
 *       applied; records the dragon pose matrix's rotation, keyed by entity id and frame.</li>
 *   <li><b>H2</b> ({@link #onRiderPose}) - called by PlayerRotationMixin right after the rider's
 *       attitude is applied; computes the hip world position from the pose matrix (plus the
 *       camera's world position - the 26.2 pose stack is camera-relative).</li>
 * </ul>
 *
 * <p>Orientation error: the angle between the rider rotation and the dragon rotation of the SAME
 * render frame (trace of dragonRot^T * riderRot, so the camera rotation common to both cancels).
 * The two hooks can run in either order within a frame, so each rider sample is finalized against
 * BOTH adjacent dragon records (the one before it and the one after it) and keeps the SMALLER
 * error - that selects the dragon rotation of the sample's own frame whichever order the renderer
 * used, and stays fully sensitive to real mismatches (a wrong attitude is far from both records).
 *
 * <p>Thread-safe: the hooks run on the render thread; the client gametest reads from the gametest
 * thread. All public methods are synchronized.
 */
public final class SeatSelfCheck {
	public static final boolean ENABLED = Boolean.getBoolean("babyenderdragon.selfcheck");
	public static final String MUTATE = System.getProperty("babyenderdragon.selfcheck.mutate", "");
	/** Negative control: rotate the rider about its feet (the old no-pivot bug). */
	public static final boolean MUTATE_FEET = ENABLED && "feet".equals(MUTATE);
	/** Negative control: the old +pitch/+roll signs. */
	public static final boolean MUTATE_SIGNS = ENABLED && "signs".equals(MUTATE);

	private SeatSelfCheck() {
	}

	public static final class Sample {
		public float partialTick;
		public double hipError;
		public double rotErrorDeg;
		public double camDelta;
		public float riderFwdY;
		public float dragonFwdY;
		public float riderUpX;
		public float dragonUpX;
		public float riderBodyYaw;
		public float dragonYaw;
		public String riderEuler = "?";
		public String dragonEuler = "?";
	}

	/** A rider sample waiting for the next dragon record to finalize its orientation error. */
	private static final class Pending {
		Matrix3f riderRot;
		float riderFwdY;
		float riderUpX;
		double hipError;
		double camDelta;
		float partialTick;
		float riderBodyYaw;
		float dragonYaw;
		String riderEuler;
		double errVsLast;
		float fwdLast;
		float upLast;
		String eulerLast;
	}

	public static final class Stats {
		public String scenario = "?";
		public int samples;
		public double maxHipError;
		public double maxRotError;
		public double maxCamDelta;
		public double maxFwdMatch;
		public double maxUpMatch;
		public boolean allFwdNegative = true;
		public boolean allFwdPositive = true;
		public boolean upSameSide = true;
		public double avgRiderFwdY;
		public double avgDragonFwdY;
		public double avgRiderUpX;
		public double avgDragonUpX;
		public String firstRiderEuler = "?";
		public String firstDragonEuler = "?";
		public float firstRiderBodyYaw;
		public float firstDragonYaw;
	}

	// ---- scenario state (render thread writes, gametest thread reads) ---------------------
	private static final List<Sample> samples = new ArrayList<>();
	private static final List<Pending> pending = new ArrayList<>();
	private static String scenario = null;
	private static Matrix3f lastDragonRot = null;
	private static final Map<Integer, Matrix3f> dragonRotById = new HashMap<>();

	public static synchronized void beginScenario(String name) {
		if (!ENABLED) {
			return;
		}
		scenario = name;
		samples.clear();
		pending.clear();
	}

	public static synchronized int sampleCount() {
		return samples.size();
	}

	public static synchronized Stats endScenario() {
		finalizePendingWith(lastDragonRot); // trailing samples
		Stats s = new Stats();
		if (scenario != null) {
			s.scenario = scenario;
		}
		s.samples = samples.size();
		double sumRFwd = 0, sumDFwd = 0, sumRUp = 0, sumDUp = 0;
		for (Sample sm : samples) {
			s.maxHipError = Math.max(s.maxHipError, sm.hipError);
			s.maxRotError = Math.max(s.maxRotError, sm.rotErrorDeg);
			s.maxCamDelta = Math.max(s.maxCamDelta, sm.camDelta);
			s.maxFwdMatch = Math.max(s.maxFwdMatch, Math.abs(sm.riderFwdY - sm.dragonFwdY));
			s.maxUpMatch = Math.max(s.maxUpMatch, Math.abs(sm.riderUpX - sm.dragonUpX));
			if (!(sm.riderFwdY < 0)) {
				s.allFwdNegative = false;
			}
			if (!(sm.riderFwdY > 0)) {
				s.allFwdPositive = false;
			}
			if (!(sm.riderUpX * sm.dragonUpX >= -1.0e-6)) {
				s.upSameSide = false;
			}
			sumRFwd += sm.riderFwdY;
			sumDFwd += sm.dragonFwdY;
			sumRUp += sm.riderUpX;
			sumDUp += sm.dragonUpX;
		}
		if (!samples.isEmpty()) {
			s.avgRiderFwdY = sumRFwd / samples.size();
			s.avgDragonFwdY = sumDFwd / samples.size();
			s.avgRiderUpX = sumRUp / samples.size();
			s.avgDragonUpX = sumDUp / samples.size();
			Sample first = samples.get(0);
			s.firstRiderEuler = first.riderEuler;
			s.firstDragonEuler = first.dragonEuler;
			s.firstRiderBodyYaw = first.riderBodyYaw;
			s.firstDragonYaw = first.dragonYaw;
		}
		scenario = null;
		samples.clear();
		pending.clear();
		return s;
	}

	// ---- H1: dragon renderer, after the roll pivot ----------------------------------------
	public static synchronized void onDragonPose(int entityId, Matrix4f poseMatrix) {
		if (!ENABLED) {
			return;
		}
		lastDragonRot = rotationOf(poseMatrix);
		dragonRotById.put(entityId, lastDragonRot);
		finalizePendingWith(lastDragonRot);
	}

	private static void finalizePendingWith(Matrix3f dragonRot) {
		if (dragonRot == null) {
			return;
		}
		for (Pending p : pending) {
			double errThis = angleDeg(p.riderRot, dragonRot);
			Vector3f fwdThis = dragonRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
			Vector3f upThis = dragonRot.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			Sample sm = new Sample();
			sm.partialTick = p.partialTick;
			sm.hipError = p.hipError;
			sm.camDelta = p.camDelta;
			sm.riderBodyYaw = p.riderBodyYaw;
			sm.riderEuler = p.riderEuler;
			sm.riderFwdY = p.riderFwdY;
			sm.riderUpX = p.riderUpX;
			sm.dragonYaw = p.dragonYaw;
			if (errThis <= p.errVsLast) {
				sm.rotErrorDeg = errThis;
				sm.dragonFwdY = fwdThis.y;
				sm.dragonUpX = upThis.x;
				sm.dragonEuler = euler(dragonRot);
			} else {
				sm.rotErrorDeg = p.errVsLast;
				sm.dragonFwdY = p.fwdLast;
				sm.dragonUpX = p.upLast;
				sm.dragonEuler = p.eulerLast;
			}
			samples.add(sm);
		}
		pending.clear();
	}

	// ---- H2: rider attitude, right after the attitude mulPose -----------------------------
	public static synchronized void onRiderPose(int dragonId, Matrix4f riderPose,
			double seatX, double seatY, double seatZ,
			double playerX, double playerY, double playerZ,
			float attachY, float partialTick,
			double camX, double camY, double camZ,
			float riderBodyYaw, float dragonYaw) {
		if (!ENABLED || scenario == null || lastDragonRot == null) {
			return; // nothing recorded yet - skip, do not fake.
		}

		// The pose stack is CAMERA-RELATIVE in 26.2: EntityRenderDispatcher translates by
		// (state.x - camera.pos.x, ...) before calling the renderer, so a stack point plus the
		// camera's world position is a world point.
		Vector3f hip = riderPose.transformPosition(new Vector3f(0.0F, attachY, 0.0F));
		double hipX = hip.x + camX;
		double hipY = hip.y + camY;
		double hipZ = hip.z + camZ;

		Matrix3f riderRot = rotationOf(riderPose);
		Vector3f riderFwd = riderRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
		Vector3f riderUp = riderRot.transform(new Vector3f(0.0F, 1.0F, 0.0F));

		Pending p = new Pending();
		p.riderRot = riderRot;
		p.riderFwdY = riderFwd.y;
		p.riderUpX = riderUp.x;
		p.partialTick = partialTick;
		p.riderBodyYaw = riderBodyYaw;
		p.dragonYaw = dragonYaw;
		p.riderEuler = euler(riderRot);
		p.hipError = Math.sqrt((hipX - seatX) * (hipX - seatX)
				+ (hipY - seatY) * (hipY - seatY)
				+ (hipZ - seatZ) * (hipZ - seatZ));
		p.camDelta = Math.sqrt((playerX - hipX) * (playerX - hipX)
				+ (playerY - (hipY - attachY)) * (playerY - (hipY - attachY))
				+ (playerZ - hipZ) * (playerZ - hipZ));
		p.errVsLast = angleDeg(riderRot, lastDragonRot);
		p.fwdLast = lastDragonRot.transform(new Vector3f(0.0F, 0.0F, -1.0F)).y;
		p.upLast = lastDragonRot.transform(new Vector3f(0.0F, 1.0F, 0.0F)).x;
		p.eulerLast = euler(lastDragonRot);
		pending.add(p);
	}

	/** Angle in degrees between two rotation matrices: acos((trace(a^T * b) - 1) / 2). */
	private static double angleDeg(Matrix3f a, Matrix3f b) {
		Matrix3f rel = new Matrix3f(a).transpose().mul(b);
		double trace = rel.m00() + rel.m11() + rel.m22();
		double cos = Math.max(-1.0, Math.min(1.0, (trace - 1.0) / 2.0));
		return Math.toDegrees(Math.acos(cos));
	}

	/**
	 * Negative-control attitudes (debug-only; selected by -Dbabyenderdragon.selfcheck.mutate).
	 * Returns {@code correct} unless a mutation is active, so with the property unset this is a
	 * pass-through.
	 */
	public static Matrix4f mutatedAttitude(float pitchDeg, float rollDeg, float attachY, Matrix4f correct) {
		if (MUTATE_FEET) {
			// The old bug: rotate the rider about its feet (no pivot translation).
			return new Matrix4f()
					.rotateX((float) Math.toRadians(-pitchDeg))
					.rotateZ((float) Math.toRadians(-rollDeg));
		}
		if (MUTATE_SIGNS) {
			// The old signs: +pitch, +roll (pivot kept, to isolate the sign variable).
			return new Matrix4f()
					.translate(0.0F, attachY, 0.0F)
					.rotateX((float) Math.toRadians(pitchDeg))
					.rotateZ((float) Math.toRadians(rollDeg))
					.translate(0.0F, -attachY, 0.0F);
		}
		return correct;
	}

	/** Rotation part of a pose matrix, with columns normalized (guards any uniform scale). */
	private static Matrix3f rotationOf(Matrix4f m) {
		Matrix3f rot = new Matrix3f(m);
		for (int c = 0; c < 3; c++) {
			float x = rot.get(c, 0);
			float y = rot.get(c, 1);
			float z = rot.get(c, 2);
			float len = (float) Math.sqrt(x * x + y * y + z * z);
			if (len > 1.0e-6F) {
				rot.set(c, 0, x / len);
				rot.set(c, 1, y / len);
				rot.set(c, 2, z / len);
			}
		}
		return rot;
	}

	/** "yaw=.. pitch=.. roll=.." for a rotation matrix (JOML YXZ euler convention). */
	private static String euler(Matrix3f rot) {
		Quaternionf q = new Quaternionf().setFromNormalized(new Matrix4f().set(rot));
		Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
		return String.format("yaw=%.1f pitch=%.1f roll=%.1f",
				Math.toDegrees(e.x), Math.toDegrees(e.y), Math.toDegrees(e.z));
	}
}
