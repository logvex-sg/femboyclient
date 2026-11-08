package com.logvex.crystal.scan.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Persistent storage for scan sessions and scan profiles.
 *
 * <p>Everything lives in one atomic JSON file under the client config directory. The store is
 * deliberately defensive: reads tolerate a missing, empty or malformed file (returning no
 * sessions rather than throwing), writes are atomic with a {@code .bak} backup via
 * {@link ConfigIo}, and a session history is capped so a long-lived install cannot grow without
 * bound. Import/export use the same JSON shape as the store, so an exported map is exactly what
 * an import expects.
 *
 * <p>Minecraft-free and unit tested against a temporary path.
 */
public final class ScanDatabase {

    /** Schema version of the on-disk format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;
    /** At most this many finished sessions are kept; the oldest are dropped. */
    public static final int HISTORY_CAP = 32;

    private final Path file;
    private final List<ScanSession> sessions = new ArrayList<>();
    private final List<ScanProfile> profiles = new ArrayList<>();

    private String currentId;
    private int skipped;

    public ScanDatabase(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** How many malformed session entries were skipped on the last load. */
    public int skipped() {
        return skipped;
    }

    // ------------------------------------------------------------------ sessions

    /** Every known session, oldest first. */
    public List<ScanSession> sessions() {
        return Collections.unmodifiableList(sessions);
    }

    /** The session currently being run, or null. */
    public ScanSession current() {
        if (currentId == null) {
            return null;
        }
        for (ScanSession session : sessions) {
            if (session.id().equals(currentId)) {
                return session;
            }
        }
        return null;
    }

    /** Registers a new session as the current one and returns it. */
    public ScanSession begin(String dimension, ScanBounds bounds, long nowMillis) {
        String id = "scan-" + nowMillis + "-" + Integer.toHexString((int) (nowMillis & 0xFFFF));
        ScanSession session = new ScanSession(id, dimension, bounds, nowMillis);
        session.setState(ScanState.INITIALIZING, nowMillis);
        sessions.add(session);
        currentId = id;
        trimHistory();
        return session;
    }

    /** Ends the current run, leaving the session in history. */
    public void end(ScanState finalState, long nowMillis) {
        ScanSession session = current();
        if (session != null) {
            session.setState(finalState, nowMillis);
        }
        currentId = null;
    }

    public ScanSession byId(String id) {
        if (id == null) {
            return null;
        }
        for (ScanSession session : sessions) {
            if (session.id().equals(id)) {
                return session;
            }
        }
        return null;
    }

    /** Deletes one session. Returns true when it existed. */
    public boolean delete(String id) {
        boolean removed = sessions.removeIf(session -> session.id().equals(id));
        if (removed && id != null && id.equals(currentId)) {
            currentId = null;
        }
        return removed;
    }

    /** Deletes every session. */
    public void clearSessions() {
        sessions.clear();
        currentId = null;
    }

    /** Deletes the oldest sessions until the history is within {@link #HISTORY_CAP}. */
    private void trimHistory() {
        while (sessions.size() > HISTORY_CAP) {
            ScanSession removed = sessions.remove(0);
            if (removed.id().equals(currentId)) {
                currentId = null;
            }
        }
    }

    // ------------------------------------------------------------------ profiles

    public List<ScanProfile> profiles() {
        return Collections.unmodifiableList(profiles);
    }

    /** Adds or replaces a profile by name (case-insensitive). */
    public void putProfile(ScanProfile profile) {
        profiles.removeIf(existing -> existing.name().equalsIgnoreCase(profile.name()));
        profiles.add(profile);
    }

    public ScanProfile profile(String name) {
        if (name == null) {
            return null;
        }
        for (ScanProfile profile : profiles) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    public boolean deleteProfile(String name) {
        return profiles.removeIf(existing -> existing.name().equalsIgnoreCase(name));
    }

    // ------------------------------------------------------------------ persistence

    /** Loads sessions and profiles from disk. A missing or corrupt file yields an empty store. */
    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        sessions.clear();
        profiles.clear();
        currentId = null;
        skipped = 0;
        if (root.has("sessions") && root.get("sessions").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("sessions")) {
                if (!element.isJsonObject()) {
                    skipped++;
                    continue;
                }
                ScanSession session = readSession(element.getAsJsonObject());
                if (session == null) {
                    skipped++;
                } else {
                    sessions.add(session);
                }
            }
        }
        if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("profiles")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                ScanProfile profile = readProfile(element.getAsJsonObject());
                if (profile != null) {
                    profiles.add(profile);
                }
            }
        }
        trimHistory();
    }

    /** Writes sessions and profiles to disk atomically. */
    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    /** Serialises the whole store (exposed for tests). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray sessionArray = new JsonArray();
        for (ScanSession session : sessions) {
            sessionArray.add(sessionToJson(session));
        }
        root.add("sessions", sessionArray);
        JsonArray profileArray = new JsonArray();
        for (ScanProfile profile : profiles) {
            profileArray.add(profileToJson(profile));
        }
        root.add("profiles", profileArray);
        return root;
    }

    /** Serialises one session to JSON. */
    public static JsonObject sessionToJson(ScanSession session) {
        JsonObject json = new JsonObject();
        json.addProperty("id", session.id());
        json.addProperty("dimension", session.dimension());
        json.addProperty("state", session.state().name());
        json.addProperty("startedAt", session.startedAtMillis());
        json.addProperty("updatedAt", session.updatedAtMillis());
        json.addProperty("waypointIndex", session.waypointIndex());
        json.addProperty("routeSize", session.routeSize());
        json.addProperty("duplicates", session.duplicates());
        ScanBounds bounds = session.bounds();
        json.addProperty("minChunkX", bounds.minChunkX());
        json.addProperty("minChunkZ", bounds.minChunkZ());
        json.addProperty("maxChunkX", bounds.maxChunkX());
        json.addProperty("maxChunkZ", bounds.maxChunkZ());
        JsonArray chunks = new JsonArray();
        for (ScanSession.ChunkRecord record : session.chunks()) {
            JsonObject chunk = new JsonObject();
            chunk.addProperty("x", record.chunkX());
            chunk.addProperty("z", record.chunkZ());
            chunk.addProperty("category", record.category().name());
            chunk.addProperty("confidence", record.confidence());
            chunk.addProperty("manual", record.manual());
            chunk.addProperty("terrain", record.terrain().name());
            chunk.addProperty("surfaceY", record.surfaceY());
            chunk.addProperty("at", record.atMillis());
            chunks.add(chunk);
        }
        json.add("chunks", chunks);
        return json;
    }

    /** Parses a session, returning null when the object is not a usable session. */
    public static ScanSession readSession(JsonObject json) {
        if (json == null || !json.has("id") || !json.has("dimension")) {
            return null;
        }
        try {
            String id = json.get("id").getAsString();
            String dimension = json.get("dimension").getAsString();
            long started = json.has("startedAt") ? json.get("startedAt").getAsLong() : 0L;
            int minX = json.get("minChunkX").getAsInt();
            int minZ = json.get("minChunkZ").getAsInt();
            int maxX = json.get("maxChunkX").getAsInt();
            int maxZ = json.get("maxChunkZ").getAsInt();
            ScanBounds bounds = new ScanBounds(minX, minZ, maxX, maxZ);
            ScanSession session = new ScanSession(id, dimension, bounds, started);
            session.setState(parseState(json.has("state") ? json.get("state").getAsString() : "IDLE"),
                    json.has("updatedAt") ? json.get("updatedAt").getAsLong() : started);
            session.setProgress(json.has("waypointIndex") ? json.get("waypointIndex").getAsInt() : 0,
                    json.has("routeSize") ? json.get("routeSize").getAsInt() : 0, started);
            if (json.has("chunks") && json.get("chunks").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("chunks")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject chunk = element.getAsJsonObject();
                    int x = chunk.get("x").getAsInt();
                    int z = chunk.get("z").getAsInt();
                    StructureCategory category = parseCategory(chunk.has("category")
                            ? chunk.get("category").getAsString() : "OTHER");
                    double confidence = chunk.has("confidence") ? chunk.get("confidence").getAsDouble() : 0;
                    boolean manual = chunk.has("manual") && chunk.get("manual").getAsBoolean();
                    TerrainType terrain = parseTerrain(chunk.has("terrain") ? chunk.get("terrain").getAsString() : "UNKNOWN");
                    int surfaceY = chunk.has("surfaceY") ? chunk.get("surfaceY").getAsInt() : Integer.MIN_VALUE;
                    long at = chunk.has("at") ? chunk.get("at").getAsLong() : started;
                    session.putRaw(new ScanSession.ChunkRecord(x, z, category, confidence, manual, terrain,
                            surfaceY, at));
                }
            }
            return session;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ export / import

    /** A JSON document for one session (the same shape used in the store). */
    public static JsonObject export(ScanSession session) {
        JsonObject document = new JsonObject();
        document.addProperty("kind", "logvex-scan-session");
        document.addProperty("schema", SCHEMA_VERSION);
        document.add("session", sessionToJson(session));
        return document;
    }

    /**
     * Parses an exported document (or a bare session object) into a session. Returns null when the
     * document is not a usable session.
     */
    public static ScanSession importSession(JsonObject document) {
        if (document == null) {
            return null;
        }
        if (document.has("session") && document.get("session").isJsonObject()) {
            return readSession(document.getAsJsonObject("session"));
        }
        return readSession(document);
    }

    private static JsonObject profileToJson(ScanProfile profile) {
        JsonObject json = new JsonObject();
        json.addProperty("name", profile.name());
        ScanConfig c = profile.config();
        json.addProperty("radiusChunks", c.radiusChunks());
        json.addProperty("verticalRange", c.verticalRange());
        json.addProperty("sampleStep", c.sampleStep());
        json.addProperty("maxChunksPerTick", c.maxChunksPerTick());
        json.addProperty("routeStepBlocks", c.routeStepBlocks());
        json.addProperty("useFlight", c.useFlight());
        json.addProperty("classifyOffThread", c.classifyOffThread());
        json.addProperty("observationCap", c.observationCap());
        return json;
    }

    private static ScanProfile readProfile(JsonObject json) {
        if (json == null || !json.has("name")) {
            return null;
        }
        try {
            ScanConfig defaults = ScanConfig.defaults();
            ScanConfig config = new ScanConfig(
                    getInt(json, "radiusChunks", defaults.radiusChunks()),
                    getInt(json, "verticalRange", defaults.verticalRange()),
                    getInt(json, "sampleStep", defaults.sampleStep()),
                    getInt(json, "maxChunksPerTick", defaults.maxChunksPerTick()),
                    getInt(json, "routeStepBlocks", defaults.routeStepBlocks()),
                    getBool(json, "useFlight", defaults.useFlight()),
                    getBool(json, "classifyOffThread", defaults.classifyOffThread()),
                    getInt(json, "observationCap", defaults.observationCap()));
            return new ScanProfile(json.get("name").getAsString(), config);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static boolean getBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }

    private static ScanState parseState(String name) {
        try {
            return ScanState.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return ScanState.IDLE;
        }
    }

    private static StructureCategory parseCategory(String name) {
        try {
            return StructureCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return StructureCategory.OTHER;
        }
    }

    private static TerrainType parseTerrain(String name) {
        try {
            return TerrainType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return TerrainType.UNKNOWN;
        }
    }

    /** Convenience for tests: whether the backing file exists. */
    public boolean existsOnDisk() {
        return file != null && Files.exists(file);
    }
}
