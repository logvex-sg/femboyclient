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
 * Facade over the Minecraft and Fabric call sites whose API moved across the supported
 * version range. Every method delegates to {@link CompatHooks}, whose implementation is
 * compiled from a per-band source directory ({@code src/versioned/<band>/java}) chosen at
 * build time; the shared code only ever calls this facade.
 *
 * <p>Only changes that cannot be written as a call valid on both sides live here. Changes
 * that can (for example {@code Entity#getEntityPos}, present from 1.21.9 on) are called
 * directly instead.
 */
public final class Compat {

    private Compat() {
    }

    // --- render layers -------------------------------------------------------------------

    /** Line layer: {@code RenderLayers.lines()} on new versions, {@code RenderLayer.getLines()} on old. */
    public static RenderLayer lines() {
        return CompatHooks.lines();
    }

    /** Entity cutout-no-cull layer, moved from {@code RenderLayer} to {@code RenderLayers}. */
    public static RenderLayer entityCutoutNoCull(Identifier texture) {
        return CompatHooks.entityCutoutNoCull(texture);
    }

    /** Per-vertex line width; absent from {@code VertexConsumer} before 1.21.11. */
    public static VertexConsumer lineWidth(VertexConsumer consumer, float width) {
        return CompatHooks.lineWidth(consumer, width);
    }

    // --- misc ----------------------------------------------------------------------------

    /** UI sound instance: {@code PositionedSoundInstance.ui(...)} vs {@code .master(...)}. */
    public static PositionedSoundInstance uiSound(SoundEvent event, float volume, float pitch) {
        return CompatHooks.uiSound(event, volume, pitch);
    }

    /**
     * Title-screen splash text: the renderer takes {@code Text} on new versions and
     * {@code String} on old. The generic return type lets the constructor call resolve to
     * whichever the current band expects.
     */
    @SuppressWarnings("unchecked")
    public static <T> T splashText(Text text) {
        return (T) CompatHooks.splashText(text);
    }

    // --- draw matrix ---------------------------------------------------------------------
    // The matrix type behind DrawContext.getMatrices() changed over the range, so it stays
    // opaque here and each band applies the operation on the type it actually gets.

    /** Pushes the draw matrix. */
    public static void matrixPush(Object matrices) {
        CompatHooks.matrixPush(matrices);
    }

    /** Pops the draw matrix pushed by {@link #matrixPush}. */
    public static void matrixPop(Object matrices) {
        CompatHooks.matrixPop(matrices);
    }

    /** Scales the draw matrix uniformly in the screen plane. */
    public static void matrixScale(Object matrices, float scale) {
        CompatHooks.matrixScale(matrices, scale);
    }

    /** Translates the draw matrix in the screen plane. */
    public static void matrixTranslate(Object matrices, float x, float y) {
        CompatHooks.matrixTranslate(matrices, x, y);
    }

    // --- HUD / world hooks ---------------------------------------------------------------

    /**
     * Registers the Crystal HUD pass. The handler receives the {@code DrawContext} and the
     * tick counter; both types are stable across the range, only the registration API moved.
     */
    public static void registerHud(BiConsumer<Object, Object> handler) {
        CompatHooks.registerHud(handler);
    }

    /** Registers the world-space ESP pass. The handler receives the band's world render context. */
    public static void registerWorld(Consumer<Object> handler) {
        CompatHooks.registerWorld(handler);
    }

    // --- world render context accessors --------------------------------------------------
    // The world context is a different interface per band, so it crosses the facade as Object
    // and each band reads the pieces it has. The matrix type is MatrixStack in every band.

    /**
     * Draws a GUI texture with the given source rect and size. The texture argument's type
     * changed across the range — a bare {@code Identifier} (1.21.1-1.21.4), a
     * {@code Function<Identifier, RenderLayer>} (1.21.5-1.21.8), then a {@code RenderPipeline}
     * (1.21.9+) — so the call is routed here rather than written inline.
     */
    public static void drawTexture(net.minecraft.client.gui.DrawContext context,
                                   Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int texWidth, int texHeight) {
        CompatHooks.drawTexture(context, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

    /** The camera-relative matrix stack of a world render context. */
    public static MatrixStack worldMatrices(Object context) {
        return CompatHooks.worldMatrices(context);
    }

    /** The vertex consumers of a world render context. */
    public static VertexConsumerProvider worldConsumers(Object context) {
        return CompatHooks.worldConsumers(context);
    }

    /** The active camera of a world render context. */
    public static Camera worldCamera(Object context) {
        return CompatHooks.worldCamera(context);
    }
}
