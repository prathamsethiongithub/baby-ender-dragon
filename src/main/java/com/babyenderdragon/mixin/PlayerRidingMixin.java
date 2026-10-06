package com.babyenderdragon.mixin;

import com.babyenderdragon.FoundationEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the sneak key from dismounting the dragon.
 *
 * <p>26.2 has no STOP_RIDING packet action. The real path is the sneak flag:
 * ServerboundPlayerInputPacket -> ServerGamePacketListenerImpl.handlePlayerInput ->
 * ServerPlayer.setShiftKeyDown -> Entity.setSharedFlag(1, v), and then Player.rideTick() runs
 * {@code if (!level().isClientSide() && wantsToStopRiding() && isPassenger()) stopRiding();}
 * with wantsToStopRiding() being nothing but isShiftKeyDown(). So the first server tick after
 * shift goes down, the rider is thrown off - which is also why a shift-to-descend binding could
 * never have worked.
 *
 * <p>Returning false here keeps the rider seated. It only applies while the vehicle is OUR
 * dragon, so every vanilla mount still dismounts normally. A real dismount is still available:
 * our own code calls Player#stopRiding() directly, which does not consult this predicate.
 */
@Mixin(Player.class)
public abstract class PlayerRidingMixin {

	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void babyenderdragon$keepRiderSeated(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self.getVehicle() instanceof FoundationEntity) {
			cir.setReturnValue(false);
		}
	}
}
