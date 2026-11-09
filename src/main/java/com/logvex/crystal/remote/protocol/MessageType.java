package com.logvex.crystal.remote.protocol;

import java.util.Locale;

/**
 * Every message the remote protocol can carry.
 *
 * <p>The set is deliberately small and closed: there is no "run this code" or "run this shell
 * command" type, because the protocol only expresses the documented LOGVEX CRYSTAL operations
 * in {@link com.logvex.crystal.remote.protocol.RemoteAction}. A controller and an agent that
 * disagree on a type reject the message rather than guessing.
 *
 * <p>The handshake is {@code HELLO -> HELLO_ACK -> AUTH -> AUTH_OK}; everything after that is
 * request/response ({@code COMMAND -> COMMAND_RESULT}) or unsolicited state
 * ({@code STATUS -> STATUS_UPDATE}, {@code PING -> PONG}).
 */
public enum MessageType {

    /** Agent -> controller: I would like to register; carries the protocol version and instance id. */
    HELLO,

    /** Controller -> agent: your version is acceptable; carries the negotiated version and a challenge nonce. */
    HELLO_ACK,

    /** Agent -> controller: the proof that it holds the shared secret for its instance id. */
    AUTH,

    /** Controller -> agent: authentication succeeded; carries the granted permissions and capabilities. */
    AUTH_OK,

    /** Either side -> other: authentication failed; carries the reason. The link then closes. */
    AUTH_FAIL,

    /** Agent -> controller: the full instance snapshot (name, versions, world, modules, performance). */
    STATUS,

    /** Controller -> agent: one or more operations to perform. */
    COMMAND,

    /** Agent -> controller: the outcome of a {@link #COMMAND}, keyed by request id. */
    COMMAND_RESULT,

    /** Agent -> controller: a refreshed snapshot, sent on the heartbeat interval. */
    STATUS_UPDATE,

    /** Either side: liveness probe; carries a timestamp. */
    PING,

    /** Either side: the reply to {@link #PING}; echoes the timestamp so RTT can be measured. */
    PONG,

    /** Either side: I am disconnecting on purpose; carries an optional reason. */
    BYE,

    /** Either side: the last message was invalid; carries the reason. Never fatal by itself. */
    ERROR;

    /** The enum constant for a wire name, or null when unknown (so a peer can report it). */
    public static MessageType parse(String wire) {
        if (wire == null) {
            return null;
        }
        try {
            return valueOf(wire.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
