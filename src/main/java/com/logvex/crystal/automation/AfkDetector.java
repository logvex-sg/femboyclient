package com.logvex.crystal.automation;

/**
 * Pure AFK tracking: records the last time real input happened and answers whether the player has
 * been idle longer than a threshold. Kept free of any client state so the timing is unit tested.
 */
public final class AfkDetector {

    private long lastActivityMillis;
    private boolean afk;

    /** Records that the player did something; clears the AFK flag. */
    public void recordActivity(long nowMillis) {
        lastActivityMillis = nowMillis;
        afk = false;
    }

    /** Idle time in milliseconds since the last recorded activity. */
    public long idleMillis(long nowMillis) {
        if (lastActivityMillis == 0) {
            return 0;
        }
        return Math.max(0, nowMillis - lastActivityMillis);
    }

    /**
     * Updates the AFK flag for {@code nowMillis}. Returns true on the tick the player first became
     * AFK, so a caller can fire a notification exactly once.
     */
    public boolean update(long nowMillis, long thresholdMillis) {
        if (lastActivityMillis == 0) {
            lastActivityMillis = nowMillis;
            return false;
        }
        boolean nowAfk = idleMillis(nowMillis) >= Math.max(0, thresholdMillis);
        boolean became = nowAfk && !afk;
        afk = nowAfk;
        return became;
    }

    public boolean isAfk() {
        return afk;
    }

    public long lastActivityMillis() {
        return lastActivityMillis;
    }
}
