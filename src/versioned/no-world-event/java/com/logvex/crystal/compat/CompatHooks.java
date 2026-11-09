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
 * Compat implementation for the {@code no-world-event} band (Minecraft 1.21.9): identical to
 * {@code pre-world} except that fabric-rendering-v1 16.0.1 for this exact release dropped the
 * world render event. There is no hook to attach the world-space ESP to, so it registers
 * nothing and the feature is simply absent on this version; every other API is present.
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
        org.slf4j.LoggerFactory.getLogger("logvex-crystal")
                .warn("World-space ESP is unavailable on this version: Fabric API exposes no world render event");
    }

    static MatrixStack worldMatrices(Object context) {
        return null;
    }

    static VertexConsumerProvider worldConsumers(Object context) {
        return null;
    }

    static Camera worldCamera(Object context) {
        return null;
    }
}
