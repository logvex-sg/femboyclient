package com.logvex.crystal.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies a downloaded artifact against a manifest, in the order that matters:
 *
 * <ol>
 *   <li>the manifest's signature (authenticity — only the release key holder could have produced
 *       it);</li>
 *   <li>the file's SHA-256 (integrity — the bytes match what was signed);</li>
 *   <li>the artifact name and identity fields (the right edition, Minecraft version and protocol —
 *       so an Overseer jar can never be installed over Standard, or a 1.21.1 jar over a 1.21.11
 *       install).</li>
 * </ol>
 *
 * <p>Every check must pass. A failure is reported with a specific reason and the caller must refuse
 * to install. Nothing here writes to the filesystem or the network, so it is safe to call before any
 * user confirmation and easy to unit-test.
 */
public final class ArtifactVerifier {

    private final SignatureVerifier signatures;

    public ArtifactVerifier(SignatureVerifier signatures) {
        this.signatures = signatures;
    }

    /** The outcome of verifying one artifact. */
    public record Result(boolean ok, String reason) {
        static Result pass() {
            return new Result(true, "verified");
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * Runs all checks for {@code artifact} against {@code manifest}.
     *
     * @param expectedEdition         the edition this installation is (must match the manifest);
     * @param expectedMinecraft       the Minecraft version this installation runs (must match);
     * @param supportedProtocolMin    the oldest agent protocol this build can talk to;
     * @param supportedProtocolMax    the newest agent protocol this build can talk to.
     */
    public Result verify(Path artifact, ReleaseManifest manifest, Edition expectedEdition,
                         String expectedMinecraft, int supportedProtocolMin, int supportedProtocolMax) {
        if (signatures == null) {
            return Result.fail("no release public key is available, so authenticity cannot be checked");
        }
        if (!signatures.verify(manifest)) {
            return Result.fail("manifest signature does not verify against the release key");
        }
        if (manifest.edition() != expectedEdition) {
            return Result.fail("manifest edition " + manifest.edition().wire()
                    + " does not match this installation (" + expectedEdition.wire() + ")");
        }
        if (!manifest.minecraftVersion().equals(expectedMinecraft)) {
            return Result.fail("manifest is for Minecraft " + manifest.minecraftVersion()
                    + ", this installation runs " + expectedMinecraft);
        }
        if (manifest.protocolVersion() < supportedProtocolMin
                || manifest.protocolVersion() > supportedProtocolMax) {
            return Result.fail("manifest protocol " + manifest.protocolVersion()
                    + " is outside this build's supported range "
                    + supportedProtocolMin + ".." + supportedProtocolMax);
        }
        if (!artifact.getFileName().toString().equals(manifest.artifact())) {
            return Result.fail("artifact file name " + artifact.getFileName()
                    + " does not match the manifest (" + manifest.artifact() + ")");
        }
        if (!Files.isRegularFile(artifact)) {
            return Result.fail("artifact is missing: " + artifact);
        }

        String actual;
        try {
            actual = sha256(artifact);
        } catch (IOException e) {
            return Result.fail("could not read artifact: " + e.getMessage());
        }
        if (!actual.equalsIgnoreCase(manifest.sha256())) {
            return Result.fail("artifact SHA-256 " + actual + " does not match the manifest ("
                    + manifest.sha256() + ")");
        }
        return Result.pass();
    }

    /** The lower-case hex SHA-256 of a file, streamed so a large jar never has to fit in memory. */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
