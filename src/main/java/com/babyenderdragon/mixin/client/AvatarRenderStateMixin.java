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

	@Unique
	private float babyenderdragon$attachY;

	@Unique
	private int babyenderdragon$debugDragonId;

	@Unique
	private double babyenderdragon$debugSeatX;

	@Unique
	private double babyenderdragon$debugSeatY;

	@Unique
	private double babyenderdragon$debugSeatZ;

	@Unique
	private double babyenderdragon$debugPlayerX;

	@Unique
	private double babyenderdragon$debugPlayerY;

	@Unique
	private double babyenderdragon$debugPlayerZ;

	@Unique
	private float babyenderdragon$debugPartialTick;

	@Unique
	private float babyenderdragon$debugDragonYaw;

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

	@Override
	public void babyenderdragon$setAttachY(float attachY) {
		this.babyenderdragon$attachY = attachY;
	}

	@Override
	public float babyenderdragon$getAttachY() {
		return this.babyenderdragon$attachY;
	}

	@Override
	public void babyenderdragon$setDebugDragonId(int id) {
		this.babyenderdragon$debugDragonId = id;
	}

	@Override
	public int babyenderdragon$getDebugDragonId() {
		return this.babyenderdragon$debugDragonId;
	}

	@Override
	public void babyenderdragon$setDebugSeat(double x, double y, double z) {
		this.babyenderdragon$debugSeatX = x;
		this.babyenderdragon$debugSeatY = y;
		this.babyenderdragon$debugSeatZ = z;
	}

	@Override
	public double babyenderdragon$getDebugSeatX() {
		return this.babyenderdragon$debugSeatX;
	}

	@Override
	public double babyenderdragon$getDebugSeatY() {
		return this.babyenderdragon$debugSeatY;
	}

	@Override
	public double babyenderdragon$getDebugSeatZ() {
		return this.babyenderdragon$debugSeatZ;
	}

	@Override
	public void babyenderdragon$setDebugPlayer(double x, double y, double z) {
		this.babyenderdragon$debugPlayerX = x;
		this.babyenderdragon$debugPlayerY = y;
		this.babyenderdragon$debugPlayerZ = z;
	}

	@Override
	public double babyenderdragon$getDebugPlayerX() {
		return this.babyenderdragon$debugPlayerX;
	}

	@Override
	public double babyenderdragon$getDebugPlayerY() {
		return this.babyenderdragon$debugPlayerY;
	}

	@Override
	public double babyenderdragon$getDebugPlayerZ() {
		return this.babyenderdragon$debugPlayerZ;
	}

	@Override
	public void babyenderdragon$setDebugPartialTick(float partialTick) {
		this.babyenderdragon$debugPartialTick = partialTick;
	}

	@Override
	public float babyenderdragon$getDebugPartialTick() {
		return this.babyenderdragon$debugPartialTick;
	}

	@Override
	public void babyenderdragon$setDebugDragonYaw(float yaw) {
		this.babyenderdragon$debugDragonYaw = yaw;
	}

	@Override
	public float babyenderdragon$getDebugDragonYaw() {
		return this.babyenderdragon$debugDragonYaw;
	}
}
