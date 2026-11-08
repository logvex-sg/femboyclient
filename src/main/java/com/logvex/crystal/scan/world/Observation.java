package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One recorded observation of the world: what was seen, where, when, how sure the scanner is, and
 * why.
 *
 * <p>Observations are the unit the discovery database, the dashboard's Discoveries workspace and
 * the AI layer all work with. An observation is deliberately explicit about its evidence and its
 * {@link VerificationStatus}, so a consumer can always tell a confirmed find from a guess and
 * show the reasons.
 *
 * <p>{@code note} is a free-text user annotation; {@link #withNote} returns a copy so an
 * observation is otherwise immutable once recorded.
 *
 * @param id           stable identifier, unique within a session
 * @param sessionId    the scan session that produced it, or {@code ""} for an imported record
 * @param dimension    the dimension id, e.g. {@code minecraft:overworld}
 * @param chunkX       chunk x
 * @param chunkZ       chunk z
 * @param atMillis     wall-clock time the observation was recorded
 * @param category     the classified structure category
 * @param confidence   the classifier's confidence in {@code [0,1]}
 * @param verification how well evidenced the verdict is
 * @param evidence     the human-readable reasons and signals behind the verdict
 * @param signals      the raw evidence signals
 * @param note         a user annotation, or {@code ""}
 */
public record Observation(
        String id,
        String sessionId,
        String dimension,
        int chunkX,
        int chunkZ,
        long atMillis,
        StructureCategory category,
        double confidence,
        VerificationStatus verification,
        List<String> evidence,
        ObservationSignals signals,
        String note) {

    public Observation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        note = note == null ? "" : note;
        if (verification == null) {
            verification = VerificationStatus.UNCERTAIN;
        }
    }

    /** The region this observation falls in. */
    public int regionX() {
        return RegionGrid.regionOf(chunkX);
    }

    /** The region this observation falls in. */
    public int regionZ() {
        return RegionGrid.regionOf(chunkZ);
    }

    /** The chunk's centre in block coordinates, for map rendering and navigation. */
    public int blockX() {
        return (chunkX << 4) + 8;
    }

    /** The chunk's centre in block coordinates, for map rendering and navigation. */
    public int blockZ() {
        return (chunkZ << 4) + 8;
    }

    /** True when this observation may be counted and displayed as a real discovery. */
    public boolean discovered() {
        return verification.countsAsDiscovery();
    }

    /** A copy with a user annotation. */
    public Observation withNote(String newNote) {
        return new Observation(id, sessionId, dimension, chunkX, chunkZ, atMillis, category,
                confidence, verification, evidence, signals, newNote);
    }

    /** A copy with a user-reviewed verification status (for confirm/dismiss actions). */
    public Observation withVerification(VerificationStatus status) {
        return new Observation(id, sessionId, dimension, chunkX, chunkZ, atMillis, category,
                confidence, status, evidence, signals, note);
    }

    /** A one-line summary suitable for a list row. */
    public String summary() {
        String where = blockX() + ", " + blockZ();
        return category.label() + " \u00b7 " + verification.label() + " \u00b7 " + where;
    }

    /** The evidence rendered as a single comma-separated line. */
    public String evidenceLine() {
        return evidence.isEmpty() ? "no evidence recorded" : String.join(", ", evidence);
    }

    /** Groups observations by region, preserving insertion order. */
    public static Map<Long, List<Observation>> byRegion(List<Observation> observations) {
        Map<Long, List<Observation>> grouped = new LinkedHashMap<>();
        for (Observation observation : observations) {
            grouped.computeIfAbsent(RegionGrid.key(observation.regionX(), observation.regionZ()),
                    k -> new ArrayList<>()).add(observation);
        }
        return grouped;
    }
}
