package com.babyenderdragon.client;

/**
 * Implemented on {@code AvatarRenderState} by a mixin.
 *
 * <p>The render state carries no vehicle reference of its own - only {@code isPassenger}, which is
 * equally true for boats, horses and minecarts. So a flag has to ride along on the state to reach
 * the model.
 */
public interface RidingDragonRenderState {
	void babyenderdragon$setRidingDragon(boolean riding);

	boolean babyenderdragon$isRidingDragon();

	void babyenderdragon$setDragonRoll(float roll);

	float babyenderdragon$getDragonRoll();

	void babyenderdragon$setDragonPitch(float pitch);

	float babyenderdragon$getDragonPitch();

	void babyenderdragon$setAttachY(float attachY);

	float babyenderdragon$getAttachY();

	// ---- self-check stash (written only when -Dbabyenderdragon.selfcheck=true) -------------

	void babyenderdragon$setDebugDragonId(int id);

	int babyenderdragon$getDebugDragonId();

	void babyenderdragon$setDebugSeat(double x, double y, double z);

	double babyenderdragon$getDebugSeatX();

	double babyenderdragon$getDebugSeatY();

	double babyenderdragon$getDebugSeatZ();

	void babyenderdragon$setDebugPlayer(double x, double y, double z);

	double babyenderdragon$getDebugPlayerX();

	double babyenderdragon$getDebugPlayerY();

	double babyenderdragon$getDebugPlayerZ();

	void babyenderdragon$setDebugPartialTick(float partialTick);

	float babyenderdragon$getDebugPartialTick();

	void babyenderdragon$setDebugDragonYaw(float yaw);

	float babyenderdragon$getDebugDragonYaw();
}
