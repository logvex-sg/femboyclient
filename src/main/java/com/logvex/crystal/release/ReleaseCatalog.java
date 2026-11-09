package com.logvex.crystal.release;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The set of releases the client knows about, and the queries the updater asks of it.
 *
 * <p>This is pure selection logic over already-parsed {@link ReleaseManifest}s. It never downloads
 * anything: the caller fetches a catalog, parses each manifest, and hands them here. Keeping the
 * selection separate from the I/O makes the "which update applies to me" rules testable, and makes it
 * impossible for a network error to be mistaken for a compatible update.
 */
public final class ReleaseCatalog {

    private final List<ReleaseManifest> manifests;

    public ReleaseCatalog(List<ReleaseManifest> manifests) {
        this.manifests = List.copyOf(Objects.requireNonNull(manifests, "manifests"));
    }

    /** An empty catalog, used before any fetch has happened. */
    public static ReleaseCatalog empty() {
        return new ReleaseCatalog(List.of());
    }

    /** Every manifest in the catalog. */
    public List<ReleaseManifest> all() {
        return manifests;
    }

    /**
     * The manifests that could apply to this installation: the same edition and Minecraft version,
     * on one of the channels the user accepts.
     */
    public List<ReleaseManifest> compatible(Edition edition, String minecraftVersion,
                                            ReleaseChannel... acceptedChannels) {
        List<ReleaseManifest> result = new ArrayList<>();
        for (ReleaseManifest manifest : manifests) {
            if (manifest.edition() != edition) {
                continue;
            }
            if (!manifest.minecraftVersion().equals(minecraftVersion)) {
                continue;
            }
            for (ReleaseChannel channel : acceptedChannels) {
                if (manifest.releaseChannel() == channel) {
                    result.add(manifest);
                    break;
                }
            }
        }
        result.sort(Comparator.comparing(ReleaseManifest::clientVersion, ReleaseCatalog::compareVersions));
        return result;
    }

    /**
     * The best update for this installation, or {@code null} when there is none.
     *
     * <p>"Best" is the highest client version that is strictly newer than {@code currentVersion} and
     * whose protocol this build can speak. A manifest for a different edition or Minecraft version
     * is never a candidate, so an agent is never offered an incompatible jar.
     */
    public ReleaseManifest bestUpdate(Edition edition, String minecraftVersion, String currentVersion,
                                      int supportedProtocolMin, int supportedProtocolMax,
                                      ReleaseChannel... acceptedChannels) {
        ReleaseManifest best = null;
        for (ReleaseManifest manifest : compatible(edition, minecraftVersion, acceptedChannels)) {
            if (manifest.protocolVersion() < supportedProtocolMin
                    || manifest.protocolVersion() > supportedProtocolMax) {
                continue;
            }
            if (compareVersions(manifest.clientVersion(), currentVersion) <= 0) {
                continue;
            }
            if (best == null
                    || compareVersions(manifest.clientVersion(), best.clientVersion()) > 0) {
                best = manifest;
            }
        }
        return best;
    }

    /**
     * Compares two dotted numeric versions field by field. A non-numeric field is compared as text
     * only when a numeric comparison is impossible, so a pre-release suffix never sorts above the
     * release it precedes by accident.
     */
    static int compareVersions(String a, String b) {
        String[] left = a.split("[.-]");
        String[] right = b.split("[.-]");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            Integer ln = parseInt(l);
            Integer rn = parseInt(r);
            if (ln != null && rn != null) {
                int cmp = Integer.compare(ln, rn);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                int cmp = l.compareTo(r);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return 0;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
