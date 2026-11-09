package com.logvex.crystal.compat;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Compat implementation for the {@code pre-world} band (Minecraft 1.21.6 .. 1.21.9): the draw
 * matrix is a {@link Matrix3x2fStack} and the HUD registry exists, but the world render event
 * is still the legacy {@code v1.WorldRenderEvents} with its untyped context, and the layer,
 * sound and splash factories use the older forms.
 */
final class CompatHooks {

    private CompatHooks() {
    }

    static RenderLayer lines() {
        return RenderLayer.getLines();
    }

    static RenderLayer entityCutoutNoCull(Identifier texture) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    static VertexConsumer lineWidth(VertexConsumer consumer, float width) {
        return consumer;
    }

    static PositionedSoundInstance uiSound(SoundEvent event, float volume, float pitch) {
        return PositionedSoundInstance.master(event, volume, pitch);
    }

    static Object splashText(Text text) {
        return text.getString();
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
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_DEBUG_RENDER
                .register(context -> handler.accept(context));
    }

    static MatrixStack worldMatrices(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext) context).matrixStack();
    }

    static VertexConsumerProvider worldConsumers(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext) context).consumers();
    }

    static Camera worldCamera(Object context) {
        return ((net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext) context).camera();
    }
}
