package com.babyenderdragon;

import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Registry wiring for the Phase 1 foundation entity.
 *
 * <p>Verified against the locked toolchain (Minecraft 26.2, Fabric API 0.161.0+26.2) by compiling,
 * not by assumption:
 * <ul>
 *   <li>{@code FabricEntityTypeBuilder} no longer exists in 26.2.</li>
 *   <li>{@code FabricEntityType.Builder} only offers {@code createLiving} / {@code createMob} — both
 *       require a {@code LivingEntity}/{@code Mob}, which would drag in AI, attributes and hurt
 *       handling this phase must not have.</li>
 *   <li>Plain {@code EntityType.Builder.of(...)} is used instead, and Fabric mixins it so the type
 *       registers normally under a mod's namespace.</li>
 *   <li>{@code build(...)} takes a {@code ResourceKey<EntityType<?>>}; keys are made with
 *       {@code ResourceKey.createRegistryKey(Identifier)} then {@code ResourceKey.create(...)} —
 *       there is no {@code createRegistries(...)}.</li>
 * </ul>
 */
public final class ModEntities {
	public static final String FOUNDATION_ENTITY_ID = BabyEnderDragon.MOD_ID + ":foundation_entity";

	private static final Identifier FOUNDATION_ENTITY_IDENTIFIER =
			Identifier.fromNamespaceAndPath(BabyEnderDragon.MOD_ID, "foundation_entity");

	/**
	 * The registry key must be derived from the existing {@code Registries.ENTITY_TYPE} holder.
	 * Building a fresh registry key via {@code ResourceKey.createRegistryKey(...)} compiles but
	 * fails at server start with "Some intrusive holders were not registered", because the type
	 * would then belong to a registry that vanilla never freezes. Verified by running the server.
	 */
	private static final ResourceKey<EntityType<?>> FOUNDATION_ENTITY_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, FOUNDATION_ENTITY_IDENTIFIER);

	public static final EntityType<FoundationEntity> FOUNDATION_ENTITY = buildFoundationEntity();

	private static EntityType<FoundationEntity> buildFoundationEntity() {
		EntityType<FoundationEntity> type = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				FOUNDATION_ENTITY_KEY,
				EntityType.Builder.of(FoundationEntity::new, MobCategory.MISC)
						.sized(0.9F, 0.9F)
						.eyeHeight(0.6F)
						.clientTrackingRange(10)
						// Two seats. passengerAttachments() APPENDS, so these are seat 0 and seat 1.
						// Seat capacity is canAddPassenger alone; the base allows 1, so override it.
						// Seat height 1.35 is MEASURED, not guessed: at 2.4 the rider floated
						// 1.0-1.1 blocks clear of the spine (read off a screenshot against the
						// player's own 1.8-block height). 2.4 - 1.05 = 1.35.
						//
						// Original problem: Seats sat at the dragon's feet. The model renders at
						// scale(-0.35,-0.35,0.35) then translate(0,-1.501,0), so a model point at
						// model_y draws at 0.35 * (1.501 + model_y). The old 0.6 seat put both the
						// rider AND the camera inside the dragon's body, which is what rendered as
						// clipping shards. -Z is behind the head (the model is yaw-flipped 180).
						.passengerAttachments(
								new net.minecraft.world.phys.Vec3(0.0D, 1.15D, -1.95D),
								new net.minecraft.world.phys.Vec3(0.0D, 1.15D, -2.9D))
						.build(FOUNDATION_ENTITY_KEY));

		// LivingEntity's constructor calls DefaultAttributes.getSupplier(type); if the type was
		// never registered as a mob/living entity it returns null and the constructor NPEs with
		// "Cannot invoke AttributeSupplier.getValue because this.supplier is null".
		// Start from vanilla's OWN living-attribute set, then tune. Hand-listing attributes is a
		// trap: 26.2 LivingEntity also needs waypoint_transmit_range / waypoint_receive_range,
		// and a missing one throws IllegalArgumentException "Can't find attribute" while ticking.
		FabricDefaultAttributeRegistry.register(type, LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.25D)
				.add(Attributes.FLYING_SPEED, 0.55D)
				.add(Attributes.STEP_HEIGHT, 0.6F)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
				.build());

		return type;
	}

	private ModEntities() {
	}
}