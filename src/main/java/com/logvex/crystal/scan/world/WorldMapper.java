package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The world map: what the scanner has seen, per dimension, indexed by chunk.
 *
 * <p>A single {@link LinkedHashMap} keyed by the packed chunk coordinate gives O(1) lookup and
 * insertion while preserving discovery order (useful for the recent-observations list). The map
 * is split per dimension so a scan of the overworld is never mixed with the nether: each
 * dimension has its own index, and switching dimensions never invalidates the other's data.
 *
 * <p>The mapper only records; it performs no I/O and holds no Minecraft types, so it is cheap to
 * drive from a tick and trivial to unit test. Persistence is the database's job, rendering is the
 * dashboard's job, and neither reaches into this class's internals.
 */
public final class WorldMapper {

    private final Map<String, Map<Long, MappedChunk>> dimensions = new LinkedHashMap<>();
    private final int perDimensionCap;
    private long version;

    /** A mapper bounded to {@code perDimensionCap} chunks per dimension (oldest evicted first). */
    public WorldMapper(int perDimensionCap) {
        this.perDimensionCap = Math.max(64, perDimensionCap);
    }

    /**
     * Records a sample. A chunk that was already known is updated in place (so an unchanged
     * region is not reprocessed into a new object graph); a new chunk is appended. Returns the
     * stored entry.
     */
    public MappedChunk record(ChunkSample sample, Classification classification, long nowMillis) {
        if (sample == null || sample.dimension() == null) {
            return null;
        }
        Map<Long, MappedChunk> index = dimensions.computeIfAbsent(sample.dimension(), d -> new LinkedHashMap<>());
        long key = ChunkKey.of(sample.chunkX(), sample.chunkZ());
        MappedChunk existing = index.get(key);
        MappedChunk updated;
        if (existing != null) {
            updated = new MappedChunk(sample, existing.category(), existing.confidence(),
                    existing.manual(), nowMillis);
            if (!existing.manual() && classification != null) {
                updated = updated.withClassification(classification, nowMillis);
            }
        } else {
            updated = new MappedChunk(sample,
                    classification == null ? StructureCategory.OTHER : classification.category(),
                    classification == null ? 0 : classification.confidence(), false, nowMillis);
        }
        index.put(key, updated);
        evictIfNeeded(index);
        version++;
        return updated;
    }

    private void evictIfNeeded(Map<Long, MappedChunk> index) {
        while (index.size() > perDimensionCap) {
            var it = index.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            it.next();
            it.remove();
        }
    }

    /** Applies a user correction to a chunk, creating the entry if the chunk is known. */
    public boolean correct(String dimension, int chunkX, int chunkZ, StructureCategory category, long nowMillis) {
        Map<Long, MappedChunk> index = dimensions.get(dimension);
        if (index == null) {
            return false;
        }
        long key = ChunkKey.of(chunkX, chunkZ);
        MappedChunk existing = index.get(key);
        if (existing == null) {
            return false;
        }
        index.put(key, existing.withManual(category, nowMillis));
        version++;
        return true;
    }

    /** True when the chunk is already mapped for the dimension. */
    public boolean contains(String dimension, int chunkX, int chunkZ) {
        Map<Long, MappedChunk> index = dimensions.get(dimension);
        return index != null && index.containsKey(ChunkKey.of(chunkX, chunkZ));
    }

    public MappedChunk get(String dimension, int chunkX, int chunkZ) {
        Map<Long, MappedChunk> index = dimensions.get(dimension);
        return index == null ? null : index.get(ChunkKey.of(chunkX, chunkZ));
    }

    /** Every mapped chunk in a dimension, in discovery order. */
    public List<MappedChunk> chunks(String dimension) {
        Map<Long, MappedChunk> index = dimensions.get(dimension);
        return index == null ? List.of() : new ArrayList<>(index.values());
    }

    /** Every dimension the mapper holds data for. */
    public Collection<String> dimensions() {
        return List.copyOf(dimensions.keySet());
    }

    public int size(String dimension) {
        Map<Long, MappedChunk> index = dimensions.get(dimension);
        return index == null ? 0 : index.size();
    }

    /** Total mapped chunks across every dimension. */
    public int totalSize() {
        int total = 0;
        for (Map<Long, MappedChunk> index : dimensions.values()) {
            total += index.size();
        }
        return total;
    }

    /** Number of chunks in a dimension whose (non-manual) verdict is a confident find. */
    public int discoveryCount(String dimension) {
        int count = 0;
        for (MappedChunk chunk : chunks(dimension)) {
            if (chunk.confidence() >= 0.6 && chunk.category() != StructureCategory.OTHER) {
                count++;
            }
        }
        return count;
    }

    /** The most recently updated chunks in a dimension, newest first, up to {@code limit}. */
    public List<MappedChunk> recent(String dimension, int limit) {
        List<MappedChunk> all = new ArrayList<>(chunks(dimension));
        all.sort((a, b) -> Long.compare(b.updatedAtMillis(), a.updatedAtMillis()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Chunks in a dimension matching a predicate (used for filtering by category/confidence). */
    public List<MappedChunk> filter(String dimension, Predicate<MappedChunk> predicate) {
        List<MappedChunk> result = new ArrayList<>();
        for (MappedChunk chunk : chunks(dimension)) {
            if (predicate.test(chunk)) {
                result.add(chunk);
            }
        }
        return result;
    }

    /** The count of chunks per category in a dimension (for the dashboard legend). */
    public Map<StructureCategory, Integer> categoryCounts(String dimension) {
        Map<StructureCategory, Integer> counts = new LinkedHashMap<>();
        for (MappedChunk chunk : chunks(dimension)) {
            counts.merge(chunk.category(), 1, Integer::sum);
        }
        return counts;
    }

    /** Monotonic counter bumped on every mutation, so a view can skip redraws when unchanged. */
    public long version() {
        return version;
    }

    /** Forgets one dimension (e.g. after an explicit map reset). */
    public void clearDimension(String dimension) {
        if (dimensions.remove(dimension) != null) {
            version++;
        }
    }

    /** Forgets everything. */
    public void clear() {
        dimensions.clear();
        version++;
    }
}
