package com.logvex.crystal.scan.world;

import java.util.List;

/**
 * The classifier's verdict on one chunk: a category, a confidence in {@code [0,1]}, and the
 * human-readable reasons that produced it.
 *
 * <p>The confidence is the mechanism the mission calls for: a chunk the classifier is unsure
 * about keeps a low confidence and lands in {@link StructureCategory#OTHER} rather than being
 * asserted as a confident find. The reasons are surfaced in the dashboard so a user can see
 * why a chunk was labelled and correct it.
 */
public record Classification(StructureCategory category, double confidence, List<String> reasons) {

    public Classification {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        confidence = Math.max(0, Math.min(1, confidence));
    }

    /** An explicit "unknown / not enough evidence" verdict. */
    public static Classification unknown(String reason) {
        return new Classification(StructureCategory.OTHER, 0.0, List.of(reason));
    }

    /** True when the verdict is a confident, non-{@code OTHER} find. */
    public boolean confident() {
        return confidence >= 0.6 && category != StructureCategory.OTHER;
    }

    /** A short one-line description for the dashboard's recent-observation list. */
    public String summary() {
        return category.label() + " " + Math.round(confidence * 100) + "%";
    }
}
