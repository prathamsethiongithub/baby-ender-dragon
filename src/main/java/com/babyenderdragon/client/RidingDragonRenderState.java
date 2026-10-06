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
}
