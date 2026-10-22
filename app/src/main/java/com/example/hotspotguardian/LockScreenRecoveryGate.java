package com.example.hotspotguardian;

/**
 * Pure, testable state gate: a secure Keyguard is NEVER bypassed.
 * Actual Android UI recovery requires an unlocked, interactive device.
 */
public final class LockScreenRecoveryGate {
    private LockScreenRecoveryGate() {}

    public static boolean shouldDefer(boolean outage, boolean consent,
                                      boolean accessibilityReady,
                                      boolean locked, boolean interactive,
                                      boolean attemptsRemaining) {
        return outage && consent && accessibilityReady && attemptsRemaining
                && (locked || !interactive);
    }

    public static boolean mayRequestUi(boolean outage, boolean consent,
                                       boolean accessibilityReady,
                                       boolean locked, boolean interactive,
                                       boolean attemptsRemaining) {
        return outage && consent && accessibilityReady && attemptsRemaining
                && !locked && interactive;
    }
}
