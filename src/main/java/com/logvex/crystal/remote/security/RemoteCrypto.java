package com.logvex.crystal.remote.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The cryptographic primitives the remote protocol authenticates with.
 *
 * <p>Authentication is an HMAC-SHA256 over the message's canonical form (see
 * {@link com.logvex.crystal.remote.protocol.RemoteMessage#canonicalForm()}), keyed by the shared
 * secret established during pairing. HMAC is the right tool here: the controller and the agent
 * share a symmetric secret, so neither needs to exchange a public key, and the tag proves both
 * <em>who</em> sent the message and that it was not altered in transit. TLS (when enabled)
 * protects confidentiality; the HMAC protects authenticity and integrity even on a plain local
 * socket, and the two are complementary.
 *
 * <p>Comparisons use {@link java.security.MessageDigest#isEqual}, which is constant time, so a
 * timing side channel cannot leak how much of a forged tag matched.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class RemoteCrypto {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private RemoteCrypto() {
    }

    /** A fresh 160-bit secret, base64url-encoded, for a new pairing. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        com.logvex.crystal.remote.protocol.Nonces.fill(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * The HMAC-SHA256 tag over {@code message}, keyed by the base64url {@code secret}.
     * Returns a base64url tag, or an empty string when the secret is unusable.
     */
    public static String sign(String secret, String message) {
        byte[] key = decode(secret);
        if (key.length == 0 || message == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Verifies a tag in constant time. An empty expected or provided tag never verifies, so a
     * message with no signature can never be accepted as if it had one.
     */
    public static boolean verify(String secret, String message, String providedTag) {
        String expected = sign(secret, message);
        if (expected.isEmpty() || providedTag == null || providedTag.isEmpty()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedTag.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a base64url secret; an invalid secret yields an empty array (never throws). */
    public static byte[] decode(String secret) {
        if (secret == null || secret.isBlank()) {
            return new byte[0];
        }
        try {
            return DECODER.decode(secret);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    /** Encodes raw bytes as a base64url string. */
    public static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    /**
     * The human-typeable pairing code for a secret: the same 160-bit secret rendered in base32,
     * grouped in fours.
     *
     * <p>This is a direct encoding, not a one-way derivation, because the code <em>is</em> the
     * shared secret during pairing — the controller shows it, the user types it on the agent, and
     * both ends end up holding the same key. Encoding the raw secret (rather than an HMAC of it)
     * means the agent does not have to know the instance id or trust anything else the controller
     * sent; the typed code alone is sufficient to authenticate. The code is therefore as sensitive
     * as the secret and is shown on screen only while pairing.
     */
    public static String pairingCode(String secret) {
        return Base32.group(Base32.encode(decode(secret)));
    }

    /** The canonical secret for a pairing code the user typed, or empty when it is invalid. */
    public static String secretFromPairingCode(String code) {
        byte[] raw = Base32.decode(code);
        if (raw.length < 16) {
            return "";
        }
        return ENCODER.encodeToString(raw);
    }
}
