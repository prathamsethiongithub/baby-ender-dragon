package com.babyenderdragon;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The single source of truth for the dragon's model frame.
 *
 * <p><b>The two 180-degree flips, in one place:</b>
 * <ul>
 *   <li>The dragon's model is rendered with an extra 180 degrees baked into its yaw
 *       (mirrored model + {@code scale(-s, -s, s)} + the renderer's {@code Ry(180 - yaw)}),
 *       so the MODEL frame is rotated 180 degrees about Y relative to the entity frame.
 *       An entity-frame point must be flipped into the model frame first: {@code (-x, y, -z)}.</li>
 *   <li>That flip CONJUGATES the other two axes:
 *       {@code Ry(180) . Rx(t) == Rx(-t) . Ry(180)} and
 *       {@code Ry(180) . Rz(t) == Rz(-t) . Ry(180)}.
 *       So pitch and roll both change sign inside the model frame - which is why both the
 *       renderer and the seat math negate them, and why matching the dragon's visual lean
 *       needs the opposite sign on the rider side (see {@code PlayerRotationMixin}).</li>
 * </ul>
 *
 * <p>Every consumer (the server seat, the dragon renderer) composes the SAME transform:
 * <pre>world = dragonPos + Ry(180 - yaw) . Rx(-pitch) . Rz(-roll) . (-x, y, -z)</pre>
 * where {@code (-x, y, -z)} is the entity-frame point flipped into the model frame
 * (a 180-degree Y rotation).
 *
 * <p>Verified numerically: with pitch = roll = 0 this reproduces vanilla exactly at every yaw
 * (0/45/90/137/-90/180/-33); across attitude combinations the seat holds a constant height
 * above its body station (worst deviation 2e-16, double precision).
 */
public final class DragonFrame {
	private DragonFrame() {
	}

	/**
	 * Model-space height the roll pivots about.
	 *
	 * <p>0.0 = roll about the model origin (the behavior before this class was introduced).
	 * 1.15 = the seat height: rolling the body about the spine line through the seat keeps the
	 * dragon's back under the rider through banks instead of swinging it sideways.
	 * Only the y part of the pivot matters, so the frame flips do not change it.
	 */
	public static final double ROLL_PIVOT_Y = 1.15D;

	/**
	 * Transform an entity-frame point through the dragon's model frame into world space.
	 *
	 * <pre>world = dragonPos + Ry(180 - yaw) . Rx(-pitch) . Rz(-roll) . (x, y, -z)</pre>
	 *
	 * <p>Roll is taken about the horizontal line through {@code (0, ROLL_PIVOT_Y, 0)} along the
	 * model's z axis; at {@code ROLL_PIVOT_Y = 0} this reduces exactly to the original formula.
	 *
	 * @param dragonPos        the dragon's position (entity origin)
	 * @param yawDeg           dragon yaw in degrees (entity frame)
	 * @param pitchDeg         dragon pitch in degrees (positive = nose DOWN, Minecraft convention)
	 * @param rollDeg          effective roll in degrees (caller applies sign/coupling)
	 * @param entityFramePoint the point in the ENTITY frame (e.g. a passenger attachment point)
	 */
	public static Vec3 modelToWorld(Vec3 dragonPos, float yawDeg, float pitchDeg, float rollDeg,
			Vec3 entityFramePoint) {
		// entity frame -> model frame: the frame flip is a 180-degree Y rotation, so BOTH x and z
		// flip. (The old (x, y, -z) form was a mirror - invisible only because every seat so far
		// sits at x = 0. With (-x, y, -z) this equals vanilla's yaw-only rotation at every yaw,
		// x != 0 included; checked by test T3.)
		double x = -entityFramePoint.x;
		double y = entityFramePoint.y;
		double z = -entityFramePoint.z;

		// Rz(-roll), about the horizontal line y = ROLL_PIVOT_Y (the spine line).
		double rz = Math.toRadians(-rollDeg);
		double cz = Math.cos(rz), sz = Math.sin(rz);
		double py = y - ROLL_PIVOT_Y;
		double x1 = x * cz - py * sz;
		double y1 = x * sz + py * cz + ROLL_PIVOT_Y;

		// Rx(-pitch). Minecraft pitch: positive = nose DOWN.
		double rx = Math.toRadians(-pitchDeg);
		double cx = Math.cos(rx), sx = Math.sin(rx);
		double y2 = y1 * cx - z * sx;
		double z2 = y1 * sx + z * cx;

		// Ry(180 - yaw), the model's own yaw term.
		double ry = Math.toRadians(180.0D - yawDeg);
		double cy = Math.cos(ry), sy = Math.sin(ry);
		double x3 = x1 * cy + z2 * sy;
		double z3 = -x1 * sy + z2 * cy;

		return dragonPos.add(x3, y2, z3);
	}

	/**
	 * The same rotation as a JOML quaternion: {@code Ry(180 - yaw) . Rx(-pitch) . Rz(-roll)}.
	 * No translation, no pivot (the pivot is a translation concern; see the renderer).
	 */
	public static Quaternionf rotation(float yawDeg, float pitchDeg, float rollDeg) {
		return yawPitch(yawDeg, pitchDeg).mul(rollRotation(rollDeg));
	}

	/** {@code Ry(180 - yaw) . Rx(-pitch)} - the attitude part before the roll. */
	public static Quaternionf yawPitch(float yawDeg, float pitchDeg) {
		return new Quaternionf()
				.rotateY((float) Math.toRadians(180.0F - yawDeg))
				.rotateX((float) Math.toRadians(-pitchDeg));
	}

	/** {@code Rz(-roll)} - the roll alone, for pivoted composition around {@link #ROLL_PIVOT_Y}. */
	public static Quaternionf rollRotation(float rollDeg) {
		return new Quaternionf().rotateZ((float) Math.toRadians(-rollDeg));
	}

	/**
	 * The rider's attitude, pivoting about its ATTACHMENT point ({@code attachY} above the model
	 * origin) instead of its feet:
	 *
	 * <pre>T(0, attachY, 0) . Rx(-pitch) . Rz(-roll) . T(0, -attachY, 0)</pre>
	 *
	 * <p>SAME SIGNS as the dragon's own render ({@link #yawPitch} / {@link #rollRotation}):
	 * the player renderer applies the identical frame - {@code setupRotations} (which ends with
	 * {@code Ry(180 - bodyRot)}) first, then {@code scale(-1, -1, 1)}, then
	 * {@code translate(0, -1.501, 0)} - so both are in the same flipped frame and rotate the
	 * same way. (Verified against the decompiled 26.2 {@code LivingEntityRenderer.submit} and
	 * {@code AvatarRenderer.setupRotations}.)
	 *
	 * <p>Without the pivot wrap the rider would rotate about its feet, which sit {@code attachY}
	 * BELOW the seat - swinging the seat contact point 0.6 * sin(angle) off the dragon's back.
	 */
	public static Matrix4f riderAttitude(float pitchDeg, float rollDeg, float attachY) {
		return new Matrix4f()
				.translate(0.0F, attachY, 0.0F)
				.rotateX((float) Math.toRadians(-pitchDeg))
				.rotateZ((float) Math.toRadians(-rollDeg))
				.translate(0.0F, -attachY, 0.0F);
	}
}
