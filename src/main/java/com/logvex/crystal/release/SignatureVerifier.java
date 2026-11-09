package com.logvex.crystal.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies the Ed25519 signature on a {@link ReleaseManifest} against the release public key
 * distributed with the client.
 *
 * <p>A checksum alone proves only that a file is intact, not who produced it: anyone can compute a
 * SHA-256. Authenticity comes from the signature, which only the holder of the private signing key
 * can produce. The private key is never in the repository; it lives in the release pipeline's
 * protected secrets, and only the public key ships with the client (as
 * {@code /logvex-release-key.txt}).
 *
 * <p>Verification is intentionally offline and cheap. It is a precondition for installation, not a
 * substitute for it: the updater verifies the signature, then the checksum, then the identity fields
 * (edition, Minecraft version, protocol) before anything is written.
 */
public final class SignatureVerifier {

    /** Classpath resource holding the base64 X.509 public key. */
    public static final String KEY_RESOURCE = "/logvex-release-key.txt";

    private final PublicKey publicKey;

    private SignatureVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Loads the release public key from the given classpath resource.
     *
     * @return a verifier, or {@code null} when the resource is absent or unreadable — a build with
     *         no release key simply cannot verify, and the updater must treat that as "no verified
     *         updates available" rather than as a passing check.
     */
    public static SignatureVerifier fromResource(String resource) {
        try (InputStream in = SignatureVerifier.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return fromKeyText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Loads the release public key from the standard resource path. */
    public static SignatureVerifier fromResource() {
        return fromResource(KEY_RESOURCE);
    }

    /**
     * Builds a verifier from the key file's text. The file is a small header line followed by the
     * base64 X.509 SubjectPublicKeyInfo, so a human can tell at a glance which key it is.
     */
    public static SignatureVerifier fromKeyText(String text) throws GeneralSecurityException {
        String base64 = null;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            base64 = trimmed;
            break;
        }
        if (base64 == null) {
            throw new IllegalArgumentException("no public key found");
        }
        byte[] encoded = Base64.getDecoder().decode(base64);
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        return new SignatureVerifier(key);
    }

    /** The key this verifier trusts, for diagnostics. */
    public PublicKey publicKey() {
        return publicKey;
    }

    /**
     * Verifies the manifest's signature over its {@link ReleaseManifest#canonicalPayload()}.
     *
     * @return true only when the signature is present, well-formed, and made by the trusted key over
     *         exactly this manifest's identity fields. Any error (missing signature, bad base64, a
     *         signature for different content) yields false.
     */
    public boolean verify(ReleaseManifest manifest) {
        String signature = manifest.signature();
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(signature.trim());
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(manifest.canonicalPayload().getBytes(StandardCharsets.UTF_8));
            return verifier.verify(raw);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
