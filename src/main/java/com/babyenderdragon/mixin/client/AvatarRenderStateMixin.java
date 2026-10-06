package com.babyenderdragon.mixin.client;

import com.babyenderdragon.client.RidingDragonRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements RidingDragonRenderState {
	@Unique
	private boolean babyenderdragon$ridingDragon;

	@Unique
	private float babyenderdragon$dragonRoll;

	@Unique
	private float babyenderdragon$dragonPitch;

	@Override
	public void babyenderdragon$setRidingDragon(boolean riding) {
		this.babyenderdragon$ridingDragon = riding;
	}

	@Override
	public boolean babyenderdragon$isRidingDragon() {
		return this.babyenderdragon$ridingDragon;
	}

	@Override
	public void babyenderdragon$setDragonRoll(float roll) {
		this.babyenderdragon$dragonRoll = roll;
	}

	@Override
	public float babyenderdragon$getDragonRoll() {
		return this.babyenderdragon$dragonRoll;
	}

	@Override
	public void babyenderdragon$setDragonPitch(float pitch) {
		this.babyenderdragon$dragonPitch = pitch;
	}

	@Override
	public float babyenderdragon$getDragonPitch() {
		return this.babyenderdragon$dragonPitch;
	}
}
