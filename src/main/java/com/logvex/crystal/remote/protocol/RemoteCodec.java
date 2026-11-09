package com.logvex.crystal.remote.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes {@link RemoteMessage} envelopes, including the optional signature.
 *
 * <p>The wire form is a single compact JSON object so it is easy to log and inspect while
 * debugging, but the parts that are authenticated (everything except {@code sig}) are also
 * reconstructable exactly via {@link RemoteMessage#canonicalForm()} — the signature is computed
 * over that canonical string, not over the JSON, so whitespace and key order can never change
 * what was signed.
 *
 * <p>Decoding is total: any malformed input returns a {@link DecodeException} with a reason
 * rather than throwing something unexpected, because this parses untrusted network data. Size
 * is bounded by {@link #MAX_MESSAGE_BYTES} before parsing, so a hostile peer cannot make the
 * decoder allocate unbounded memory.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class RemoteCodec {

    /** The largest encoded message either side will accept, in bytes. */
    public static final int MAX_MESSAGE_BYTES = 256 * 1024;

    /** The largest decoded payload (the JSON value tree) either side will accept, in bytes. */
    public static final int MAX_PAYLOAD_BYTES = 192 * 1024;

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private RemoteCodec() {
    }

    /** The compact JSON text of a value tree, used for the canonical signed form. */
    public static String compact(JsonObject object) {
        return COMPACT.toJson(object == null ? new JsonObject() : object);
    }

    /** Encodes a message to its wire form, without a signature. */
    public static String encode(RemoteMessage message) {
        return encode(message, "");
    }

    /**
     * Encodes a message to its wire form, attaching {@code signature} (base64) when non-empty.
     * The returned string is compact JSON.
     */
    public static String encode(RemoteMessage message, String signature) {
        JsonObject root = new JsonObject();
        root.addProperty("pv", message.protocolVersion());
        root.addProperty("type", message.type().name());
        root.addProperty("rid", message.requestId());
        root.addProperty("ts", message.timestamp());
        root.addProperty("nonce", message.nonce());
        if (signature != null && !signature.isBlank()) {
            root.addProperty("sig", signature);
        }
        root.add("payload", message.payload());
        return COMPACT.toJson(root);
    }

    /**
     * Decodes a wire message.
     *
     * @throws DecodeException when the input is too large, not JSON, missing a required field,
     *                         or names an unknown message type
     */
    public static RemoteMessage decode(String wire) {
        if (wire == null || wire.isBlank()) {
            throw new DecodeException("empty message");
        }
        if (wire.length() > MAX_MESSAGE_BYTES) {
            throw new DecodeException("message exceeds " + MAX_MESSAGE_BYTES + " bytes");
        }
        JsonObject root;
        try {
            var element = JsonParser.parseString(wire);
            if (element == null || !element.isJsonObject()) {
                throw new DecodeException("message is not a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (DecodeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DecodeException("message is not valid JSON");
        }
        int pv = Json.i32(root, "pv", -1);
        if (pv < 0) {
            throw new DecodeException("missing protocol version");
        }
        String typeName = Json.str(root, "type", "");
        MessageType type = MessageType.parse(typeName);
        if (type == null) {
            throw new DecodeException("unknown message type: " + typeName);
        }
        long ts = Json.i64(root, "ts", 0L);
        String nonce = Json.str(root, "nonce", "");
        String rid = Json.str(root, "rid", "");
        JsonObject payload = Json.obj(root, "payload");
        String payloadText = compact(payload);
        if (payloadText.length() > MAX_PAYLOAD_BYTES) {
            throw new DecodeException("payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        return new RemoteMessage(pv, type, rid, ts, nonce, payload);
    }

    /** The signature attached to a wire message, or empty when it is unsigned. */
    public static String signatureOf(String wire) {
        try {
            JsonObject root = JsonParser.parseString(wire).getAsJsonObject();
            return Json.str(root, "sig", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** UTF-8 bytes of a string, for signing and framing. */
    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** A decode failure with a human-readable reason. */
    public static final class DecodeException extends RuntimeException {
        public DecodeException(String message) {
            super(message);
        }
    }
}
