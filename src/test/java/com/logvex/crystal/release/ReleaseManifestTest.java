package com.logvex.crystal.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Exercises the release manifest contract: strict parsing, the canonical payload, and the Ed25519
 * verification path. The signing here uses a real key pair generated in the test, so the verifier
 * runs its real code rather than a stub.
 */
class ReleaseManifestTest {

    private static final String SHA = "a".repeat(64);

    private static String manifestJson(String edition, String channel, String sha, String signature) {
        return """
                {
                  "schemaVersion": 1,
                  "edition": "%s",
                  "clientVersion": "1.2.0",
                  "minecraftVersion": "1.21.11",
                  "adapterVersion": "modern",
                  "protocolVersion": 1,
                  "loaderRequirement": ">=0.20.1",
                  "javaRequirement": "21",
                  "artifact": "logvex-crystal-1.21.11-1.2.0.jar",
                  "sha256": "%s",
                  "signature": "%s",
                  "releaseChannel": "%s"
                }
                """.formatted(edition, sha, signature, channel);
    }

    @Test
    void parsesAWellFormedManifest() {
        ReleaseManifest manifest = ReleaseManifest.parse(
                manifestJson("standard", "stable", SHA, "sig"));

        assertEquals(Edition.STANDARD, manifest.edition());
        assertEquals(ReleaseChannel.STABLE, manifest.releaseChannel());
        assertEquals("1.21.11", manifest.minecraftVersion());
        assertEquals(1, manifest.protocolVersion());
        assertEquals(SHA, manifest.sha256());
    }

    @Test
    void rejectsAnUnknownSchemaVersion() {
        String json = manifestJson("standard", "stable", SHA, "sig").replace("\"schemaVersion\": 1",
                "\"schemaVersion\": 99");
        ReleaseManifest.ManifestFormatException error = assertThrows(
                ReleaseManifest.ManifestFormatException.class, () -> ReleaseManifest.parse(json));
        assertTrue(error.getMessage().contains("schemaVersion"));
    }

    @Test
    void rejectsAnUnknownEdition() {
        String json = manifestJson("enterprise", "stable", SHA, "sig");
        assertThrows(ReleaseManifest.ManifestFormatException.class, () -> ReleaseManifest.parse(json));
    }

    @Test
    void rejectsAnUnknownChannel() {
        String json = manifestJson("standard", "nightly", SHA, "sig");
        assertThrows(ReleaseManifest.ManifestFormatException.class, () -> ReleaseManifest.parse(json));
    }

    @Test
    void rejectsAMissingRequiredField() {
        String json = manifestJson("standard", "stable", SHA, "sig").replace(
                "\"artifact\": \"logvex-crystal-1.21.11-1.2.0.jar\",", "");
        ReleaseManifest.ManifestFormatException error = assertThrows(
                ReleaseManifest.ManifestFormatException.class, () -> ReleaseManifest.parse(json));
        assertTrue(error.getMessage().contains("artifact"));
    }

    @Test
    void rejectsAMalformedChecksum() {
        String json = manifestJson("standard", "stable", "not-a-sha", "sig");
        assertThrows(ReleaseManifest.ManifestFormatException.class, () -> ReleaseManifest.parse(json));
    }

    @Test
    void rejectsNonJson() {
        assertThrows(ReleaseManifest.ManifestFormatException.class,
                () -> ReleaseManifest.parse("<html>not a manifest</html>"));
    }

    @Test
    void canonicalPayloadExcludesTheSignatureAndCoversIdentity() {
        ReleaseManifest one = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, "sig-a"));
        ReleaseManifest two = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, "sig-b"));

        // Same identity, different signature value: the signed payload must be identical.
        assertEquals(one.canonicalPayload(), two.canonicalPayload());
        assertTrue(one.canonicalPayload().contains("edition=standard"));
        assertTrue(one.canonicalPayload().contains("sha256=" + SHA));
        assertTrue(!one.canonicalPayload().contains("sig-a"));
    }

    @Test
    void acceptsASignatureMadeByTheMatchingKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReleaseManifest unsigned = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, "placeholder"));
        String signature = sign(pair, unsigned.canonicalPayload());

        ReleaseManifest signed = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, signature));
        SignatureVerifier verifier = SignatureVerifier.fromKeyText(publicKeyText(pair));

        assertNotNull(verifier.publicKey());
        assertTrue(verifier.verify(signed));
    }

    @Test
    void rejectsASignatureFromADifferentKey() throws Exception {
        KeyPair signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReleaseManifest unsigned = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, "placeholder"));
        String signature = sign(signer, unsigned.canonicalPayload());

        ReleaseManifest signed = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, signature));
        SignatureVerifier verifier = SignatureVerifier.fromKeyText(publicKeyText(other));

        assertTrue(!verifier.verify(signed));
    }

    @Test
    void rejectsWhenTheSignedContentIsTamperedWith() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReleaseManifest unsigned = ReleaseManifest.parse(manifestJson("standard", "stable", SHA, "placeholder"));
        String signature = sign(pair, unsigned.canonicalPayload());

        // The signature was made for the original sha; change the sha and it must no longer verify.
        ReleaseManifest tampered = ReleaseManifest.parse(
                manifestJson("standard", "stable", "b".repeat(64), signature));
        SignatureVerifier verifier = SignatureVerifier.fromKeyText(publicKeyText(pair));

        assertTrue(!verifier.verify(tampered));
    }

    private static String sign(KeyPair pair, String payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static String publicKeyText(KeyPair pair) {
        return "# test key\n" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n";
    }
}
