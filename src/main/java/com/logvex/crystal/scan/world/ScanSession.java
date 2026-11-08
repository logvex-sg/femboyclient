package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One persistent scan session: the region that was scanned, its progress, and every chunk the
 * run discovered, keyed by chunk so re-observing a chunk updates it rather than duplicating it.
 *
 * <p>A session is deliberately Minecraft-free and serialisable so it can be written to JSON,
 * exported, imported and unit tested. It is mutable while a run is in progress (chunks are added,
 * the state changes) and frozen once the run ends.
 */
public final class ScanSession {

    /** One discovered chunk, compact enough to store thousands of them. */
    public record ChunkRecord(int chunkX, int chunkZ, StructureCategory category, double confidence,
                              boolean manual, TerrainType terrain, int surfaceY, long atMillis) {

        public long key() {
            return ChunkKey.of(chunkX, chunkZ);
        }
    }

    private final String id;
    private final String dimension;
    private final ScanBounds bounds;
    private final long startedAtMillis;
    private final Map<Long, ChunkRecord> chunks = new LinkedHashMap<>();

    private ScanState state = ScanState.IDLE;
    private long updatedAtMillis;
    private int waypointIndex;
    private int routeSize;
    private int duplicates;

    public ScanSession(String id, String dimension, ScanBounds bounds, long startedAtMillis) {
        this.id = id;
        this.dimension = dimension;
        this.bounds = bounds;
        this.startedAtMillis = startedAtMillis;
        this.updatedAtMillis = startedAtMillis;
    }

    public String id() {
        return id;
    }

    public String dimension() {
        return dimension;
    }

    public ScanBounds bounds() {
        return bounds;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    public ScanState state() {
        return state;
    }

    public int waypointIndex() {
        return waypointIndex;
    }

    public int routeSize() {
        return routeSize;
    }

    /** How many chunk observations were duplicates of a chunk already in the session. */
    public int duplicates() {
        return duplicates;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public List<ChunkRecord> chunks() {
        return new ArrayList<>(chunks.values());
    }

    public ChunkRecord chunk(int chunkX, int chunkZ) {
        return chunks.get(ChunkKey.of(chunkX, chunkZ));
    }

    /** Number of confident, non-OTHER discoveries in the session. */
    public int discoveryCount() {
        int count = 0;
        for (ChunkRecord record : chunks.values()) {
            if (record.confidence() >= 0.6 && record.category() != StructureCategory.OTHER) {
                count++;
            }
        }
        return count;
    }

    /** Count of chunks per category. */
    public Map<StructureCategory, Integer> categoryCounts() {
        Map<StructureCategory, Integer> counts = new LinkedHashMap<>();
        for (ChunkRecord record : chunks.values()) {
            counts.merge(record.category(), 1, Integer::sum);
        }
        return counts;
    }

    public void setState(ScanState state, long nowMillis) {
        this.state = state;
        this.updatedAtMillis = nowMillis;
    }

    public void setProgress(int waypointIndex, int routeSize, long nowMillis) {
        this.waypointIndex = waypointIndex;
        this.routeSize = routeSize;
        this.updatedAtMillis = nowMillis;
    }

    /**
     * Records a discovered chunk. Returns true when it was new; a chunk already in the session is
     * updated in place (and counted as a duplicate) rather than added again, so a long run that
     * passes the same area twice does not inflate the discovery count. A manual correction is
     * never overwritten by an automatic one.
     */
    public boolean recordChunk(MappedChunk mapped, long nowMillis) {
        long key = mapped.key();
        ChunkRecord existing = chunks.get(key);
        boolean isNew = existing == null;
        if (!isNew) {
            duplicates++;
        }
        StructureCategory category = mapped.category();
        double confidence = mapped.confidence();
        boolean manual = mapped.manual();
        if (existing != null && existing.manual() && !manual) {
            category = existing.category();
            confidence = existing.confidence();
            manual = true;
        }
        chunks.put(key, new ChunkRecord(mapped.sample().chunkX(), mapped.sample().chunkZ(), category,
                confidence, manual, mapped.sample().terrain(), mapped.sample().surfaceY(), nowMillis));
        this.updatedAtMillis = nowMillis;
        return isNew;
    }

    /** Inserts a pre-built record verbatim; used only when loading a persisted session. */
    public void putRaw(ChunkRecord record) {
        if (record == null) {
            return;
        }
        chunks.put(record.key(), record);
        this.updatedAtMillis = Math.max(this.updatedAtMillis, record.atMillis());
    }

    /** Applies a user correction to a stored chunk. */
    public boolean correct(int chunkX, int chunkZ, StructureCategory category, long nowMillis) {
        ChunkRecord existing = chunks.get(ChunkKey.of(chunkX, chunkZ));
        if (existing == null) {
            return false;
        }
        chunks.put(existing.key(), new ChunkRecord(existing.chunkX(), existing.chunkZ(), category, 1.0, true,
                existing.terrain(), existing.surfaceY(), nowMillis));
        this.updatedAtMillis = nowMillis;
        return true;
    }

    /** Duration in milliseconds, using the last update as the end (so a live run shows elapsed). */
    public long durationMillis() {
        return Math.max(0, updatedAtMillis - startedAtMillis);
    }

    /** A compact one-line summary for the session-history list. */
    public String summary() {
        return id + " \u00b7 " + dimension + " \u00b7 " + state.label() + " \u00b7 "
                + chunkCount() + " chunks \u00b7 " + discoveryCount() + " finds";
    }
}
