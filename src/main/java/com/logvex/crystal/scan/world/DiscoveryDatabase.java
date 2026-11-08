package com.logvex.crystal.scan.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The persistent discovery database: every observation, region record, route entry, AI summary
 * and user annotation the world intelligence system produces, in one schema-versioned JSON store.
 *
 * <p>It is deliberately defensive, matching the rest of LOGVEX's stores: a missing, empty or
 * malformed file loads as an empty store rather than throwing; writes are atomic with a backup
 * via {@link ConfigIo}; and a newer on-disk schema is refused rather than silently downgraded, so
 * a future version's data is never corrupted by an older client.
 *
 * <p>Search, filter, sort and pagination all run against an in-memory index so the dashboard can
 * query without touching the disk. The store is Minecraft-free and unit tested against a
 * temporary path.
 */
public final class DiscoveryDatabase {

    /** Schema version of the on-disk format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;
    /** Most recent observations retained; older ones are dropped so memory cannot grow forever. */
    public static final int OBSERVATION_CAP = 20000;
    /** Most recent route entries retained. */
    public static final int ROUTE_CAP = 2000;
    /** Most recent AI summaries retained. */
    public static final int SUMMARY_CAP = 200;

    /** One exploration-route entry: where the agent was sent and what came of it. */
    public record RouteEntry(String dimension, int blockX, int blockZ, long atMillis, String outcome) {
    }

    /** A stored AI (or deterministic) summary of a session or the whole store. */
    public record AiSummary(String scope, String dimension, long atMillis, String text, boolean fromModel) {
    }

    private final Path file;
    private final List<Observation> observations = new ArrayList<>();
    private final Map<Long, List<Observation>> byChunk = new LinkedHashMap<>();
    private final List<RouteEntry> routeHistory = new ArrayList<>();
    private final List<AiSummary> summaries = new ArrayList<>();

    private int skipped;

    public DiscoveryDatabase(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** How many malformed records were skipped on the last load. */
    public int skipped() {
        return skipped;
    }

    // ------------------------------------------------------------------ write

    /**
     * Adds an observation. An observation of a chunk that already has one is treated as an
     * update rather than a duplicate: the existing entry is replaced in place, so a chunk is
     * never counted twice.
     *
     * @return true when the observation was new, false when it replaced an existing one
     */
    public boolean add(Observation observation) {
        if (observation == null) {
            return false;
        }
        long key = ChunkKey.of(observation.chunkX(), observation.chunkZ());
        List<Observation> existing = byChunk.get(key);
        if (existing == null || existing.isEmpty()) {
            byChunk.put(key, new ArrayList<>(List.of(observation)));
            observations.add(observation);
        } else {
            Observation previous = existing.get(0);
            int index = observations.indexOf(previous);
            if (index >= 0) {
                observations.set(index, observation);
            } else {
                observations.add(observation);
            }
            existing.set(0, observation);
        }
        trim();
        return existing == null;
    }

    /** True when a chunk already has a recorded observation. */
    public boolean contains(int chunkX, int chunkZ) {
        return byChunk.containsKey(ChunkKey.of(chunkX, chunkZ));
    }

    /** Applies a user annotation to an observation by id. Returns the updated record, or null. */
    public Observation annotate(String id, String note) {
        return replace(id, observation -> observation.withNote(note));
    }

    /** Sets a user-reviewed verification status on an observation by id. */
    public Observation setVerification(String id, VerificationStatus status) {
        return replace(id, observation -> observation.withVerification(status));
    }

    private Observation replace(String id, java.util.function.UnaryOperator<Observation> change) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < observations.size(); i++) {
            Observation current = observations.get(i);
            if (current.id().equals(id)) {
                Observation updated = change.apply(current);
                observations.set(i, updated);
                byChunk.put(ChunkKey.of(updated.chunkX(), updated.chunkZ()),
                        new ArrayList<>(List.of(updated)));
                return updated;
            }
        }
        return null;
    }

    /** Records a route entry, bounded by {@link #ROUTE_CAP}. */
    public void addRoute(RouteEntry entry) {
        if (entry == null) {
            return;
        }
        routeHistory.add(entry);
        while (routeHistory.size() > ROUTE_CAP) {
            routeHistory.remove(0);
        }
    }

    /** Records an AI/deterministic summary, bounded by {@link #SUMMARY_CAP}. */
    public void addSummary(AiSummary summary) {
        if (summary == null) {
            return;
        }
        summaries.add(summary);
        while (summaries.size() > SUMMARY_CAP) {
            summaries.remove(0);
        }
    }

    private void trim() {
        while (observations.size() > OBSERVATION_CAP) {
            Observation removed = observations.remove(0);
            byChunk.remove(ChunkKey.of(removed.chunkX(), removed.chunkZ()));
        }
    }

    /** Deletes one observation by id. Returns true when it existed. */
    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < observations.size(); i++) {
            if (observations.get(i).id().equals(id)) {
                Observation removed = observations.remove(i);
                byChunk.remove(ChunkKey.of(removed.chunkX(), removed.chunkZ()));
                return true;
            }
        }
        return false;
    }

    /** Deletes every observation in a dimension. Returns how many were removed. */
    public int deleteDimension(String dimension) {
        int before = observations.size();
        observations.removeIf(o -> o.dimension().equals(dimension));
        rebuildIndex();
        return before - observations.size();
    }

    /** Deletes every observation older than {@code cutoffMillis}. Returns how many were removed. */
    public int deleteOlderThan(long cutoffMillis) {
        int before = observations.size();
        observations.removeIf(o -> o.atMillis() < cutoffMillis);
        rebuildIndex();
        return before - observations.size();
    }

    /** Deletes everything. */
    public void clear() {
        observations.clear();
        byChunk.clear();
        routeHistory.clear();
        summaries.clear();
    }

    private void rebuildIndex() {
        byChunk.clear();
        for (Observation observation : observations) {
            byChunk.put(ChunkKey.of(observation.chunkX(), observation.chunkZ()),
                    new ArrayList<>(List.of(observation)));
        }
    }

    // ------------------------------------------------------------------ read

    /** Every observation, oldest first. */
    public List<Observation> observations() {
        return List.copyOf(observations);
    }

    public List<RouteEntry> routeHistory() {
        return List.copyOf(routeHistory);
    }

    public List<AiSummary> summaries() {
        return List.copyOf(summaries);
    }

    public int size() {
        return observations.size();
    }

    /** Observations in one dimension, oldest first. */
    public List<Observation> forDimension(String dimension) {
        List<Observation> result = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.dimension().equals(dimension)) {
                result.add(observation);
            }
        }
        return result;
    }

    /** Observations that count as real discoveries (see {@link VerificationStatus}). */
    public List<Observation> discoveries(String dimension) {
        List<Observation> result = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.dimension().equals(dimension) && observation.discovered()) {
                result.add(observation);
            }
        }
        return result;
    }

    /**
     * Searches observations with a filter, a sort order and pagination.
     *
     * @param query   a case-insensitive substring matched against category, verification, note and
     *                evidence; an empty query matches everything
     * @param filter  an optional predicate (category/verification/confidence), or null
     * @param sort    the sort order
     * @param offset  the first result index (for pagination)
     * @param limit   the most results to return
     */
    public List<Observation> search(String query, java.util.function.Predicate<Observation> filter,
                                    SortOrder sort, int offset, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Observation> result = new ArrayList<>();
        for (Observation observation : observations) {
            if (filter != null && !filter.test(observation)) {
                continue;
            }
            if (!needle.isEmpty() && !matches(observation, needle)) {
                continue;
            }
            result.add(observation);
        }
        result.sort(sort.comparator());
        int from = Math.max(0, Math.min(offset, result.size()));
        int to = Math.max(from, Math.min(from + Math.max(0, limit), result.size()));
        return new ArrayList<>(result.subList(from, to));
    }

    private static boolean matches(Observation observation, String needle) {
        if (observation.category().label().toLowerCase(Locale.ROOT).contains(needle)
                || observation.verification().label().contains(needle)
                || observation.note().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String evidence : observation.evidence()) {
            if (evidence.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** How many observations match a query and filter, without materialising the page. */
    public int countMatching(String query, java.util.function.Predicate<Observation> filter) {
        return search(query, filter, SortOrder.RECENT, 0, Integer.MAX_VALUE).size();
    }

    /** Distinct dimensions that have observations. */
    public Set<String> dimensions() {
        Set<String> result = new LinkedHashSet<>();
        for (Observation observation : observations) {
            result.add(observation.dimension());
        }
        return result;
    }

    /** Observation count per category in a dimension, for the dashboard legend. */
    public Map<StructureCategory, Integer> categoryCounts(String dimension) {
        Map<StructureCategory, Integer> counts = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation.dimension().equals(dimension)) {
                counts.merge(observation.category(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** How many observations were recorded on or after {@code sinceMillis}. */
    public int countSince(long sinceMillis) {
        int count = 0;
        for (Observation observation : observations) {
            if (observation.atMillis() >= sinceMillis) {
                count++;
            }
        }
        return count;
    }

    /** The most recent observations, newest first, up to {@code limit}. */
    public List<Observation> recent(int limit) {
        return search("", null, SortOrder.RECENT, 0, limit);
    }

    /** How observations can be ordered. */
    public enum SortOrder {
        RECENT,
        OLDEST,
        CATEGORY,
        CONFIDENCE;

        public Comparator<Observation> comparator() {
            return switch (this) {
                case RECENT -> Comparator.comparingLong(Observation::atMillis).reversed();
                case OLDEST -> Comparator.comparingLong(Observation::atMillis);
                case CATEGORY -> Comparator.comparing(o -> o.category().label());
                case CONFIDENCE -> Comparator.comparingDouble(Observation::confidence).reversed();
            };
        }
    }

    // ------------------------------------------------------------------ persistence

    /** Loads the store from disk. A missing or corrupt file yields an empty store. */
    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        observations.clear();
        byChunk.clear();
        routeHistory.clear();
        summaries.clear();
        skipped = 0;
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (root.has("observations") && root.get("observations").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("observations")) {
                Observation observation = element.isJsonObject() ? readObservation(element.getAsJsonObject()) : null;
                if (observation == null) {
                    skipped++;
                } else {
                    observations.add(observation);
                }
            }
        }
        rebuildIndex();
        if (root.has("routes") && root.get("routes").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("routes")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject route = element.getAsJsonObject();
                routeHistory.add(new RouteEntry(getString(route, "dimension", ""),
                        getInt(route, "x", 0), getInt(route, "z", 0), getLong(route, "at", 0L),
                        getString(route, "outcome", "")));
            }
        }
        if (root.has("summaries") && root.get("summaries").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("summaries")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject summary = element.getAsJsonObject();
                summaries.add(new AiSummary(getString(summary, "scope", ""), getString(summary, "dimension", ""),
                        getLong(summary, "at", 0L), getString(summary, "text", ""),
                        summary.has("fromModel") && summary.get("fromModel").getAsBoolean()));
            }
        }
        trim();
    }

    /** Writes the store to disk atomically. */
    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    /** Serialises the whole store (exposed for tests and export). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray observationArray = new JsonArray();
        for (Observation observation : observations) {
            observationArray.add(observationToJson(observation));
        }
        root.add("observations", observationArray);
        JsonArray routeArray = new JsonArray();
        for (RouteEntry route : routeHistory) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", route.dimension());
            json.addProperty("x", route.blockX());
            json.addProperty("z", route.blockZ());
            json.addProperty("at", route.atMillis());
            json.addProperty("outcome", route.outcome());
            routeArray.add(json);
        }
        root.add("routes", routeArray);
        JsonArray summaryArray = new JsonArray();
        for (AiSummary summary : summaries) {
            JsonObject json = new JsonObject();
            json.addProperty("scope", summary.scope());
            json.addProperty("dimension", summary.dimension());
            json.addProperty("at", summary.atMillis());
            json.addProperty("text", summary.text());
            json.addProperty("fromModel", summary.fromModel());
            summaryArray.add(json);
        }
        root.add("summaries", summaryArray);
        return root;
    }

    /** Serialises one observation. */
    public static JsonObject observationToJson(Observation observation) {
        JsonObject json = new JsonObject();
        json.addProperty("id", observation.id());
        json.addProperty("session", observation.sessionId());
        json.addProperty("dimension", observation.dimension());
        json.addProperty("chunkX", observation.chunkX());
        json.addProperty("chunkZ", observation.chunkZ());
        json.addProperty("at", observation.atMillis());
        json.addProperty("category", observation.category().name());
        json.addProperty("confidence", observation.confidence());
        json.addProperty("verification", observation.verification().name());
        json.addProperty("note", observation.note());
        JsonArray evidence = new JsonArray();
        for (String reason : observation.evidence()) {
            evidence.add(reason);
        }
        json.add("evidence", evidence);
        ObservationSignals signals = observation.signals();
        if (signals != null) {
            JsonObject signalJson = new JsonObject();
            signalJson.addProperty("builtDensity", signals.builtDensity());
            signalJson.addProperty("storage", signals.storageCount());
            signalJson.addProperty("spawners", signals.spawnerCount());
            signalJson.addProperty("crops", signals.cropCount());
            signalJson.addProperty("redstone", signals.redstoneCount());
            signalJson.addProperty("light", signals.lightCount());
            signalJson.addProperty("ores", signals.oreCount());
            signalJson.addProperty("naturalVeg", signals.naturalVeg());
            signalJson.addProperty("flowingLiquids", signals.flowingLiquids());
            signalJson.addProperty("inhabitedTicks", signals.inhabitedTicks());
            if (signals.lightComplete() != null) {
                signalJson.addProperty("lightComplete", signals.lightComplete());
            }
            json.add("signals", signalJson);
        }
        return json;
    }

    /** Parses an observation, returning null when the object is not usable. */
    public static Observation readObservation(JsonObject json) {
        if (json == null || !json.has("id") || !json.has("dimension")) {
            return null;
        }
        try {
            String id = json.get("id").getAsString();
            String session = getString(json, "session", "");
            String dimension = json.get("dimension").getAsString();
            int chunkX = getInt(json, "chunkX", 0);
            int chunkZ = getInt(json, "chunkZ", 0);
            long at = getLong(json, "at", 0L);
            StructureCategory category = parseCategory(getString(json, "category", "OTHER"));
            double confidence = json.has("confidence") ? json.get("confidence").getAsDouble() : 0;
            VerificationStatus verification = parseVerification(getString(json, "verification", "UNCERTAIN"));
            String note = getString(json, "note", "");
            List<String> evidence = new ArrayList<>();
            if (json.has("evidence") && json.get("evidence").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("evidence")) {
                    if (element.isJsonPrimitive()) {
                        evidence.add(element.getAsString());
                    }
                }
            }
            ObservationSignals signals = null;
            if (json.has("signals") && json.get("signals").isJsonObject()) {
                JsonObject signalJson = json.getAsJsonObject("signals");
                signals = new ObservationSignals(
                        signalJson.has("builtDensity") ? signalJson.get("builtDensity").getAsDouble() : 0,
                        getInt(signalJson, "storage", 0),
                        getInt(signalJson, "spawners", 0),
                        getInt(signalJson, "crops", 0),
                        getInt(signalJson, "redstone", 0),
                        getInt(signalJson, "light", 0),
                        getInt(signalJson, "ores", 0),
                        getInt(signalJson, "naturalVeg", 0),
                        signalJson.has("flowingLiquids") && signalJson.get("flowingLiquids").getAsBoolean(),
                        getLong(signalJson, "inhabitedTicks", -1L),
                        signalJson.has("lightComplete") ? signalJson.get("lightComplete").getAsBoolean() : null);
            }
            return new Observation(id, session, dimension, chunkX, chunkZ, at, category, confidence,
                    verification, evidence, signals, note);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static long getLong(JsonObject json, String key, long fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsLong() : fallback;
    }

    /** Parses a category name, defaulting to OTHER rather than throwing. */
    public static StructureCategory parseCategory(String name) {
        if (name == null) {
            return StructureCategory.OTHER;
        }
        try {
            return StructureCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return StructureCategory.OTHER;
        }
    }

    /** Parses a verification status, defaulting to UNCERTAIN rather than throwing. */
    public static VerificationStatus parseVerification(String name) {
        if (name == null) {
            return VerificationStatus.UNCERTAIN;
        }
        try {
            return VerificationStatus.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VerificationStatus.UNCERTAIN;
        }
    }
}
