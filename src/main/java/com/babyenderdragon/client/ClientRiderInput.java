package com.babyenderdragon.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only bridge that reads the LOCAL player's raw key state.
 *
 * <p>Deliberately a separate class in the client package: it references {@link LocalPlayer}, a
 * client-only type, so it must never be loaded on a dedicated server. It is only reached from a
 * branch that already proved we are on the client.
 */
public final class ClientRiderInput {
	private ClientRiderInput() {
	}

	/** @return the local player's key state, or null if this rider is not the local player. */
	public static Input keys(Player rider) {
		return rider instanceof LocalPlayer local ? local.input.keyPresses : null;
	}
}
