package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.StringSetting;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;

/**
 * AutoGG (automation): says a configurable message in chat when a match ends or the player dies,
 * with a randomised pool of messages and a per-event cooldown so it cannot spam.
 *
 * <p>It sends through the ordinary chat path, so the server sees a normal player message. It is
 * disabled while the player is typing, so it never steals a message the player was composing.
 */
public final class AutoGgAutomation extends AutomationModule {

    /** When to say the message. */
    public enum Trigger {
        ON_DEATH,
        ON_WORLD_LEAVE,
        BOTH
    }

    private final EnumSetting<Trigger> trigger =
            add("Trigger", "When to send the message.", Trigger.ON_DEATH);
    private final StringSetting messages = add(new StringSetting("Messages",
            "Pipe-separated messages; one is picked at random.",
            "gg|good fight|gf|gg wp", 512));
    private final IntSetting cooldownSeconds =
            add("CooldownSeconds", "Minimum seconds between messages.", 10, 0, 120);
    private final BooleanSetting whileTyping =
            add("WhileTyping", "Also send while the chat box is open.", false);

    private boolean wasAlive = true;
    private long lastSent;

    public AutoGgAutomation() {
        super(Category.AUTOMATION, "AutoGG", "Sends a configurable message after a match or death.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            wasAlive = true;
            return;
        }
        boolean alive = !client.player.isDead();
        boolean justDied = wasAlive && !alive;
        wasAlive = alive;
        if (!justDied) {
            return;
        }
        if (trigger.value() == Trigger.ON_WORLD_LEAVE) {
            return;
        }
        send(client);
    }

    /** Called by the client when the world is left, for the {@link Trigger#ON_WORLD_LEAVE} case. */
    public void onWorldLeave() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (trigger.value() == Trigger.ON_DEATH) {
            return;
        }
        send(client);
    }

    private void send(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!whileTyping.value() && client.currentScreen != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSent < cooldownSeconds.value() * 1000L) {
            return;
        }
        List<String> pool = pool();
        if (pool.isEmpty()) {
            return;
        }
        String message = pool.get(new Random().nextInt(pool.size()));
        client.getNetworkHandler().sendChatMessage(message);
        lastSent = now;
    }

    private List<String> pool() {
        List<String> result = new java.util.ArrayList<>();
        for (String part : messages.value().split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @Override
    public String statusLine() {
        return trigger.value().name().toLowerCase(java.util.Locale.ROOT);
    }
}
