package com.example.hotspotguardian;

/** Pure, testable, in-memory safety policy: no autonomous recovery storms. */
public final class RecoveryPolicy {
    public static final long COOLDOWN_MS = 15 * 60 * 1000L;
    public static final int MAX_ATTEMPTS = 3;
    private long lastAttempt = -COOLDOWN_MS;
    private int attempts;
    public synchronized boolean allow(long now, boolean hasClient, boolean allLost,
                                       boolean optedIn, boolean serviceReady) {
        if (!optedIn || !hasClient || !allLost || !serviceReady || attempts >= MAX_ATTEMPTS)
            return false;
        if (now - lastAttempt < COOLDOWN_MS) return false;
        attempts++;
        lastAttempt = now;
        return true;
    }
    public synchronized void clear() {
        lastAttempt = -COOLDOWN_MS;
        attempts = 0;
    }
    public synchronized int attempts() { return attempts; }
}
