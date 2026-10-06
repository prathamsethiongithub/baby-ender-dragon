package com.babyenderdragon.mixin.client;

import com.babyenderdragon.FoundationEntity;
import com.babyenderdragon.client.RidingDragonRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures whether the rendered avatar is riding OUR dragon, and stamps it on the render state.
 *
 * <p>This is the only place where both the entity and its render state are in hand:
 * EntityRenderState has no vehicle field, and its {@code isPassenger} flag is equally true for
 * boats, horses and minecarts - so it cannot be used as the gate on its own.
 *
 * <p>The descriptor is the concrete overload, verified against the 26.2 jar:
 * {@code extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V}
 * (the generic parameter erases to Avatar).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void babyenderdragon$captureRidingDragon(Avatar entity, AvatarRenderState state,
			float partialTick, CallbackInfo ci) {
		RidingDragonRenderState rs = (RidingDragonRenderState) state;
		boolean riding = entity.getVehicle() instanceof FoundationEntity;

		rs.babyenderdragon$setRidingDragon(riding);
		if (riding) {
			FoundationEntity dragon = (FoundationEntity) entity.getVehicle();
			rs.babyenderdragon$setDragonRoll(dragon.getBank(partialTick));
			rs.babyenderdragon$setDragonPitch(dragon.getXRot(partialTick));

			// WELD: draw the rider exactly where the server's positionRider puts the entity -
			// seatWorld minus the player's vehicle attachment point. state.x/y/z is what positions
			// the model (verified in the 26.2 bytecode: LevelRenderer.submitEntities passes
			// state.x/y/z to EntityRenderDispatcher.submit, which translates the pose stack by
			// them; passengerOffset is minecart-only, so the avatar's render offset is ZERO).
			Vec3 seatWorld = dragon.getSeatWorld(entity, partialTick);
			Vec3 attach = entity.getVehicleAttachmentPoint(dragon);
			state.x = seatWorld.x - attach.x;
			state.y = seatWorld.y - attach.y;
			state.z = seatWorld.z - attach.z;
		} else {
			rs.babyenderdragon$setDragonRoll(0.0F);
			rs.babyenderdragon$setDragonPitch(0.0F);
		}
	}
}
