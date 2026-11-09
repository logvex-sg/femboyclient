package com.logvex.crystal.remote.protocol;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically strong, URL-safe random identifiers: nonces for replay protection and
 * request ids for command correlation.
 *
 * <p>Uses {@link SecureRandom}, never {@code Random}, because a predictable nonce would let an
 * attacker replay a captured message. The output is 128 bits of randomness encoded as URL-safe
 * base64 without padding, so it is safe to put in a header or a JSON string.
 *
 * <p>Minecraft-independent.
 */
public final class Nonces {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Nonces() {
    }

    /** A fresh 128-bit nonce. */
    public static String fresh() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** A fresh request id, prefixed so a log line makes it obvious where it came from. */
    public static String requestId() {
        return "cmd-" + fresh();
    }

    /** Fills {@code buffer} with cryptographically strong random bytes. */
    public static void fill(byte[] buffer) {
        RANDOM.nextBytes(buffer);
    }
}
