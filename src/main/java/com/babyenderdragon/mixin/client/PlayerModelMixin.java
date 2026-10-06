package com.babyenderdragon.mixin.client;

import com.babyenderdragon.client.RiderPoseConfig;
import com.babyenderdragon.client.RidingDragonRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rides the player properly instead of leaving them a limp passenger.
 *
 * <p>Injects at TAIL, so vanilla's own riding pose (thighs flexed almost horizontal, arms barely
 * forward) has already been applied and this deliberately overrides it.
 *
 * <p>NO HEAD COUNTER-ROTATION, on purpose. In the humanoid mesh, {@code head} and {@code body} are
 * both direct children of the root part, so the head does NOT inherit the torso lean. Because it is
 * a sibling, leaning the body already leaves the head level; adding the counter-rotation would tip
 * the rider's head back and make them stare at the sky.
 *
 * <p>Verified descriptor against the 26.2 jar:
 * {@code setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V}.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
			at = @At("TAIL"))
	private void babyenderdragon$ridingPose(AvatarRenderState state, CallbackInfo ci) {
		if (!RiderPoseConfig.ENABLED) {
			return;
		}
		if (!((RidingDragonRenderState) state).babyenderdragon$isRidingDragon()) {
			return;
		}

		PlayerModel self = (PlayerModel) (Object) this;

		// Torso leans into the wind.
		self.body.xRot = (float) Math.toRadians(RiderPoseConfig.BODY_LEAN_DEG);

		// Arms forward and slightly wide, gripping near the dragon's shoulders.
		float armFwd = (float) Math.toRadians(RiderPoseConfig.ARM_FORWARD_DEG);
		float armOut = (float) Math.toRadians(RiderPoseConfig.ARM_OUT_DEG);
		self.rightArm.xRot = -armFwd;
		self.leftArm.xRot = -armFwd;
		self.rightArm.zRot = -armOut;
		self.leftArm.zRot = armOut;

		// Legs wrap the flanks. Vanilla's near-horizontal flex buried them inside the dragon,
		// which is what the screenshot showed.
		float flex = (float) Math.toRadians(RiderPoseConfig.LEG_FLEX_DEG);
		float spread = (float) Math.toRadians(RiderPoseConfig.LEG_SPREAD_DEG);
		float yaw = (float) Math.toRadians(RiderPoseConfig.LEG_YAW_DEG);
		self.rightLeg.xRot = -flex;
		self.leftLeg.xRot = -flex;
		self.rightLeg.zRot = spread;
		self.leftLeg.zRot = -spread;
		self.rightLeg.yRot = yaw;
		self.leftLeg.yRot = -yaw;
	}
}
