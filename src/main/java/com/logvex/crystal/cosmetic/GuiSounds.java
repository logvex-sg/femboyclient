package com.logvex.crystal.cosmetic;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.compat.Compat;
import com.logvex.crystal.util.BestEffort;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Plays soft, original UI sounds for clicks, toggles and notifications.
 *
 * <p>All sounds are vanilla sound events re-used at low volume and slightly randomised
 * pitch, so no external audio asset is bundled. Everything is gated behind the
 * {@code guiSounds} setting, and every call is wrapped so an audio failure can never
 * disturb the interface.
 */
public final class GuiSounds {

    private GuiSounds() {
    }

    public static void click() {
        playEntry(SoundEvents.UI_BUTTON_CLICK, 0.35f, 1.4f);
    }

    public static void toggleOn() {
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
    }

    public static void toggleOff() {
        play(SoundEvents.ENTITY_ITEM_PICKUP, 0.3f, 0.9f);
    }

    public static void notifyCute() {
        playEntry(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.8f);
    }

    public static void open() {
        playEntry(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.4f, 1.5f);
    }

    public static void close() {
        playEntry(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.35f, 1.1f);
    }

    /** Easter-egg jingle: a short bright chime. */
    public static void secret() {
        playEntry(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.9f);
    }

    private static void playEntry(RegistryEntry<SoundEvent> event, float volume, float pitch) {
        if (!enabled()) {
            return;
        }
        BestEffort.run("gui-sound", "UI sound", () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getSoundManager() != null) {
                client.getSoundManager().play(Compat.uiSound(event.value(), volume, jitter(pitch)));
            }
        });
    }

    private static void play(SoundEvent event, float volume, float pitch) {
        if (!enabled()) {
            return;
        }
        BestEffort.run("gui-sound", "UI sound", () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getSoundManager() != null) {
                client.getSoundManager().play(Compat.uiSound(event, volume, jitter(pitch)));
            }
        });
    }

    private static float jitter(float pitch) {
        return pitch * (1.0f + (float) (Math.random() * 0.08 - 0.04));
    }

    private static boolean enabled() {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return false;
        }
        return bootstrap.theme().settings().guiSounds;
    }
}
