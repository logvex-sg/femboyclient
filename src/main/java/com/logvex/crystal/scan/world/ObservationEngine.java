package com.logvex.crystal.scan.world;

import java.util.List;

/**
 * Turns a {@link ChunkSample} and a {@link Classification} into a verification verdict.
 *
 * <p>This is the component that keeps the mission's central promise: the scanner must never
 * present a guess as a finding. A verdict is confirmed only when the classification itself is
 * confident <em>and</em> enough independent signals agree. A weak classification, or a confident
 * one with no corroborating signals, stays {@link VerificationStatus#UNCERTAIN} and is stored as a
 * guess.
 *
 * <p>Deterministic and Minecraft-free: the same sample always yields the same verdict, so the
 * behaviour can be unit tested without a world and a local AI provider is never required.
 */
public final class ObservationEngine {

    /** A classification at least this confident is a finding on its own. */
    public static final double SELF_EVIDENT_CONFIDENCE = 0.85;

    private ObservationEngine() {
    }

    /**
     * The verification status for a classification of a sample.
     *
     * <p>Order of reasoning, highest first:
     * <ol>
     *   <li>an unclassified or low-confidence verdict is always UNCERTAIN;</li>
     *   <li>a verdict that is self-evident from a single unambiguous block (a portal, a spawner)
     *       is CONFIRMED regardless of the player-activity signals;</li>
     *   <li>otherwise the status comes from how many independent player-activity signals agree.</li>
     * </ol>
     */
    public static VerificationStatus verify(ChunkSample sample, Classification classification) {
        if (sample == null || classification == null || !sample.loaded()) {
            return VerificationStatus.UNCERTAIN;
        }
        if (classification.category() == StructureCategory.OTHER || !classification.confident()) {
            return VerificationStatus.UNCERTAIN;
        }
        if (classification.confidence() >= SELF_EVIDENT_CONFIDENCE) {
            return VerificationStatus.CONFIRMED;
        }
        return VerificationStatus.fromSignals(sample.signals().agreeingSignals());
    }

    /** The human-readable evidence behind a verdict, for the dashboard and AI summaries. */
    public static List<String> evidence(ChunkSample sample, Classification classification) {
        if (sample == null || classification == null) {
            return List.of();
        }
        List<String> evidence = new java.util.ArrayList<>(classification.reasons());
        for (String signal : sample.signals().describe()) {
            if (!evidence.contains(signal)) {
                evidence.add(signal);
            }
        }
        return List.copyOf(evidence);
    }

    /**
     * A one-line, plain-language statement of what was found and how sure the scanner is.
     * Never asserts certainty for an uncertain verdict.
     */
    public static String summary(ChunkSample sample, Classification classification,
                                 VerificationStatus status) {
        if (sample == null || classification == null) {
            return "no observation";
        }
        String where = "chunk " + sample.chunkX() + "," + sample.chunkZ();
        return switch (status) {
            case CONFIRMED -> "confirmed " + classification.category().label() + " at " + where;
            case LIKELY -> "likely " + classification.category().label() + " at " + where;
            case REJECTED -> "dismissed observation at " + where;
            case UNCERTAIN -> "possible " + classification.category().label() + " at " + where
                    + " (unconfirmed)";
        };
    }
}
