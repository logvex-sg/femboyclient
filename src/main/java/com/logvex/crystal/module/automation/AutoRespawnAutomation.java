package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

/**
 * AutoRespawn (automation): clicks respawn for you after a configurable delay.
 *
 * <p>It only acts while the vanilla death screen is actually up, so it never interferes with a
 * disconnect, a ban screen or a menu the player opened deliberately. The optional notification
 * reports where the player died before the screen is dismissed.
 */
public final class AutoRespawnAutomation extends AutomationModule {

    private final IntSetting delayTicks = add("DelayTicks", "Wait this many ticks before respawning.", 10, 0, 100);
    private final BooleanSetting notify =
            add("Notify", "Announce the death location before respawning.", true);

    private long deathAtTick = -1;
    private boolean announced;

    public AutoRespawnAutomation() {
        super(Category.AUTOMATION, "AutoRespawn", "Respawns automatically after the death screen.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            deathAtTick = -1;
            announced = false;
            return;
        }
        boolean dead = client.currentScreen instanceof DeathScreen || client.player.isDead();
        if (!dead) {
            deathAtTick = -1;
            announced = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (deathAtTick < 0) {
            deathAtTick = now;
        }
        if (!announced && notify.value()) {
            announced = true;
            var pos = client.player.getBlockPos();
            context().notifications().warn("Died at " + pos.getX() + ", " + pos.getY()
                    + ", " + pos.getZ() + " \u2661");
        }
        if (now - deathAtTick >= delayTicks.value() * 50L) {
            client.player.requestRespawn();
            deathAtTick = -1;
        }
    }

    @Override
    public String statusLine() {
        return deathAtTick < 0 ? "alive" : "respawning...";
    }
}
