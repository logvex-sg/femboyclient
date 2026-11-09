package com.logvex.crystal.remote.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live authenticated sessions and expires them after an idle timeout.
 *
 * <p>A session begins when an agent finishes the {@code AUTH} handshake and ends when the link
 * closes, the user disconnects it, or it has been silent for longer than {@link #idleTimeoutMillis()}.
 * Expiry matters for a controller that keeps a socket open for a long time: a session whose agent
 * vanished (machine slept, network dropped) must not linger as "connected" forever, and a token
 * that was revoked must stop working the moment its session is next checked.
 *
 * <p>Thread safe: sessions are touched by the controller's accept thread, each connection's reader
 * thread and the UI thread. Minecraft-independent and unit testable.
 */
public final class SessionManager {

    /** One live session: which instance, when it started, when it was last heard from. */
    public static final class Session {
        private final String sessionId;
        private final String instanceId;
        private final long startedAtMillis;
        private volatile long lastActivityMillis;

        Session(String sessionId, String instanceId, long startedAtMillis) {
            this.sessionId = sessionId;
            this.instanceId = instanceId;
            this.startedAtMillis = startedAtMillis;
            this.lastActivityMillis = startedAtMillis;
        }

        public String sessionId() {
            return sessionId;
        }

        public String instanceId() {
            return instanceId;
        }

        public long startedAtMillis() {
            return startedAtMillis;
        }

        public long lastActivityMillis() {
            return lastActivityMillis;
        }

        void touch(long nowMillis) {
            this.lastActivityMillis = nowMillis;
        }
    }

    private final long idleTimeoutMillis;
    private final Map<String, Session> bySessionId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionByInstance = new ConcurrentHashMap<>();

    public SessionManager(long idleTimeoutMillis) {
        this.idleTimeoutMillis = Math.max(5_000L, idleTimeoutMillis);
    }

    /** A session manager with a 60-second idle timeout. */
    public static SessionManager standard() {
        return new SessionManager(60_000L);
    }

    public long idleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    /** Opens a session for an instance, replacing any previous session for the same instance. */
    public Session open(String instanceId) {
        String sessionId = com.logvex.crystal.remote.protocol.Nonces.fresh();
        Session session = new Session(sessionId, instanceId, System.currentTimeMillis());
        bySessionId.put(sessionId, session);
        sessionByInstance.put(instanceId, sessionId);
        return session;
    }

    /** Records activity, so an active session does not expire. Returns false when unknown. */
    public boolean touch(String sessionId) {
        Session session = bySessionId.get(sessionId);
        if (session == null) {
            return false;
        }
        session.touch(System.currentTimeMillis());
        return true;
    }

    public Session get(String sessionId) {
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    public Session forInstance(String instanceId) {
        String sessionId = sessionByInstance.get(instanceId);
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    public boolean isLive(String sessionId) {
        return get(sessionId) != null;
    }

    /** Closes a session, by session id or by instance id. */
    public void close(String sessionId) {
        Session session = bySessionId.remove(sessionId);
        if (session != null) {
            sessionByInstance.remove(session.instanceId(), sessionId);
        }
    }

    /** Closes every session for an instance (used on disconnect and revocation). */
    public void closeForInstance(String instanceId) {
        String sessionId = sessionByInstance.remove(instanceId);
        if (sessionId != null) {
            bySessionId.remove(sessionId);
        }
    }

    /**
     * Expires idle sessions.
     *
     * @return the instance ids whose sessions were expired, so the caller can log/audit them
     */
    public java.util.List<String> expireIdle(long nowMillis) {
        java.util.List<String> expired = new java.util.ArrayList<>();
        for (Session session : bySessionId.values()) {
            if (nowMillis - session.lastActivityMillis() > idleTimeoutMillis) {
                close(session.sessionId());
                expired.add(session.instanceId());
            }
        }
        return expired;
    }

    public int size() {
        return bySessionId.size();
    }
}
