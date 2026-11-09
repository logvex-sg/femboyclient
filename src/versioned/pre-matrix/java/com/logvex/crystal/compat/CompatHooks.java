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

/**
 * Compat implementation for the {@code pre-matrix} band (Minecraft 1.21.1 .. 1.21.5): the
 * oldest supported shapes. {@code DrawContext} hands back a {@link MatrixStack}, HUD elements
 * register through {@code HudRenderCallback}, the world event is the legacy untyped one, and
 * the layer, sound and splash factories use the older forms.
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
        ((MatrixStack) matrices).push();
    }

    static void matrixPop(Object matrices) {
        ((MatrixStack) matrices).pop();
    }

    static void matrixScale(Object matrices, float scale) {
        ((MatrixStack) matrices).scale(scale, scale, 1.0f);
    }

    static void matrixTranslate(Object matrices, float x, float y) {
        ((MatrixStack) matrices).translate(x, y, 0.0);
    }

    static void registerHud(BiConsumer<Object, Object> handler) {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
                .register((context, tickCounter) -> handler.accept(context, tickCounter));
    }

    static void registerWorld(Consumer<Object> handler) {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_DEBUG_RENDER
                .register(context -> handler.accept(context));
    }

    static void drawTexture(net.minecraft.client.gui.DrawContext context, Identifier texture,
                            int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        // 1.21.1 .. 1.21.4: the texture overload takes a bare Identifier and there is no
        // RenderPipelines class yet.
        context.drawTexture(texture, x, y, u, v, width, height, texWidth, texHeight);
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
