package com.example.hotspotguardian;

/**
 * RAM-only bounded retry policy. Monitoring never stops during cooldown.
 * A single lost-client episode can trigger up to 3 recovery attempts, 45s apart.
 * A real resumed heartbeat resets the episode budget but NOT the cooldown.
 */
public final class RecoveryPolicy {
    public static final long COOLDOWN_MS = 45_000L;
    public static final int MAX_ATTEMPTS = 3;
    private long lastAttempt = -1L;
    private int attempts;

    public synchronized boolean allow(long now, boolean hasClient, boolean allLost,
                                      boolean optedIn, boolean serviceReady) {
        if (!optedIn || !hasClient || !allLost || !serviceReady
                || attempts >= MAX_ATTEMPTS) return false;
        if (lastAttempt >= 0 && (now < lastAttempt || now - lastAttempt < COOLDOWN_MS))
            return false;
        ++attempts;
        lastAttempt = now;
        return true;
    }

    /** Call only on an actually received laptop heartbeat, not a timer event. */
    public synchronized void onHeartbeatRestored() {
        attempts = 0;
    }

    /** Clear the entire state only when monitoring is explicitly restarted. */
    public synchronized void clear() {
        lastAttempt = -1L;
        attempts = 0;
    }
    public synchronized int attempts() { return attempts; }
}
