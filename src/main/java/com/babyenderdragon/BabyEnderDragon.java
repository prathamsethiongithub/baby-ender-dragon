package com.babyenderdragon;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baby Ender Dragon — Phase 1 foundation mod.
 *
 * <p>Phase 1 establishes only the smallest foundation entity required by Gate 1.
 * No hatching, taming, flight, breath or projectile behaviour exists yet.
 */
public final class BabyEnderDragon implements ModInitializer {
	public static final String MOD_ID = "babyenderdragon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] foundation initialising; registered entity type: {}",
				MOD_ID, ModEntities.FOUNDATION_ENTITY_ID);
		// Touch the field so registration happens during mod init rather than lazily.
		if (ModEntities.FOUNDATION_ENTITY == null) {
			throw new IllegalStateException("foundation entity type failed to register");
		}
	}
}