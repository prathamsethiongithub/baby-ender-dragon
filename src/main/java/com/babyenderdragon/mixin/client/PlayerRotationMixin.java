package com.babyenderdragon.mixin.client;

import com.babyenderdragon.DragonFrame;
import com.babyenderdragon.SeatSelfCheck;
import com.babyenderdragon.client.RiderPoseConfig;
import com.babyenderdragon.client.RidingDragonRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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

		// One attitude, SAME signs as the dragon's own render (Step-1 source check: the player
		// renderer applies the identical frame - setupRotations with Ry(180 - bodyRot), then
		// scale(-1, -1, 1), then translate(0, -1.501, 0) - so both rotate the same way), and
		// pivoting about the rider's ATTACHMENT point so the seat contact stays on the back.
		// mutatedAttitude is a pass-through unless a negative-control property is set.
		float pitchDeg = rs.babyenderdragon$getDragonPitch();
		float rollDeg = rs.babyenderdragon$getDragonRoll();
		float attachY = rs.babyenderdragon$getAttachY();
		Matrix4f attitude = SeatSelfCheck.mutatedAttitude(pitchDeg, rollDeg, attachY,
				DragonFrame.riderAttitude(pitchDeg, rollDeg, attachY));
		pose.mulPose(attitude);

		if (SeatSelfCheck.ENABLED) {
			// H2: seat self-check, right after the attitude is applied. The 26.2 pose stack is
			// camera-relative (the dispatcher translates by state.x - camera.pos), so
			// SeatSelfCheck adds the camera's world position back to get world space.
			Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
			SeatSelfCheck.onRiderPose(rs.babyenderdragon$getDebugDragonId(), pose.last().pose(),
					rs.babyenderdragon$getDebugSeatX(), rs.babyenderdragon$getDebugSeatY(),
					rs.babyenderdragon$getDebugSeatZ(),
					rs.babyenderdragon$getDebugPlayerX(), rs.babyenderdragon$getDebugPlayerY(),
					rs.babyenderdragon$getDebugPlayerZ(),
					rs.babyenderdragon$getAttachY(), rs.babyenderdragon$getDebugPartialTick(),
					cam.x, cam.y, cam.z,
					bodyYaw, rs.babyenderdragon$getDebugDragonYaw());
		}
	}
}
