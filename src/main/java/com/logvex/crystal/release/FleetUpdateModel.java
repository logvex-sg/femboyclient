package com.logvex.crystal.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides, for a whole fleet of instances, which ones can be updated and to what.
 *
 * <p>This is the model the Overseer dashboard renders. It is deliberately in the shared release
 * package rather than the Overseer edition: the selection rules are about releases, not about
 * administration, and keeping them here means the standard edition carries no administration code
 * while the same tested rules serve both.
 *
 * <p>Nothing here downloads or contacts an instance. It answers one question per member — "is there
 * a newer release for this member's edition and Minecraft version that this fleet can speak to?" —
 * and the caller decides what to do with the answer.
 */
public final class FleetUpdateModel {

    /** One member of the fleet, reduced to the fields that affect update selection. */
    public record Member(String instanceId, String minecraftVersion, String clientVersion) {
        public Member {
            Objects.requireNonNull(instanceId, "instanceId");
        }
    }

    /** What should happen to one member. */
    public enum Status {
        /** The member runs the newest release for its Minecraft version. */
        UP_TO_DATE,
        /** A newer compatible release exists. */
        UPDATE_AVAILABLE,
        /** No release exists for the member's Minecraft version in this catalog. */
        UNSUPPORTED,
        /** A release exists but its protocol is outside this fleet's supported range. */
        INCOMPATIBLE
    }

    /** The decision for one member, with the release to install when there is one. */
    public record Decision(Member member, Status status, ReleaseManifest target) {
        public boolean actionable() {
            return status == Status.UPDATE_AVAILABLE && target != null;
        }
    }

    private final Edition edition;
    private final ReleaseCatalog catalog;
    private final int protocolMin;
    private final int protocolMax;

    public FleetUpdateModel(Edition edition, ReleaseCatalog catalog, int protocolMin, int protocolMax) {
        this.edition = Objects.requireNonNull(edition, "edition");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.protocolMin = protocolMin;
        this.protocolMax = protocolMax;
    }

    /** Decides for one member. */
    public Decision decide(Member member) {
        String minecraft = member.minecraftVersion() == null ? "" : member.minecraftVersion();
        String current = member.clientVersion() == null ? "0.0.0" : member.clientVersion();

        List<ReleaseManifest> candidates = catalog.compatible(edition, minecraft,
                ReleaseChannel.STABLE, ReleaseChannel.EXPERIMENTAL);
        if (candidates.isEmpty()) {
            return new Decision(member, Status.UNSUPPORTED, null);
        }
        boolean anyProtocolCompatible = candidates.stream()
                .anyMatch(m -> m.protocolVersion() >= protocolMin && m.protocolVersion() <= protocolMax);
        if (!anyProtocolCompatible) {
            return new Decision(member, Status.INCOMPATIBLE, null);
        }
        ReleaseManifest best = catalog.bestUpdate(edition, minecraft, current, protocolMin, protocolMax,
                ReleaseChannel.STABLE, ReleaseChannel.EXPERIMENTAL);
        if (best == null) {
            return new Decision(member, Status.UP_TO_DATE, null);
        }
        return new Decision(member, Status.UPDATE_AVAILABLE, best);
    }

    /** Decides for every member, in the order given. */
    public List<Decision> decide(List<Member> members) {
        List<Decision> decisions = new ArrayList<>(members.size());
        for (Member member : members) {
            decisions.add(decide(member));
        }
        return decisions;
    }

    /** A one-line summary for the dashboard: how many of each status. */
    public static String summarise(List<Decision> decisions) {
        int upToDate = 0;
        int updatable = 0;
        int unsupported = 0;
        int incompatible = 0;
        for (Decision decision : decisions) {
            switch (decision.status()) {
                case UP_TO_DATE -> upToDate++;
                case UPDATE_AVAILABLE -> updatable++;
                case UNSUPPORTED -> unsupported++;
                case INCOMPATIBLE -> incompatible++;
            }
        }
        return decisions.size() + " instances: " + updatable + " updatable, " + upToDate
                + " up to date, " + unsupported + " unsupported, " + incompatible + " incompatible";
    }

    public Edition edition() {
        return edition;
    }

    public int protocolMin() {
        return protocolMin;
    }

    public int protocolMax() {
        return protocolMax;
    }
}
