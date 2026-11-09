package com.logvex.crystal.cosmetic.render;

import com.logvex.crystal.cosmetic.PlayerCosmetics;
import net.minecraft.client.render.OverlayTexture;
import com.logvex.crystal.compat.Compat;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws the LOGVEX CRYSTAL cosmetic accessories on players.
 *
 * <p>This is an ordinary {@code FeatureRenderer} attached to the vanilla player renderer
 * through the Fabric {@code LivingEntityFeatureRendererRegistrationCallback}. It changes
 * nothing about the player entity — no hitbox, no gameplay state — it only submits extra
 * geometry during the render pass. The whole feature is optional: it is gated by the module
 * toggle and the live theme gate, and it silently does nothing when either is off, when the
 * player is invisible or spectating, or when no world is loaded.
 *
 * <p>The geometry is anchored to the head by applying the vanilla head transform (exactly as
 * the vanilla skull feature renderer does), so the ears follow head pitch/yaw and the player's
 * pose for free. All artwork is original to this project.
 */
public final class PlayerCosmeticFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("logvex-crystal", "textures/entity/cosmetic/player_accessories.png");

    private final CatCosmeticsModel model;

    public PlayerCosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context,
                                         CatCosmeticsModel model) {
        super(context);
        this.model = model;
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state == null || state.invisible || state.spectator || !PlayerCosmetics.visible()) {
            return;
        }
        PlayerEntityModel context = getContextModel();
        if (context == null) {
            return;
        }
        matrices.push();
        // Body transform, then head transform: afterwards the stack is in head-local space,
        // where the top of the head is y = -8 (see the vanilla biped head box).
        context.getRootPart().applyTransform(matrices);
        ((ModelWithHead) context).applyTransform(matrices);

        RenderCommandQueue batch = queue.getBatchingQueue(0);
        batch.submitModel(model, state, matrices, Compat.entityCutoutNoCull(TEXTURE),
                light, OverlayTexture.DEFAULT_UV, state.outlineColor, null);
        matrices.pop();
    }
}
