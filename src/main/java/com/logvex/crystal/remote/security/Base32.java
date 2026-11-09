package com.logvex.crystal.remote.security;

/**
 * RFC 4648 base32 (no padding), used to render a pairing secret as a short, unambiguous code the
 * user can read off one machine and type into another.
 *
 * <p>Base32 is chosen over base64 for the human step on purpose: it uses only {@code A-Z} and
 * {@code 2-7}, so it is case-insensitive to type, contains no characters that look like
 * punctuation, and survives being written on paper or read aloud. The alphabet is the standard
 * one, so any base32 tool can decode it if the user needs to move a secret by other means.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    /** Encodes bytes as unpadded base32. */
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    /**
     * Decodes unpadded base32, ignoring spaces, dashes and case.
     *
     * @return the decoded bytes, or an empty array when the input is not valid base32
     */
    public static byte[] decode(String text) {
        if (text == null) {
            return new byte[0];
        }
        String cleaned = text.replaceAll("[^A-Za-z2-7]", "").toUpperCase(java.util.Locale.ROOT);
        if (cleaned.isEmpty()) {
            return new byte[0];
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int value = ALPHABET.indexOf(cleaned.charAt(i));
            if (value < 0) {
                return new byte[0];
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** Groups a code in fours for display, e.g. {@code ABCD-EFGH-IJKL}. */
    public static String group(String code) {
        if (code == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                out.append('-');
            }
            out.append(code.charAt(i));
        }
        return out.toString();
    }
}
