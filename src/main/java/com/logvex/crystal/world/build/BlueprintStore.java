package com.logvex.crystal.world.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the blueprint library - the loaded schematics and the placement plan - as one JSON
 * document with an explicit schema version, so a future change can migrate rather than discard.
 *
 * <p>Uses the shared {@link ConfigIo} atomic-write path, so a crash mid-save cannot corrupt the
 * previous good file and a malformed file is reported and replaced by defaults instead of
 * throwing.
 */
public final class BlueprintStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public BlueprintStore(Path file) {
        this.file = file;
    }

    /** Writes the schematics and placements. Returns false when nothing could be written. */
    public boolean save(BlueprintLibrary library) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);

        JsonArray schematics = new JsonArray();
        for (Schematic schematic : library.schematics()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", schematic.name());
            JsonArray blocks = new JsonArray();
            for (Schematic.Block block : schematic.blocks()) {
                JsonArray entry = new JsonArray();
                entry.add(block.x());
                entry.add(block.y());
                entry.add(block.z());
                entry.add(block.itemId());
                blocks.add(entry);
            }
            object.add("blocks", blocks);
            schematics.add(object);
        }
        root.add("schematics", schematics);

        JsonArray placements = new JsonArray();
        for (BlueprintLibrary.Placement placement : library.placements()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", placement.id());
            object.addProperty("schematic", placement.schematicName());
            object.addProperty("x", placement.x());
            object.addProperty("y", placement.y());
            object.addProperty("z", placement.z());
            object.addProperty("rotation", placement.rotation());
            object.addProperty("mirror", placement.mirrorX());
            object.addProperty("visible", placement.visible());
            placements.add(object);
        }
        root.add("placements", placements);
        try {
            ConfigIo.writeObject(file, root);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Loads into a fresh library, returning it. A missing or corrupt file yields an empty one. */
    public BlueprintLibrary load() {
        BlueprintLibrary library = new BlueprintLibrary();
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return library;
        }
        JsonArray schematics = root.getAsJsonArray("schematics");
        if (schematics != null) {
            for (var element : schematics) {
                JsonObject object = element.getAsJsonObject();
                String name = object.get("name").getAsString();
                List<Schematic.Block> blocks = new ArrayList<>();
                JsonArray array = object.getAsJsonArray("blocks");
                for (var blockElement : array) {
                    JsonArray entry = blockElement.getAsJsonArray();
                    blocks.add(new Schematic.Block(entry.get(0).getAsInt(), entry.get(1).getAsInt(),
                            entry.get(2).getAsInt(), entry.get(3).getAsString()));
                }
                library.addSchematic(Schematic.of(name, blocks));
            }
        }
        JsonArray placements = root.getAsJsonArray("placements");
        if (placements != null) {
            for (var element : placements) {
                JsonObject object = element.getAsJsonObject();
                String schematic = object.get("schematic").getAsString();
                if (library.schematic(schematic).isEmpty()) {
                    continue;
                }
                BlueprintLibrary.Placement placement = library.place(schematic,
                        object.get("x").getAsInt(), object.get("y").getAsInt(),
                        object.get("z").getAsInt());
                int rotation = object.has("rotation") ? object.get("rotation").getAsInt() : 0;
                for (int i = 0; i < Math.floorMod(rotation, 4); i++) {
                    library.rotate(placement.id());
                }
                if (object.has("mirror") && object.get("mirror").getAsBoolean()) {
                    library.mirror(placement.id());
                }
                if (object.has("visible")) {
                    library.setVisible(placement.id(), object.get("visible").getAsBoolean());
                }
            }
        }
        // A load is not a user edit: start with a clean history.
        library.history().clear();
        return library;
    }
}
