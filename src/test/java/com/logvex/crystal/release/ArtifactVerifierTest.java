package com.logvex.crystal.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises artifact verification and installation against real files on disk. The signing key is a
 * real Ed25519 pair generated per test, so the verifier and installer run their production paths.
 */
class ArtifactVerifierTest {

    private static KeyPair pair;

    private static KeyPair pair() throws Exception {
        if (pair == null) {
            pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }
        return pair;
    }

    private static ReleaseManifest manifestFor(Path artifact, Edition edition, String minecraft,
                                               int protocol, String shaOverride) throws Exception {
        String sha = shaOverride != null ? shaOverride : ArtifactVerifier.sha256(artifact);
        ReleaseManifest unsigned = new ReleaseManifest(edition, "1.0.0", minecraft, "modern",
                protocol, ">=0.20.1", "21", artifact.getFileName().toString(), sha, "placeholder",
                ReleaseChannel.STABLE);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair().getPrivate());
        signer.update(unsigned.canonicalPayload().getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        return new ReleaseManifest(edition, "1.0.0", minecraft, "modern", protocol, ">=0.20.1", "21",
                artifact.getFileName().toString(), sha, signature, ReleaseChannel.STABLE);
    }

    private static ArtifactVerifier verifier() throws Exception {
        String keyText = "# test\n"
                + Base64.getEncoder().encodeToString(pair().getPublic().getEncoded()) + "\n";
        return new ArtifactVerifier(SignatureVerifier.fromKeyText(keyText));
    }

    @Test
    void acceptsAVerifiedArtifact(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(artifact, "pretend jar bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.STANDARD, "1.21.11", 1, null);

        ArtifactVerifier.Result result = verifier().verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 1);

        assertTrue(result.ok(), result.reason());
    }

    @Test
    void rejectsAnArtifactWhoseBytesWereChanged(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(artifact, "original bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.STANDARD, "1.21.11", 1, null);
        Files.writeString(artifact, "tampered bytes");

        ArtifactVerifier.Result result = verifier().verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 1);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("SHA-256"));
    }

    @Test
    void rejectsAnOverseerManifestForAStandardInstallation(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-overseer-1.21.11-1.0.0.jar");
        Files.writeString(artifact, "bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.OVERSEER, "1.21.11", 1, null);

        ArtifactVerifier.Result result = verifier().verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 1);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("edition"));
    }

    @Test
    void rejectsAManifestForADifferentMinecraftVersion(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-1.21.1-1.0.0.jar");
        Files.writeString(artifact, "bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.STANDARD, "1.21.1", 1, null);

        ArtifactVerifier.Result result = verifier().verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 1);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("1.21.1"));
    }

    @Test
    void rejectsAManifestWithAnOutOfRangeProtocol(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(artifact, "bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.STANDARD, "1.21.11", 5, null);

        ArtifactVerifier.Result result = verifier().verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 2);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("protocol"));
    }

    @Test
    void refusesToVerifyWithoutAReleaseKey(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(artifact, "bytes");
        ReleaseManifest manifest = manifestFor(artifact, Edition.STANDARD, "1.21.11", 1, null);

        ArtifactVerifier.Result result = new ArtifactVerifier(null).verify(
                artifact, manifest, Edition.STANDARD, "1.21.11", 1, 1);

        assertFalse(result.ok());
        assertTrue(result.reason().contains("public key"));
    }

    @Test
    void installsAndKeepsAPreviousCopy(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Path current = mods.resolve("logvex-crystal-1.21.11-0.9.0.jar");
        Files.writeString(current, "old jar");

        Path download = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(download, "new jar");
        ReleaseManifest manifest = manifestFor(download, Edition.STANDARD, "1.21.11", 1, null);

        UpdateInstaller installer = new UpdateInstaller(mods);
        UpdateInstaller.Outcome outcome = installer.install(download, current, manifest);

        assertTrue(outcome.installed());
        assertTrue(Files.exists(outcome.installedFile()));
        assertEquals("new jar", Files.readString(outcome.installedFile()));
        assertTrue(Files.exists(outcome.backupFile()), "previous jar must be kept");
        assertEquals("old jar", Files.readString(outcome.backupFile()));
    }

    @Test
    void refusesToInstallAnArtifactThatFailsItsFinalChecksum(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Path current = mods.resolve("logvex-crystal-1.21.11-0.9.0.jar");
        Files.writeString(current, "old jar");

        Path download = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(download, "new jar");
        // Sign a manifest whose sha does not match the download.
        ReleaseManifest manifest = manifestFor(download, Edition.STANDARD, "1.21.11", 1, "c".repeat(64));

        UpdateInstaller installer = new UpdateInstaller(mods);
        UpdateInstaller.Outcome outcome = installer.install(download, current, manifest);

        assertFalse(outcome.installed());
        assertNull(outcome.installedFile());
        assertEquals("old jar", Files.readString(current), "the running jar must be untouched");
    }

    @Test
    void rollsBackToThePreviousJar(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Path current = mods.resolve("logvex-crystal-1.21.11-0.9.0.jar");
        Files.writeString(current, "old jar");

        Path download = dir.resolve("logvex-crystal-1.21.11-1.0.0.jar");
        Files.writeString(download, "new jar");
        ReleaseManifest manifest = manifestFor(download, Edition.STANDARD, "1.21.11", 1, null);

        UpdateInstaller installer = new UpdateInstaller(mods);
        installer.install(download, current, manifest);
        assertTrue(installer.rollback(current));
        assertEquals("old jar", Files.readString(current));
    }

    @Test
    void rollbackReportsFalseWhenThereIsNoBackup(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        UpdateInstaller installer = new UpdateInstaller(mods);
        assertFalse(installer.rollback(mods.resolve("logvex-crystal-1.21.11-1.0.0.jar")));
    }
}
