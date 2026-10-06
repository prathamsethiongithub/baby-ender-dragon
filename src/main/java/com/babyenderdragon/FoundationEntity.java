package com.babyenderdragon;

import com.babyenderdragon.client.ClientRiderInput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Baby Ender Dragon - rideable prototype with a steering flight controller.
 *
 * <p><b>Movement model.</b> The rider's look sets a TARGET orientation. The dragon's OWN yaw and
 * pitch turn gradually toward it, and W thrusts ALONG THE DRAGON'S OWN FACING. The camera is never
 * turned into a velocity vector, so the dragon turns before it moves and its path curves.
 *
 * <p>Three 26.2 facts make riding work at all: getControllingPassenger() (base Entity returns
 * null), isClientAuthoritative() (Player hardcodes true), and getRiddenInput() (the vanilla hook
 * for reading rider controls).
 */
public final class FoundationEntity extends LivingEntity {
	private static final EntityDataAccessor<Boolean> DATA_TAMED =
			SynchedEntityData.defineId(FoundationEntity.class, EntityDataSerializers.BOOLEAN);

	// ------------------------------------------------------------------ flight config
	/** Top speed, blocks per tick (1.4 = 28 blocks/s). Preserved from the tuned build. */
	private static final double MAX_SPEED = 2.8D;
	private static final double ACCEL = MAX_SPEED * 0.04D;
	private static final double DRAG = 0.97D;
	private static final double BRAKE = MAX_SPEED * 0.08D;
	private static final double YAW_EASE = 0.20D;
	private static final double PITCH_EASE = 0.15D;
	private static final double YAW_STEP_MIN = 2.0D;
	private static final double YAW_STEP_MAX = 12.0D;
	private static final double YAW_STEP_PER_SPEED = 1.5D;
	private static final double PITCH_STEP_MAX = 4.0D;
	private static final double PITCH_LIMIT = 60.0D;
	private static final double ASSIST_SLOW = 0.30D;
	private static final double ASSIST_FAST = 0.08D;
	private static final double ASSIST_EASE = 0.20D;
	private static final double VELOCITY_EASE = 0.30D;
	/** A/D steering rate in degrees per tick (turning, NOT strafing). */
	private static final double TURN_RATE = 4.0D;
	/** How fast the steering input ramps in and out. Lower = heavier, more dragon-like. */
	private static final double TURN_EASE = 0.20D;
	/** Degrees of roll per degree of yaw actually achieved, and the roll clamp. */
	private static final double BANK_PER_DEG = 6.0D;
	private static final double BANK_MAX = 35.0D;
	/** Roll easing. Lower = the bank trails the turn more, like mass swinging through an arc. */
	private static final double BANK_EASE = 0.12D;
	/**
	 * Sign of the roll. A real dragon banks INTO the corner. Can't be confirmed without eyes on
	 * the model, so it lives here as one named knob: flip it if the dragon leans outward.
	 */
	private static final double BANK_SIGN = 1.0D;
	/** Vertical speed from Space/Shift, blocks per tick - creative-flight style. */
	private static final double VERTICAL_SPEED = 0.40D;
	/** A sneak press released within this many ticks is a TAP; longer is a descent hold. */
	private static final int TAP_MAX_TICKS = 10;
	/** Ticks allowed from the first tap of a burst to the last, for the dismount gesture. */
	private static final int TAP_WINDOW = 20;
	/** Gentler descent while landing (S + Shift), blocks/tick: settle, do not drop. */
	private static final double LANDING_DESCENT = 0.16D;
	/** Extra descent added while spiralling (A/D + Shift), blocks/tick. */
	private static final double SPIRAL_DESCENT = 0.22D;
	/** Bank gain multiplier while spiralling, so the spiral leans harder than a flat turn. */
	private static final double SPIRAL_BANK_MULT = 1.6D;
	/**
	 * Vertical speed at FULL crosshair pitch, blocks/tick. This turns the crosshair into a
	 * climb/dive control: near 1.0 = a shallow glide, near forward speed = a steep stoop.
	 */
	private static final double PITCH_CLIMB_MAX = 2.4D;
	/** Forward speed fraction at which climb/dive reaches full strength. */
	private static final double CLIMB_FULL_SPEED_FRAC = 0.6D;

	// ---- dive / climb speed trade -------------------------------------------------------
	/** Top speed gained at a full, fully-charged dive (1.0 = +100%). */
	private static final double DIVE_BOOST = 1.0D;
	/** How fast a dive winds up, per tick, and how fast it bleeds off once level. */
	private static final double DIVE_CHARGE_RATE = 0.014D;
	private static final double DIVE_DISCHARGE_RATE = 0.020D;
	/** Exponent on the charge so the payoff runs away from you, the way an elytra dive does. */
	private static final double DIVE_CURVE = 1.6D;
	/** Dive acceleration multiplier - a stoop should feel like it grabs. */
	private static final double DIVE_ACCEL_BOOST = 2.5D;
	/** A climb must be HELD this long before it starts paying out (3 seconds). */
	private static final int CLIMB_SUSTAIN_TICKS = 60;
	/** Then it ramps over this many further ticks. */
	private static final int CLIMB_RAMP_TICKS = 60;
	/** Top speed gained from a fully sustained climb (0.6 = +60%). */
	private static final double CLIMB_BOOST = 0.6D;

	/** Wing-flap phase. Advanced on BOTH sides - see tick(). */
	private float flapTime;
	private float oFlapTime;

	// ------------------------------------------------------------------ flight state
	private double flightSpeed;
	private double vAssist;
	private Vec3 flightVelocity = Vec3.ZERO;
	/** Eased A/D steering rate, degrees per tick. */
	private double turnAssist;
	/** Server-side sneak-tap tracking for the triple-tap dismount gesture. */
	private boolean prevShift;
	private int shiftPressTick;
	private int tapCount;
	/** 0..1, how wound up the current dive is. Dives trade altitude for speed. */
	private double diveCharge;
	/** Consecutive ticks spent climbing, for the sustained-climb payoff. */
	private int climbTicks;
	private int firstTapTick;
	/** Roll into the current turn, degrees. Visual only; the renderer applies it. */
	private float bank;
	private float oBank;
	/**
	 * Ring buffer of recent (y, yaw) samples. The vanilla EnderDragonModel positions its body
	 * segments from this via getHistoricalPos(), which gives the real Ender Dragon its trailing,
	 * sinuous flight.
	 *
	 * <p>DELIBERATELY LEFT EMPTY. Recording into it tore the model apart: the buffer is calibrated
	 * for the vanilla dragon's lumbering turns, and the model turns those historical yaw deltas
	 * into per-segment lateral offsets. Our dragon steers at up to ~16 degrees/tick, an order of
	 * magnitude faster, so every segment landed at its own wild angle - tail out of the hip, no
	 * neck, wings detached. Confirmed against a screenshot. Re-attempting this needs the recorded
	 * yaw rate damped to vanilla's, not a raw record().
	 */
	private final DragonFlightHistory flightHistory = new DragonFlightHistory();
	/** Last sampled position, so the debug line can report MEASURED speed, not just intent. */
	private Vec3 dbgLastPos;

	public FoundationEntity(EntityType<? extends FoundationEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TAMED, true);
	}

	/** Body-follow buffer. The renderer copies this into its render state each frame. */
	public DragonFlightHistory getFlightHistory() {
		return this.flightHistory;
	}

	/** Roll into the turn, in degrees, interpolated for smooth rendering. */
	public float getBank(float partialTick) {
		return Mth.lerp(partialTick, this.oBank, this.bank);
	}

	/**
	 * While a rider is aboard our steering controller is the ONLY owner of this entity's motion.
	 *
	 * <p>Vanilla's travelRidden() calls travel() when this returns true, and it runs inside
	 * super.tick() - so with a rider it was moving the dragon by last tick's velocity AND our
	 * controller moved it again. The dragon measured a real 2.8 blocks/tick against a tuned
	 * MAX_SPEED of 1.4: exactly the double application.
	 */
	/**
	 * Three quick sneak taps dismount; a long press is a descent and resets the count.
	 *
	 * <p>Sneak can no longer dismount on its own - PlayerRidingMixin returns false from
	 * Player#wantsToStopRiding while the vehicle is this entity - so this gesture is the only way
	 * off. stopRiding() is called directly, which bypasses that predicate and still works.
	 *
	 * <p>A "tap" is a press released within TAP_MAX_TICKS. Three of them, with the burst inside
	 * TAP_WINDOW, throws the rider. Any hold in the middle means the rider was descending, so the
	 * count resets instead of firing.
	 */
	private void trackDismountTaps(ServerPlayer serverRider) {
		boolean shift = serverRider.getLastClientInput().shift();

		if (shift && !this.prevShift) {
			this.shiftPressTick = this.tickCount;
		} else if (!shift && this.prevShift) {
			int held = this.tickCount - this.shiftPressTick;

			if (held <= TAP_MAX_TICKS) {
				// Stale burst? Start a fresh one rather than firing on old taps.
				if (this.tapCount > 0 && this.tickCount - this.firstTapTick > TAP_WINDOW) {
					this.tapCount = 0;
				}
				if (this.tapCount == 0) {
					this.firstTapTick = this.shiftPressTick;
				}
				this.tapCount++;
				if (this.tapCount >= 3) {
					this.tapCount = 0;
					serverRider.stopRiding();
				}
			} else {
				// Held long enough to be a descent. That was not a dismount gesture.
				this.tapCount = 0;
			}
		}
		this.prevShift = shift;
	}

	@Override
	public boolean canSimulateMovement() {
		return getControllingPassenger() == null && super.canSimulateMovement();
	}

	/** Sign for the seat's roll. Flip if the rider swings the wrong way through a bank. */
	private static final double SEAT_ROLL_SIGN = 1.0D;
	/**
	 * How much of the dragon's roll the SEAT takes on. 1.0 = rigidly locked to the rolled back.
	 *
	 * <p>History: at 1.0 with the roll pivoting at the model ORIGIN, the seat swung
	 * 1.15 * sin(35) = 0.66 blocks sideways at full bank and read as sliding off - so it was
	 * damped to 0.35 as a fudge. With the roll now pivoting about the spine line through
	 * {@link DragonFrame#ROLL_PIVOT_Y} (the seat height), the seat sits ON the roll axis and
	 * full coupling no longer slides it: set to 1.0.
	 */
	private static final double SEAT_ROLL_COUPLING = 1.0D;

	/**
	 * Pins the rider to the dragon's BACK through dives, climbs and banks.
	 *
	 * <p>Vanilla rotates the seat by YAW ALONE; this dragon pitches up to 60 degrees and rolls 35,
	 * so the seat must follow the body's attitude or the rider reads as floating/sunk no matter
	 * what seat height is picked. The transform (and the two 180-degree flips that shape it) now
	 * lives in ONE place: {@link DragonFrame#modelToWorld}.
	 */
	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		Vec3 seat = getPassengerAttachmentPoint(passenger, getDimensions(getPose()), 1.0F);
		return DragonFrame.modelToWorld(position(), getYRot(), getXRot(),
				(float) (this.bank * SEAT_ROLL_SIGN * SEAT_ROLL_COUPLING), seat);
	}

	/**
	 * The seat in world space, interpolated at partialTick - the same transform as
	 * {@link #getPassengerRidingPosition}, sampled with the dragon's RENDER-time values so the
	 * rider's drawing (the client weld in AvatarRendererMixin) matches the dragon's interpolated
	 * pose. Per-passenger: the attachment point resolves seat 0 / seat 1 automatically.
	 */
	public Vec3 getSeatWorld(Entity passenger, float partialTick) {
		Vec3 seat = getPassengerAttachmentPoint(passenger, getDimensions(getPose()), 1.0F);
		float roll = (float) (getBank(partialTick) * SEAT_ROLL_SIGN * SEAT_ROLL_COUPLING);
		return DragonFrame.modelToWorld(getPosition(partialTick), getYRot(partialTick),
				getXRot(partialTick), roll, seat);
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		Entity rider = getFirstPassenger();
		return rider instanceof LivingEntity living ? living : null;
	}

	/** Flyer: no gravity. Without this the dragon walks like a pig and sinks through terrain. */
	@Override
	public boolean isNoGravity() {
		return true;
	}

	/** Right-click to mount. Without this the entity can only be ridden via /ride. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitPos) {
		if (!level().isClientSide() && getPassengers().isEmpty()) {
			player.startRiding(this);
			return InteractionResult.SUCCESS;
		}
		return super.interact(player, hand, hitPos);
	}

	/** Base allows 1 passenger; we declare 2 seats. */
	@Override
	public boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < 2 && super.canAddPassenger(passenger);
	}

	/**
	 * Reads rider controls from ServerPlayer.getLastClientInput(). The old Player.xxa/.zza float
	 * fields are CLIENT-ONLY on 26.2 (zero writers tree-wide), so reading them server-side always
	 * yielded 0 - the original cause of the never-moving dragon.
	 *
	 * <p>Returns RAW key state: x = strafe (A/D), y = vertical (Space/Shift), z = forward (W/S).
	 */
	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 localInput) {
		// ServerPlayer carries the last input packet it received; the local player exposes the
		// live key state. Everything downstream consumes this one neutral Vec3.
		Input keys = null;
		if (rider instanceof ServerPlayer serverPlayer) {
			keys = serverPlayer.getLastClientInput();
		} else if (level().isClientSide()) {
			keys = ClientRiderInput.keys(rider);
		}
		if (keys == null) {
			return Vec3.ZERO;
		}
		double forward = (keys.forward() ? 1.0D : 0.0D) - (keys.backward() ? 1.0D : 0.0D);
		double strafe = (keys.left() ? 1.0D : 0.0D) - (keys.right() ? 1.0D : 0.0D);
		double vertical = (keys.jump() ? 1.0D : 0.0D) - (keys.shift() ? 1.0D : 0.0D);
		return new Vec3(strafe, vertical, forward);
	}

	@Override
	public void tick() {
		LivingEntity rider = getControllingPassenger();

		super.tick();

		// ---- wing clock ------------------------------------------------------------
		// Must run on BOTH sides. It used to run server-only, so the client's copy stayed 0 and the
		// model's "flapTime * 2pi" jaw/wing rotations never animated: the wings looked frozen.
		double horizontal = Math.sqrt(getDeltaMovement().x * getDeltaMovement().x
				+ getDeltaMovement().z * getDeltaMovement().z);
		float flapSpeed = 0.075F / ((float) horizontal * 4.0F + 1.0F);
		flapSpeed *= (float) Math.pow(2.0D, Mth.clamp(getDeltaMovement().y, -4.0D, 4.0D));
		this.oFlapTime = this.flapTime;
		this.flapTime += flapSpeed;


		if (rider == null) {
			// Unridden: hover, bleed momentum, reset controller state for the next rider.
			this.flightSpeed = 0.0D;
			this.vAssist = 0.0D;
			this.turnAssist = 0.0D;
			this.flightVelocity = Vec3.ZERO;
			// Level out when nobody is flying it.
			this.oBank = this.bank;
			this.bank = (float) Mth.lerp(BANK_EASE, this.bank, 0.0D);
			if (isLocalInstanceAuthoritative()) {
				setDeltaMovement(getDeltaMovement().scale(0.9D));
			}
			return;
		}

		// ---- triple-tap dismount, SERVER side -------------------------------------------
		// stopRiding() is only authoritative on the server, and the controller below runs only on
		// the riding client, so this is deliberately its own block rather than part of the flight
		// path.
		if (!level().isClientSide() && rider instanceof ServerPlayer serverRider) {
			trackDismountTaps(serverRider);
		}

		if (!isLocalInstanceAuthoritative()) {
			// The other side owns this entity's motion and will report the position to us.
			// Server-authoritative flight looked like it worked on the server while the rider's
			// client sat still, because the ridden-mount sync path expects the client to drive.
			return;
		}

		Player player = rider instanceof Player p ? p : null;
		Vec3 input = player != null ? getRiddenInput(player, Vec3.ZERO) : Vec3.ZERO;
		double fwdInput = input.z;      // +1 = W (forward), -1 = S (reverse)
		double strafeInput = input.x;   // +1 = A (left),    -1 = D (right)
		boolean thrust = fwdInput > 0.0D;
		boolean upKey = input.y > 0.0D;
		boolean downKey = input.y < 0.0D;
		// Shift is overloaded by intent: plain descent, a gentler landing settle when paired with
		// S, or a descending spiral when paired with steering. One key, three shaped responses.
		boolean landing = downKey && fwdInput < 0.0D;
		boolean spiralling = downKey && strafeInput != 0.0D;

		// ---- 1. yaw: turn toward the rider's heading, bounded per tick ---------------
		double prevYaw = getYRot();
		double yawDiff = Mth.wrapDegrees(rider.getYRot() - getYRot());
		double maxStep = Mth.clamp(YAW_STEP_MIN + YAW_STEP_PER_SPEED * flightSpeed,
				YAW_STEP_MIN, YAW_STEP_MAX);
		double yawStep = Mth.clamp(yawDiff * YAW_EASE, -maxStep, maxStep);
		// A/D STEER: they rotate the dragon's body, so it TURNS and only then flies the new
		// heading. They deliberately do NOT translate.
		// Yaw DECREASES turning left in Minecraft, hence the minus: A is strafeInput +1
		// (left) and D is -1 (right). Routed through turnAssist so the input ramps in and
		// out instead of snapping the heading -- that snap is what read as robotic.
		this.turnAssist = Mth.lerp(TURN_EASE, this.turnAssist, strafeInput * TURN_RATE);
		yawStep -= this.turnAssist;
		setYRot((float) (getYRot() + yawStep));
		setYBodyRot(getYRot());

		// ---- 1b. bank: roll INTO the corner -----------------------------------------
		// Derived from the heading actually achieved this tick, so the roll tracks the real arc
		// (steering input plus the pull toward the rider's aim), not a canned tilt.
		double achieved = Mth.wrapDegrees(getYRot() - prevYaw);
		this.oBank = this.bank;
		double bankGain = spiralling ? BANK_PER_DEG * SPIRAL_BANK_MULT : BANK_PER_DEG;
		this.bank = (float) Mth.lerp(BANK_EASE, this.bank,
				Mth.clamp(achieved * bankGain * BANK_SIGN, -BANK_MAX, BANK_MAX));

		// ---- 2. pitch: ease toward rider pitch (positive = looking DOWN) -------------
		// Landing levels the nose -- a controlled settle, not a nose-down fall.
		double targetPitch = landing ? 0.0D : Mth.clamp(rider.getXRot(), -PITCH_LIMIT, PITCH_LIMIT);
		setXRot((float) (getXRot() + Mth.clamp((targetPitch - getXRot()) * PITCH_EASE,
				-PITCH_STEP_MAX, PITCH_STEP_MAX)));

		// ---- 3. speed: W thrusts, S brakes, release drags ---------------------------
		// Dive/climb trade, computed from the dragon's own pitch BEFORE the clamp so it applies
		// on this tick rather than lagging one.
		double pitchFracNow = Mth.clamp(Math.abs(getXRot()) / PITCH_LIMIT, 0.0D, 1.0D);
		double pitchShape = pitchFracNow * pitchFracNow;
		double diveFactor = getXRot() > 0.0D ? pitchShape : 0.0D;      // positive pitch = nose down
		double climbFactor = getXRot() < 0.0D ? pitchShape : 0.0D;

		if (diveFactor > 0.1D) {
			this.diveCharge = Math.min(1.0D, this.diveCharge + DIVE_CHARGE_RATE);
		} else {
			this.diveCharge = Math.max(0.0D, this.diveCharge - DIVE_DISCHARGE_RATE);
		}
		// A climb only pays out once it has been HELD - a quick nose-up should not hand out speed.
		if (climbFactor > 0.1D) {
			this.climbTicks++;
		} else {
			this.climbTicks = 0;
		}
		double climbSustain = Mth.clamp((this.climbTicks - CLIMB_SUSTAIN_TICKS)
				/ (double) CLIMB_RAMP_TICKS, 0.0D, 1.0D);
		double diveGain = Math.pow(this.diveCharge, DIVE_CURVE) * diveFactor;
		double speedCap = MAX_SPEED * (1.0D + DIVE_BOOST * diveGain
				+ CLIMB_BOOST * climbSustain * climbFactor);
		double accel = ACCEL * (1.0D + DIVE_ACCEL_BOOST * diveGain);

		if (fwdInput > 0.0D) {
			flightSpeed += accel;
		} else if (fwdInput < 0.0D) {
			flightSpeed -= BRAKE;   // S now REVERSES, not just brakes
		} else {
			flightSpeed *= DRAG;
		}
		// Reverse stays capped at half of BASE speed; forward gets the dive/climb ceiling.
		flightSpeed = Mth.clamp(flightSpeed, -MAX_SPEED * 0.5D, speedCap);
		if (Math.abs(flightSpeed) < 0.01D && fwdInput == 0.0D) {
			flightSpeed = 0.0D;
		}

		// ---- 4. forward vector from the DRAGON's own orientation, never the camera ---
		// HORIZONTAL hull. The crosshair's vertical effect is applied separately below, as a
		// shaped climb/dive, rather than by tilting this vector.
		double yr = Math.toRadians(getYRot());
		Vec3 forward = new Vec3(-Math.sin(yr), 0.0D, Math.cos(yr));

		// ---- 4b. crosshair climb / dive -------------------------------------------------
		// SQUARED response on purpose. A linear link is what made the dragon drift upward whenever
		// the crosshair sat slightly high - the original complaint. Squaring gives a dead zone
		// near level flight while a steep look still produces a genuine stoop:
		//   15 deg -> ~3%   (level flight stays level)
		//   45 deg -> ~56%  (a real dive)
		//   60 deg -> 100%  (full stoop / steep climb)
		// Minecraft pitch is positive looking DOWN, so positive pitch must descend.
		double pitchFrac = Mth.clamp(Math.abs(getXRot()) / PITCH_LIMIT, 0.0D, 1.0D);
		double speedFrac = Mth.clamp(Math.abs(flightSpeed) / (MAX_SPEED * CLIMB_FULL_SPEED_FRAC),
				0.0D, 1.0D);
		double climb = -Math.signum(getXRot()) * (pitchFrac * pitchFrac)
				* PITCH_CLIMB_MAX * speedFrac;

		// ---- 5. smoothed vertical assistance (Space up / Shift down) ----------------
		// Flat vertical speed, eased. Space ascends, Shift descends, exactly like creative flight.
		double descendSpeed = landing ? LANDING_DESCENT
				: (spiralling ? VERTICAL_SPEED + SPIRAL_DESCENT : VERTICAL_SPEED);
		double targetAssist = upKey ? VERTICAL_SPEED : (downKey ? -descendSpeed : 0.0D);
		this.vAssist += (targetAssist - this.vAssist) * ASSIST_EASE;

		if (this.tickCount % 20 == 0) {
			Vec3 now = position();
			double moved = this.dbgLastPos == null ? 0.0D : now.distanceTo(this.dbgLastPos) / 20.0D;
			this.dbgLastPos = now;
			com.babyenderdragon.BabyEnderDragon.LOGGER.info(
					"[flight] input=({},{},{}) thrust={} speed={} delta=({},{},{}) moved={}bt pos=({},{},{}) yaw={} pitch={}",
					input.x, input.y, input.z, thrust,
						String.format("%.3f", flightSpeed) + " climb=" + String.format("%.2f", climb)
								+ " cap=" + String.format("%.2f", speedCap)
								+ " dive=" + String.format("%.2f", diveGain)
								+ " climbSust=" + String.format("%.2f", climbSustain),
					String.format("%.3f", getDeltaMovement().x), String.format("%.3f", getDeltaMovement().y),
					String.format("%.3f", getDeltaMovement().z), String.format("%.3f", moved),
					String.format("%.2f", now.x), String.format("%.2f", now.y), String.format("%.2f", now.z),
					String.format("%.1f", getYRot()), String.format("%.1f", getXRot()));
		}

		// ---- 6. velocity follows the body; the existing collision-aware move() applies it
		Vec3 desired = forward.scale(flightSpeed).add(0.0D, this.vAssist + climb, 0.0D);
		this.flightVelocity = this.flightVelocity.add(
				desired.subtract(this.flightVelocity).scale(VELOCITY_EASE));
		setDeltaMovement(this.flightVelocity);
		move(MoverType.SELF, getDeltaMovement());
		// Adopt move()'s result so collisions actually stop us instead of building phantom speed.
		this.flightVelocity = getDeltaMovement();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Tamed", isTame());
		output.putFloat("FlapTime", flapTime);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setTame(input.getBooleanOr("Tamed", true));
		this.flapTime = input.getFloatOr("FlapTime", 0.0F);
	}

	public boolean isTame() {
		return getEntityData().get(DATA_TAMED);
	}

	public void setTame(boolean tame) {
		getEntityData().set(DATA_TAMED, tame);
	}

	/** Client render accessor: flap phase interpolated across the last tick. */
	public float getFlapTime(float partialTick) {
		return this.oFlapTime + (this.flapTime - this.oFlapTime) * partialTick;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}
}