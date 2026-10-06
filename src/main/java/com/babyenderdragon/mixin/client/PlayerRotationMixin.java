package com.babyenderdragon.mixin.client;

import com.babyenderdragon.client.RiderPoseConfig;
import com.babyenderdragon.client.RidingDragonRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Banks the rider along with the dragon.
 *
 * <p>Vanilla renders a passenger upright no matter what the vehicle is doing, so on a banking
 * dragon the rider stays bolt upright and reads as perched rather than riding.
 *
 * <p>SIGN: the dragon applies its roll INSIDE a yaw frame that carries an extra 180 deg flip, and
 * {@code Ry(180) . Rz(t) == Rz(-t) . Ry(180)}. The rider's frame has no such flip, so matching the
 * dragon's visual lean needs the OPPOSITE sign to the one the dragon uses. If the rider ever leans
 * against the turn instead of into it, flip {@code PLAYER_ROLL_SIGN}.
 *
 * <p>Injected at the TAIL of AvatarRenderer's own override (verified in the 26.2 jar: it declares
 * setupRotations and calls super), so it lands after the body yaw is established.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRotationMixin {

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void babyenderdragon$bankWithDragon(AvatarRenderState state, PoseStack pose,
			float bodyYaw, float scale, CallbackInfo ci) {
		if (!RiderPoseConfig.ENABLED) {
			return;
		}
		RidingDragonRenderState rs = (RidingDragonRenderState) state;
		if (!rs.babyenderdragon$isRidingDragon()) {
			return;
		}

		float roll = rs.babyenderdragon$getDragonRoll() * RiderPoseConfig.PLAYER_ROLL_COUPLING
				* RiderPoseConfig.PLAYER_ROLL_SIGN;
		float pitch = rs.babyenderdragon$getDragonPitch()
				* RiderPoseConfig.PLAYER_PITCH_COUPLING
				* RiderPoseConfig.PLAYER_PITCH_SIGN;

		// Pitch first, then roll, matching the order the dragon's own render composes them.
		// Without the pitch the rider stays bolt upright through a steep dive while the dragon
		// noses down, so he reads as floating off it even though the seat position is correct.
		if (pitch != 0.0F) {
			pose.mulPose(Axis.XP.rotationDegrees(pitch));
		}
		if (roll == 0.0F) {
			return;
		}
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
	}
}
