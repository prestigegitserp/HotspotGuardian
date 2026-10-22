package com.example.hotspotguardian;

/**
 * Small pure RAM-only schedule. It does not pretend to detect SSID broadcast
 * from the phone, and never actuates on its own: caller must check permission,
 * Keyguard and the verified hotspot Quick Settings tile before any click.
 */
public final class MaintenancePolicy {
    public static final long MIN_INTERVAL_MS = 30L * 60 * 1000;
    public static final long MAX_INTERVAL_MS = 120L * 60 * 1000;
    private long intervalMs;
    private long dueAt = Long.MAX_VALUE;
    private boolean armed;

    public synchronized boolean arm(long now, long requestedIntervalMs) {
        if (requestedIntervalMs < MIN_INTERVAL_MS ||
                requestedIntervalMs > MAX_INTERVAL_MS) return false;
        intervalMs = requestedIntervalMs;
        dueAt = now + intervalMs;
        armed = true;
        return true;
    }
    public synchronized boolean isDue(long now) {
        return armed && now >= dueAt;
    }
    public synchronized boolean hasConsent() { return armed; }
    public synchronized long remainingMs(long now) {
        return armed ? Math.max(0L, dueAt-now) : -1L;
    }
    /** Restart count only after the UI request has been accepted for execution. */
    public synchronized void accepted(long now) {
        if (armed) dueAt = now + intervalMs;
    }
    public synchronized void stop() {
        armed = false;
        intervalMs = 0;
        dueAt = Long.MAX_VALUE;
    }
}
