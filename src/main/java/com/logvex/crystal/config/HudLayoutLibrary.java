package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.gui.HudLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A file-backed library of named HUD layouts ("HUD profiles"), plus import/export.
 *
 * <p>Each layout is one JSON document under {@code <config>/hud-layouts/<name>.json} written
 * through {@link ConfigIo}, so the format is identical to {@code hud.json} and a layout can be
 * moved between the active layout and the library, or exported and re-imported, without any
 * translation step. Names are sanitised and never overwrite an existing layout unless the caller
 * asks; import appends a suffix on a clash rather than clobbering the user's work.
 *
 * <p>The class holds no Minecraft types, so the save/load/list/import/export flow is fully
 * unit-testable.
 */
public final class HudLayoutLibrary {

    /** Layout file suffix. */
    private static final String SUFFIX = ".json";
    /** Suffix used for exported documents, matching the profile export convention. */
    public static final String EXPORT_SUFFIX = ".export.json";

    private final Path directory;

    public HudLayoutLibrary(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    /** Sanitises a name into a safe file stem; empty input becomes "layout". */
    public static String sanitize(String name) {
        String cleaned = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return cleaned.isEmpty() ? "layout" : cleaned;
    }

    /** The file a named layout would live in. */
    public Path fileFor(String name) {
        return directory.resolve(sanitize(name) + SUFFIX);
    }

    /** Names of every stored layout, sorted. */
    public List<String> names() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String file = path.getFileName().toString();
                if (file.endsWith(SUFFIX) && !file.endsWith(EXPORT_SUFFIX)) {
                    names.add(file.substring(0, file.length() - SUFFIX.length()));
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        names.sort(String::compareTo);
        return names;
    }

    public boolean has(String name) {
        return Files.isRegularFile(fileFor(name));
    }

    /**
     * Stores {@code layout} under {@code name}.
     *
     * @param overwrite when false, an existing layout is left untouched and false is returned
     * @return true when a file was written
     */
    public boolean save(String name, HudLayout layout, boolean overwrite) throws IOException {
        Path file = fileFor(name);
        if (Files.exists(file) && !overwrite) {
            return false;
        }
        Files.createDirectories(directory);
        ConfigIo.writeObject(file, HudLayoutStore.toJson(layout));
        return true;
    }

    /** Loads a named layout into {@code target}. Returns false when it is missing. */
    public boolean load(String name, HudLayout target) {
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return false;
        }
        target.clear();
        HudLayoutStore.apply(root, target);
        return true;
    }

    /** Deletes a named layout. Returns false when there was nothing to delete. */
    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Writes a named layout to {@code <name>.export.json} in the library directory, ready to be
     * copied elsewhere. Returns the path written, or null on failure.
     */
    public Path export(String name) {
        Path source = fileFor(name);
        if (!Files.isRegularFile(source)) {
            return null;
        }
        Path target = directory.resolve(sanitize(name) + EXPORT_SUFFIX);
        try {
            Files.createDirectories(directory);
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Imports every {@code *.export.json} document in the library directory into the live layout
     * set. A clash with an existing name gets a numeric suffix, so an import never overwrites a
     * stored layout. Returns the names actually imported.
     */
    public List<String> importAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> imported = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String file = path.getFileName().toString();
                if (!file.endsWith(EXPORT_SUFFIX)) {
                    continue;
                }
                String stem = file.substring(0, file.length() - EXPORT_SUFFIX.length());
                String target = uniqueName(sanitize(stem));
                JsonObject root = ConfigIo.readObject(path);
                if (root == null) {
                    continue;
                }
                try {
                    ConfigIo.writeObject(fileFor(target), root);
                    imported.add(target);
                } catch (IOException ignored) {
                    // A single bad document must not abort the rest of the import.
                }
            }
        } catch (IOException e) {
            return imported;
        }
        return imported;
    }

    private String uniqueName(String base) {
        if (!has(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (!has(candidate)) {
                return candidate;
            }
        }
        return base + "-" + System.nanoTime();
    }
}
