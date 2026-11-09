package com.logvex.crystal.compat;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Compat implementation for the {@code modern} band (Minecraft 1.21.11 and later in family A):
 * {@code DrawContext} hands back a {@link Matrix3x2fStack}, the layer factories live on
 * {@code RenderLayers}, and the world render event is the typed {@code v1.world} one.
 */
final class CompatHooks {

    private CompatHooks() {
    }

    static RenderLayer lines() {
        return RenderLayers.lines();
    }

    static RenderLayer entityCutoutNoCull(Identifier texture) {
        return RenderLayers.entityCutoutNoCull(texture);
    }

    static VertexConsumer lineWidth(VertexConsumer consumer, float width) {
        return consumer.lineWidth(width);
    }

    static PositionedSoundInstance uiSound(SoundEvent event, float volume, float pitch) {
        return PositionedSoundInstance.ui(event, volume, pitch);
    }

    static Object splashText(Text text) {
        return text;
    }

    static void matrixPush(Object matrices) {
        ((Matrix3x2fStack) matrices).pushMatrix();
    }

    static void matrixPop(Object matrices) {
        ((Matrix3x2fStack) matrices).popMatrix();
    }

    static void matrixScale(Object matrices, float scale) {
        ((Matrix3x2fStack) matrices).scale(scale, scale);
    }

    static void matrixTranslate(Object matrices, float x, float y) {
        ((Matrix3x2fStack) matrices).translate(x, y);
    }

    static void registerHud(BiConsumer<Object, Object> handler) {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("logvex-crystal", "hud"),
                (context, tickCounter) -> handler.accept(context, tickCounter));
    }

    static void registerWorld(Consumer<Object> handler) {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_DEBUG_RENDER
                .register(context -> handler.accept(context));
    }

    static void drawTexture(net.minecraft.client.gui.DrawContext context, Identifier texture,
                            int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, texWidth, texHeight);
    }

    static MatrixStack worldMatrices(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext) context).matrices();
    }

    static VertexConsumerProvider worldConsumers(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext) context).consumers();
    }

    static Camera worldCamera(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext) context)
                .gameRenderer().getCamera();
    }
}
