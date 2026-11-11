package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AfkDetector;
import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * AFK Detection (automation): watches for real player input and reports when the player has gone
 * idle, optionally nudging the view so the session is not flagged as AFK by an idle timer.
 *
 * <p>Activity is any movement key, jump, sneak or an actual change of the player's position or
 * look, so merely standing still in a safe spot is not treated as activity. The timing lives in
 * the unit-tested {@link AfkDetector}.
 */
public final class AfkGuardAutomation extends AutomationModule {

    private final IntSetting idleSeconds =
            add("IdleSeconds", "Consider the player AFK after this many idle seconds.", 120, 10, 3600);
    private final BooleanSetting notify = add("Notify", "Notify when the player goes AFK.", true);
    private final BooleanSetting preventKick =
            add("PreventKick", "Nudge the view while AFK to defeat idle timers.", false);
    private final IntSetting nudgeSeconds =
            add("NudgeSeconds", "Seconds between anti-AFK nudges.", 30, 5, 300);

    private final AfkDetector detector = new AfkDetector();
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private long lastNudge;

    public AfkGuardAutomation() {
        super(Category.AUTOMATION, "AfkGuard", "Detects idleness and optionally prevents kicks.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (inputActive(client) || moved(client)) {
            detector.recordActivity(now);
        }
        boolean becameAfk = detector.update(now, idleSeconds.value() * 1000L);
        if (becameAfk && notify.value()) {
            context().notifications().info("You are now AFK \u2661");
        }
        if (preventKick.value() && detector.isAfk() && now - lastNudge >= nudgeSeconds.value() * 1000L) {
            lastNudge = now;
            nudge(client);
        }
    }

    private boolean inputActive(MinecraftClient client) {
        var input = client.player.input;
        return input.hasForwardMovement() || client.options.jumpKey.isPressed()
                || client.options.sneakKey.isPressed() || client.options.attackKey.isPressed()
                || client.options.useKey.isPressed();
    }

    private boolean moved(MinecraftClient client) {
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        boolean changed = Math.abs(x - lastX) > 1e-3 || Math.abs(y - lastY) > 1e-3
                || Math.abs(z - lastZ) > 1e-3 || Math.abs(yaw - lastYaw) > 0.01f
                || Math.abs(pitch - lastPitch) > 0.01f;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        return changed;
    }

    private void nudge(MinecraftClient client) {
        float yaw = client.player.getYaw() + (client.player.getRandom().nextBoolean() ? 0.5f : -0.5f);
        client.player.setYaw(yaw);
    }

    @Override
    public String statusLine() {
        if (!detector.isAfk()) {
            return "active";
        }
        return "afk " + (detector.idleMillis(System.currentTimeMillis()) / 1000) + "s";
    }
}
