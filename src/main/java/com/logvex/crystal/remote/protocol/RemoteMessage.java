package com.logvex.crystal.remote.protocol;

import com.google.gson.JsonObject;
import java.util.Objects;

/**
 * One protocol message: a typed, versioned envelope around a JSON payload.
 *
 * <p>Every field except {@code payload} is part of the message's identity and is covered by the
 * authentication signature (see {@link #canonicalForm()}). {@code requestId} correlates a
 * {@link MessageType#COMMAND} with its {@link MessageType#COMMAND_RESULT} and any {@link
 * MessageType#ERROR}; {@code nonce} plus {@code timestamp} give the receiver what it needs to
 * reject a replayed message; {@code protocolVersion} lets a peer check compatibility before it
 * trusts anything else.
 *
 * <p>It is a plain value type with no Minecraft dependency, so it can be encoded, signed and
 * tested without a game.
 */
public final class RemoteMessage {

    private final int protocolVersion;
    private final MessageType type;
    private final String requestId;
    private final long timestamp;
    private final String nonce;
    private final JsonObject payload;

    public RemoteMessage(int protocolVersion, MessageType type, String requestId,
                         long timestamp, String nonce, JsonObject payload) {
        this.protocolVersion = protocolVersion;
        this.type = Objects.requireNonNull(type, "type");
        this.requestId = requestId == null ? "" : requestId;
        this.timestamp = timestamp;
        this.nonce = nonce == null ? "" : nonce;
        this.payload = payload == null ? new JsonObject() : payload;
    }

    /** A convenience builder for an unsigned request/response with a fresh timestamp. */
    public static RemoteMessage of(MessageType type, String requestId, JsonObject payload) {
        return new RemoteMessage(ProtocolVersion.CURRENT, type, requestId,
                System.currentTimeMillis(), Nonces.fresh(), payload);
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public MessageType type() {
        return type;
    }

    public String requestId() {
        return requestId;
    }

    public long timestamp() {
        return timestamp;
    }

    public String nonce() {
        return nonce;
    }

    public JsonObject payload() {
        return payload;
    }

    /**
     * The exact byte string the signature is computed over. Field order and separators are
     * fixed, and the payload is the compact (non-pretty) JSON so both ends hash the same bytes
     * regardless of how their serialiser formats whitespace.
     */
    public String canonicalForm() {
        return protocolVersion + "\n"
                + type.name() + "\n"
                + requestId + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + RemoteCodec.compact(payload);
    }
}
