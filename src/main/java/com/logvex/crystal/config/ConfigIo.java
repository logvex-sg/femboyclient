package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared, Minecraft-independent helpers for the JSON config stores.
 *
 * <p>Every store previously re-implemented the same atomic-write dance. Centralising it
 * gives one place to get the safety rules right: write to a temp file, flush and close it,
 * then move it over the target; before that, keep a {@code .bak} copy of the previous valid
 * file so a bad write or a crash cannot lose the user's settings. Reads tolerate a missing,
 * empty or malformed file by returning {@code null} so the caller can fall back to defaults.
 */
public final class ConfigIo {

    /** Pretty-printing Gson shared by the stores (no HTML escaping, so names stay readable). */
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";

    /**
     * A process-wide sink for "a config file could not be read, so defaults were used"
     * notices. The client registers this to raise an in-game warning once at startup, so a
     * corrupt file is reported to the user rather than silently reset. Deliberately
     * Minecraft-independent, so the store tests can observe it too.
     */
    private static volatile java.util.function.Consumer<String> recoveryListener;

    /**
     * A process-wide sink for "a config file was restored from its backup" notices, kept
     * separate from {@link #onRecovery} so the client can tell the two apart.
     */
    private static volatile java.util.function.Consumer<String> restoreListener;

    /** Registers the recovery listener (last registration wins). */
    public static void onRecovery(java.util.function.Consumer<String> listener) {
        recoveryListener = listener;
    }

    /** Registers the backup-restore listener (last registration wins). */
    public static void onRestore(java.util.function.Consumer<String> listener) {
        restoreListener = listener;
    }

    private ConfigIo() {
    }

    /**
     * Reads and parses a JSON object from {@code file}.
     *
     * <p>When the primary file is missing or malformed but a valid {@code .bak} exists, the
     * backup is restored and re-read, so a crash mid-write or a hand edit gone wrong does not
     * cost the user their settings. The restore fires the restore listener; a file that stays
     * unreadable fires the recovery listener.
     *
     * @return the parsed object, or {@code null} when there is nothing readable. Never throws.
     */
    public static JsonObject readObject(Path file) {
        if (file == null) {
            return null;
        }
        JsonObject object = readObjectDirect(file);
        if (object != null) {
            return object;
        }
        if (recoverFromBackup(file)) {
            reportRestore(file);
            return readObjectDirect(file);
        }
        if (Files.exists(file)) {
            reportRecovery(file);
        }
        return null;
    }

    private static JsonObject readObjectDirect(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void reportRecovery(Path file) {
        notify(recoveryListener, file);
    }

    private static void reportRestore(Path file) {
        notify(restoreListener, file);
    }

    private static void notify(java.util.function.Consumer<String> listener, Path file) {
        if (listener == null) {
            return;
        }
        try {
            listener.accept(file.getFileName().toString());
        } catch (RuntimeException ignored) {
            // A broken listener must never break a load.
        }
    }

    /**
     * Writes {@code root} to {@code file} atomically and durably.
     *
     * <p>Sequence: create the parent directory, snapshot the current file to {@code .bak},
     * write the temp file and close it (flushing), then move the temp file over the target
     * (atomic when the filesystem supports it). A failure at any step leaves either the old
     * file or the backup intact, so the next launch still has something to load.
     */
    public static void writeObject(Path file, JsonObject root) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        backup(file);
        Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies the current file to {@code <name>.bak} when it exists, so a corrupt write can be
     * recovered by hand. Best effort: a backup failure must not block the real write.
     */
    private static void backup(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + BACKUP_SUFFIX),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // A missing backup is not fatal; the write itself still proceeds.
        }
    }

    /** The backup path a store would keep for {@code file} ({@code <name>.bak}). */
    public static Path backupPath(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }

    /** True when the file exists but cannot be parsed as a JSON object. */
    public static boolean isCorrupt(Path file) {
        if (file == null || !Files.exists(file)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root == null || !root.isJsonObject();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Restores {@code file} from its {@code .bak} backup when the primary file is missing or
     * corrupt and the backup parses cleanly.
     *
     * <p>This is the recovery half of the atomic-write contract: {@link #writeObject} always
     * keeps a valid previous copy, so a crash mid-write (or a hand edit gone wrong) can be
     * undone. A healthy primary file is never touched, and a corrupt backup is never used.
     *
     * @return true when the file was restored from the backup.
     */
    public static boolean recoverFromBackup(Path file) {
        if (file == null) {
            return false;
        }
        Path backup = backupPath(file);
        if (!Files.exists(backup) || isCorrupt(backup)) {
            return false;
        }
        boolean primaryMissing = !Files.exists(file);
        if (!primaryMissing && !isCorrupt(file)) {
            return false;
        }
        try {
            Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
