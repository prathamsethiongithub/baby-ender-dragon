package com.babyenderdragon.client;

/**
 * Every tunable for the rider's posture, in one place so tuning is a one-line edit.
 * All values are DEGREES.
 */
public final class RiderPoseConfig {
	private RiderPoseConfig() {
	}

	/** Forward torso lean while riding. A rider on a flying creature leans into the wind. */
	public static final float BODY_LEAN_DEG = 12.0F;

	/** Arms swing forward-down to grip, instead of hanging limp at the sides. */
	public static final float ARM_FORWARD_DEG = 30.0F;
	/** Arms pushed outward, so the hands sit wide near the dragon's shoulders. */
	public static final float ARM_OUT_DEG = 10.0F;

	/**
	 * Legs: how far the thighs come forward. Deliberately much less than vanilla's riding value
	 * of -81 deg. Vanilla flexes them almost horizontal, which on a wide dragon buries them inside
	 * the body - that is exactly what the screenshot showed.
	 */
	public static final float LEG_FLEX_DEG = 28.0F;
	/** Legs splayed outward so they wrap the flanks and stay visible from behind. */
	public static final float LEG_SPREAD_DEG = 32.0F;
	/** Legs angled slightly outward along the body. */
	public static final float LEG_YAW_DEG = 6.0F;

	/**
	 * Sign of the rider's bank. The dragon rolls inside a yaw frame with an extra 180 deg flip
	 * (Ry(180).Rz(t) == Rz(-t).Ry(180)); the rider's frame has no flip, so matching visually needs
	 * the opposite sign. Flip this if the rider ever leans AGAINST the turn.
	 */
	public static final float PLAYER_ROLL_SIGN = 1.0F;

	/**
	 * Sign of the rider's pitch. Kept as a knob for parity with PLAYER_ROLL_SIGN: the dragon's
	 * render frame carries an extra 180 deg yaw flip, so if the rider ever noses UP while the
	 * dragon dives, flip this instead of editing the mixin.
	 */
	public static final float PLAYER_PITCH_SIGN = 1.0F;

	/** Set false to fall back to vanilla's default riding pose for comparison. */
	public static final boolean ENABLED = true;
}
