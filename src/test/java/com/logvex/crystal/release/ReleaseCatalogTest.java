package com.logvex.crystal.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Selection rules: which release, if any, applies to a given installation. */
class ReleaseCatalogTest {

    private static ReleaseManifest manifest(Edition edition, String minecraft, String version,
                                            int protocol, ReleaseChannel channel) {
        return new ReleaseManifest(edition, version, minecraft, "modern", protocol, ">=0.20.1", "21",
                "logvex-crystal-" + edition.wire() + "-" + minecraft + "-" + version + ".jar",
                "a".repeat(64), "sig", channel);
    }

    @Test
    void picksTheHighestNewerCompatibleVersion() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.STANDARD, "1.21.11", "1.0.0", 1, ReleaseChannel.STABLE),
                manifest(Edition.STANDARD, "1.21.11", "1.2.0", 1, ReleaseChannel.STABLE),
                manifest(Edition.STANDARD, "1.21.11", "1.1.0", 1, ReleaseChannel.STABLE)));

        ReleaseManifest best = catalog.bestUpdate(
                Edition.STANDARD, "1.21.11", "1.0.0", 1, 1, ReleaseChannel.STABLE);

        assertEquals("1.2.0", best.clientVersion());
    }

    @Test
    void neverOffersAnOlderOrEqualVersion() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.STANDARD, "1.21.11", "1.0.0", 1, ReleaseChannel.STABLE)));

        assertNull(catalog.bestUpdate(Edition.STANDARD, "1.21.11", "1.0.0", 1, 1, ReleaseChannel.STABLE));
        assertNull(catalog.bestUpdate(Edition.STANDARD, "1.21.11", "2.0.0", 1, 1, ReleaseChannel.STABLE));
    }

    @Test
    void neverOffersAnotherEditionsArtifact() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.OVERSEER, "1.21.11", "9.9.9", 1, ReleaseChannel.STABLE)));

        assertNull(catalog.bestUpdate(Edition.STANDARD, "1.21.11", "1.0.0", 1, 1, ReleaseChannel.STABLE));
    }

    @Test
    void neverOffersAnArtifactForAnotherMinecraftVersion() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.STANDARD, "1.21.1", "9.9.9", 1, ReleaseChannel.STABLE)));

        assertNull(catalog.bestUpdate(Edition.STANDARD, "1.21.11", "1.0.0", 1, 1, ReleaseChannel.STABLE));
    }

    @Test
    void neverOffersAnIncompatibleProtocol() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.STANDARD, "1.21.11", "9.9.9", 7, ReleaseChannel.STABLE)));

        assertNull(catalog.bestUpdate(Edition.STANDARD, "1.21.11", "1.0.0", 1, 2, ReleaseChannel.STABLE));
    }

    @Test
    void respectsTheSelectedChannel() {
        ReleaseCatalog catalog = new ReleaseCatalog(List.of(
                manifest(Edition.STANDARD, "1.21.11", "2.0.0", 1, ReleaseChannel.EXPERIMENTAL),
                manifest(Edition.STANDARD, "1.21.11", "1.5.0", 1, ReleaseChannel.STABLE)));

        // A stable-only user must not be offered the newer experimental build.
        ReleaseManifest stableOnly = catalog.bestUpdate(
                Edition.STANDARD, "1.21.11", "1.0.0", 1, 1, ReleaseChannel.STABLE);
        assertEquals("1.5.0", stableOnly.clientVersion());

        // An opted-in user gets the newest across both channels.
        ReleaseManifest both = catalog.bestUpdate(
                Edition.STANDARD, "1.21.11", "1.0.0", 1, 1,
                ReleaseChannel.STABLE, ReleaseChannel.EXPERIMENTAL);
        assertEquals("2.0.0", both.clientVersion());
    }

    @Test
    void versionComparisonIsNumericNotLexical() {
        assertTrue(ReleaseCatalog.compareVersions("1.10.0", "1.9.0") > 0);
        assertTrue(ReleaseCatalog.compareVersions("1.0.0", "1.0.0") == 0);
        assertTrue(ReleaseCatalog.compareVersions("1.0.0", "1.0.1") < 0);
    }
}
