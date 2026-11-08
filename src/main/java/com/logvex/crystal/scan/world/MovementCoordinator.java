package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arbitrates who may move the player.
 *
 * <p>Autonomous exploration drives the player through Baritone and, when authorised, through
 * flight. Several other features in the client can also want to move the player, and two of them
 * acting at once is how a client walks a player into lava. This coordinator gives movement a
 * single owner: a component asks to {@link #claim} movement for a named purpose, and every other
 * mover must respect the claim. The world scanner claims movement for the duration of a run and
 * releases it on stop, pause, abort or shutdown.
 *
 * <p>It is deliberately a small, synchronous, Minecraft-free bookkeeper so the rule is testable
 * without a game. It does not itself move anything: it only answers "may I?".
 */
public final class MovementCoordinator {

    /** A held movement claim. */
    public record Claim(String owner, long sinceMillis, String purpose) {
    }

    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final List<String> log = new ArrayList<>();

    /**
     * Requests movement for {@code owner}. A claim succeeds when nothing else holds one; a second
     * claim by the same owner refreshes it.
     *
     * @return the holder's name when the request is refused, or {@code null} when it succeeded
     */
    public String claim(String owner, String purpose, long nowMillis) {
        if (owner == null || owner.isBlank()) {
            return "invalid owner";
        }
        Claim existing = claims.get(owner);
        if (existing != null) {
            claims.put(owner, new Claim(owner, nowMillis, purpose));
            return null;
        }
        for (Claim claim : claims.values()) {
            if (!claim.owner().equals(owner)) {
                return claim.owner();
            }
        }
        claims.put(owner, new Claim(owner, nowMillis, purpose));
        record("claim " + owner + " (" + purpose + ")");
        return null;
    }

    /** True when {@code owner} holds the movement claim. */
    public boolean holds(String owner) {
        return claims.containsKey(owner);
    }

    /** True when nothing holds movement. */
    public boolean free() {
        return claims.isEmpty();
    }

    /** The current holder's name, or {@code ""} when movement is free. */
    public String holder() {
        return claims.isEmpty() ? "" : claims.keySet().iterator().next();
    }

    /** Releases {@code owner}'s claim. Returns true when a claim was released. */
    public boolean release(String owner) {
        if (claims.remove(owner) != null) {
            record("release " + owner);
            return true;
        }
        return false;
    }

    /** Releases every claim (used on disconnect/shutdown). */
    public void releaseAll() {
        if (!claims.isEmpty()) {
            claims.clear();
            record("release all");
        }
    }

    /** A read-only view of the arbitration log, for the dashboard's diagnostics. */
    public List<String> log() {
        return List.copyOf(log);
    }

    private void record(String event) {
        log.add(event);
        while (log.size() > 64) {
            log.remove(0);
        }
    }
}
