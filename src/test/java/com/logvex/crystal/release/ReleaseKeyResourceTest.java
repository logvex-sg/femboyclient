package com.logvex.crystal.release;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the release key that ships with the client. The updater can only authenticate a release if
 * {@code /logvex-release-key.txt} parses as an Ed25519 public key, so a broken or accidentally
 * deleted key file must fail the build rather than silently disable updates.
 */
class ReleaseKeyResourceTest {

    @Test
    void theShippedReleaseKeyLoads() {
        SignatureVerifier verifier = SignatureVerifier.fromResource();
        assertNotNull(verifier, "the client must ship a loadable release public key");
        assertNotNull(verifier.publicKey());
        // The JDK names an Ed25519 key's algorithm "EdDSA" (SunEC) rather than "Ed25519".
        String algorithm = verifier.publicKey().getAlgorithm();
        assertTrue(algorithm.equalsIgnoreCase("Ed25519") || algorithm.equalsIgnoreCase("EdDSA"),
                "unexpected key algorithm: " + algorithm);
    }

    @Test
    void anAbsentKeyYieldsNoVerifierRatherThanAnError() {
        // A build without a release key cannot verify; that is reported as "unavailable", not as a
        // crash, so the updater degrades to "no verified updates" instead of failing to start.
        assertTrue(SignatureVerifier.fromResource("/does-not-exist-key.txt") == null);
    }
}
