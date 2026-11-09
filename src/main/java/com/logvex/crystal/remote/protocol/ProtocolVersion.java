package com.logvex.crystal.remote.protocol;

/**
 * The wire-protocol version this build speaks.
 *
 * <p>The protocol is versioned independently of the Minecraft and client versions, because a
 * controller and an agent are separate processes that are upgraded at different times. A
 * connection negotiates the highest version both ends understand during the handshake; a peer
 * whose version is outside {@link #MIN} .. {@link #CURRENT} is rejected with a clear reason
 * rather than being allowed to send messages the other end cannot parse.
 *
 * <p>Bump {@link #CURRENT} when a message's shape or meaning changes; widen {@link #MIN} only
 * when an older peer is still fully supported.
 */
public final class ProtocolVersion {

    /** The newest protocol this build speaks. */
    public static final int CURRENT = 1;

    /** The oldest protocol this build still understands. */
    public static final int MIN = 1;

    /** A human label for logs and the dashboard, e.g. {@code "1"}. */
    public static final String LABEL = "1";

    private ProtocolVersion() {
    }

    /** True when {@code version} is a protocol this build can speak. */
    public static boolean supported(int version) {
        return version >= MIN && version <= CURRENT;
    }

    /** The version to use with a peer that offers {@code peerVersion}: the lower of the two. */
    public static int negotiate(int peerVersion) {
        return Math.min(peerVersion, CURRENT);
    }

    /** Why a peer's version is unusable, or empty when it is fine. */
    public static String rejection(int peerVersion) {
        if (peerVersion < MIN) {
            return "protocol " + peerVersion + " is older than the minimum supported " + MIN;
        }
        if (peerVersion > CURRENT) {
            return "protocol " + peerVersion + " is newer than this build supports (" + CURRENT + ")";
        }
        return "";
    }
}
