package com.logvex.crystal.render.esp;

import com.logvex.crystal.render.Frustum;
import com.logvex.crystal.render.ScreenProjector;
import com.logvex.crystal.render.ViewProjection;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import com.logvex.crystal.compat.Compat;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws the {@link WorldEspCollector} buffer into the world.
 *
 * <p>Boxes are drawn as outlines with the vanilla {@code lines()} render layer (one
 * {@code VertexConsumer} per box, flushed immediately) and labels are drawn with the
 * vanilla text renderer into the same consumer provider, so the text is a real 3-D label
 * that scales with distance rather than a screen-space overlay. Both happen inside a single
 * matrix push/pop rooted at the camera, so a failure can never leave the matrix stack
 * unbalanced.
 *
 * <p>The camera-relative offset convention used here matches vanilla's own debug outline
 * rendering (subtract the camera position, then draw).
 */
public final class WorldEspRenderer {

    private static final int MAX_BOXES_PER_FRAME = 4096;

    private WorldEspRenderer() {
    }

    /**
     * Draws the buffer.
     *
     * @param context  the world render context (camera-relative matrix stack + consumers)
     * @param client   the client
     * @param camera   the active camera
     * @param buffer   the collected primitives
     */
    public static void render(Object context,
                              MinecraftClient client, Camera camera, WorldEspCollector buffer) {
        if (buffer.isEmpty() || client.textRenderer == null) {
            return;
        }
        MatrixStack matrices = Compat.worldMatrices(context);
        VertexConsumerProvider consumers = Compat.worldConsumers(context);
        if (matrices == null || consumers == null) {
            return;
        }
        Vec3d cam = camera.getCameraPos();
        List<WorldEspCollector.Box> boxes = buffer.boxes();
        int limit = Math.min(boxes.size(), MAX_BOXES_PER_FRAME);
        // Reject boxes the camera cannot see before spending any vertex work on them. The
        // frustum is rebuilt each frame from the camera pose, so it tracks movement and FOV.
        Frustum frustum = Frustum.fromMatrix(ViewProjection.build(
                cam.x, cam.y, cam.z, camera.getYaw(), camera.getPitch(),
                fovOf(client), client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), 0.05f, 512f));
        matrices.push();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            Entry entry = matrices.peek();
            Matrix4f matrix = entry.getPositionMatrix();
            for (int i = 0; i < limit; i++) {
                WorldEspCollector.Box box = boxes.get(i);
                if (!frustum.intersectsBox(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ())) {
                    continue;
                }
                VertexConsumer line = consumers.getBuffer(Compat.lines());
                addBoxLines(line, matrix, box);
            }
            drawLabels(client, consumers, matrices, buffer.labels(), camera, frustum);
        } finally {
            matrices.pop();
        }
    }

    /** The client's vertical FOV in degrees, or a sane default before options exist. */
    private static float fovOf(MinecraftClient client) {
        if (client.options != null) {
            return client.options.getFov().getValue().floatValue();
        }
        return 70f;
    }

    private static void addBoxLines(VertexConsumer vc, Matrix4f matrix, WorldEspCollector.Box b) {
        float x0 = (float) b.minX();
        float y0 = (float) b.minY();
        float z0 = (float) b.minZ();
        float x1 = (float) b.maxX();
        float y1 = (float) b.maxY();
        float z1 = (float) b.maxZ();
        int c = b.color();
        float w = b.lineWidth();
        // Bottom rectangle.
        edge(vc, matrix, x0, y0, z0, x1, y0, z0, c, w);
        edge(vc, matrix, x1, y0, z0, x1, y0, z1, c, w);
        edge(vc, matrix, x1, y0, z1, x0, y0, z1, c, w);
        edge(vc, matrix, x0, y0, z1, x0, y0, z0, c, w);
        // Top rectangle.
        edge(vc, matrix, x0, y1, z0, x1, y1, z0, c, w);
        edge(vc, matrix, x1, y1, z0, x1, y1, z1, c, w);
        edge(vc, matrix, x1, y1, z1, x0, y1, z1, c, w);
        edge(vc, matrix, x0, y1, z1, x0, y1, z0, c, w);
        // Verticals.
        edge(vc, matrix, x0, y0, z0, x0, y1, z0, c, w);
        edge(vc, matrix, x1, y0, z0, x1, y1, z0, c, w);
        edge(vc, matrix, x1, y0, z1, x1, y1, z1, c, w);
        edge(vc, matrix, x0, y0, z1, x0, y1, z1, c, w);
    }

    private static void edge(VertexConsumer vc, Matrix4f m, float ax, float ay, float az,
                             float bx, float by, float bz, int color, float width) {
        Compat.lineWidth(vc.vertex(m, ax, ay, az).color(color), width);
        Compat.lineWidth(vc.vertex(m, bx, by, bz).color(color), width);
    }

    private static void drawLabels(MinecraftClient client, VertexConsumerProvider consumers, MatrixStack matrices,
                                   List<WorldEspCollector.Label> labels, Camera camera, Frustum frustum) {
        if (labels.isEmpty()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        float scale = 0.025f;
        for (WorldEspCollector.Label label : labels) {
            if (!frustum.containsPoint(label.x(), label.y(), label.z())) {
                continue;
            }
            matrices.push();
            try {
                matrices.translate(label.x(), label.y(), label.z());
                matrices.scale(scale, -scale, scale);
                float width = renderer.getWidth(label.text());
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                renderer.draw(Text.literal(label.text()), -width / 2f, 0, label.color(), false,
                        matrix, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            } catch (Throwable t) {
                // A label must never break the render pass, but a persistent failure should
                // still surface once via the log/; errors rather than vanish.
                com.logvex.crystal.util.BestEffort.report("esp-label", "ESP label", t);
            } finally {
                matrices.pop();
            }
        }
    }

    /** Whether the given world point is close enough to be worth drawing. */
    public static boolean within(Vec3d cameraPos, double x, double y, double z, double maxDistance) {
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    /** Projects a point through the current frame's view-projection for the screen-space ESP. */
    public static ScreenProjector.Projected project(MinecraftClient client, Camera camera, double x, double y, double z) {
        Vec3d cam = camera.getCameraPos();
        float fov = fovOf(client);
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        float[] mvp = ViewProjection.build(cam.x, cam.y, cam.z, camera.getYaw(), camera.getPitch(),
                fov, width, height, 0.05f, 512f);
        return ScreenProjector.project(mvp, x, y, z, width, height);
    }
}
