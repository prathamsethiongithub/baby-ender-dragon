package com.babyenderdragon.client;

import com.babyenderdragon.BabyEnderDragon;
import com.babyenderdragon.DragonFrame;
import com.babyenderdragon.FoundationEntity;
import com.babyenderdragon.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint and renderer.
 *
 * <p>Two hard requirements, both discovered by running the client:
 * <ul>
 *   <li>fabric.mod.json must declare a "client" entrypoint, or Fabric never calls this and 26.2's
 *       EntityRenderDispatcher NPEs with "renderer is null" (no null guard there).</li>
 *   <li>This must live in src/main. A separate src/client source set is not compiled by this build,
 *       which produced ClassNotFoundException at startup.</li>
 * </ul>
 *
 * <p>26.2 has no getTextureLocation hook and no render() method; drawing happens in submit(). And
 * EntityRenderState carries NO rotation, so the body yaw/pitch are carried in our own subclass.
 */
public final class BabyEnderDragonClient implements ClientModInitializer {
	private static final Identifier DRAGON_TEXTURE =
			Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");

	/** Baby scale. The full dragon model is enormous next to a 0.9-block hitbox. */
	private static final float BABY_SCALE = 0.35F;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.FOUNDATION_ENTITY, BabyDragonRenderer::new);
		BabyEnderDragon.LOGGER.info("[{}] dragon renderer registered", BabyEnderDragon.MOD_ID);
	}

	/**
	 * Vanilla's render state has no yaw/pitch fields, so we add them. Subclassing is safe: the
	 * vanilla dragon model accepts any EnderDragonRenderState, and submitModel takes
	 * {@code Model<? super S>}.
	 */
	public static final class DragonRenderState extends EnderDragonRenderState {
		public float bodyYaw;
		public float bodyPitch;
		public float roll;
	}

	private static final class BabyDragonRenderer
			extends EntityRenderer<FoundationEntity, DragonRenderState> {

		private final EnderDragonModel model;

		BabyDragonRenderer(EntityRendererProvider.Context context) {
			super(context);
			this.model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
			this.shadowRadius = 1.0F;
		}

		@Override
		public DragonRenderState createRenderState() {
			return new DragonRenderState();
		}

		@Override
		public void extractRenderState(FoundationEntity entity, DragonRenderState state, float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			// The wing clock. Advanced on both sides in the entity, so this actually changes.
			state.flapTime = entity.getFlapTime(partialTick);
			state.partialTicks = partialTick;
			state.deathTime = 0.0F;
			// Body orientation, so the dragon visibly turns and pitches into climbs.
			state.bodyYaw = entity.getYRot(partialTick);
			state.bodyPitch = entity.getXRot(partialTick);
			state.roll = entity.getBank(partialTick);
		}

		@Override
		public void submit(DragonRenderState state, PoseStack pose,
				SubmitNodeCollector collector, CameraRenderState camera) {
			pose.pushPose();

			// Yaw + pitch from the model frame's single source of truth (DragonFrame; it also
			// carries the explanation of the two 180-degree flips).
			pose.mulPose(DragonFrame.yawPitch(state.bodyYaw, state.bodyPitch));
			// Roll about the spine line through ROLL_PIVOT_Y (one constant, the same pivot the
			// seat math uses) so the dragon's back stays under the rider through a bank.
			pose.translate(0.0F, (float) DragonFrame.ROLL_PIVOT_Y, 0.0F);
			pose.mulPose(DragonFrame.rollRotation(state.roll));
			pose.translate(0.0F, (float) -DragonFrame.ROLL_PIVOT_Y, 0.0F);

			// Copied from vanilla EnderDragonRenderer.submit (bytecode, 26.2):
			//   scale(-1, -1, 1) then translate(0, -1.501, 0)
			// The NEGATIVE scale is essential: the dragon model is authored mirrored, so without it
			// the model renders inside-out as a magenta/black shard mess.
			pose.scale(-BABY_SCALE, -BABY_SCALE, BABY_SCALE);
			pose.translate(0.0F, -1.501F, 0.0F);

			this.model.setupAnim(state);

			collector.submitModel(this.model, state, pose, DRAGON_TEXTURE,
					0xF000F0, OverlayTexture.NO_OVERLAY, 0,
					(net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay) null);

			pose.popPose();
		}
	}
}