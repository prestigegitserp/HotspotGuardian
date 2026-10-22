package com.example.hotspotguardian;

public final class RecoveryPolicyTest {
    public static void main(String[] args) {
        RecoveryPolicy r = new RecoveryPolicy();
        assert !r.allow(1000, false, true, true, true); // No observed client
        assert !r.allow(1000, true, false, true, true); // Other laptop is online
        assert !r.allow(1000, true, true, false, true); // No opt-in
        assert !r.allow(1000, true, true, true, false); // Accessibility disabled
        assert r.allow(1000, true, true, true, true); // First loss
        assert !r.allow(1001, true, true, true, true); // Same storm
        assert !r.allow(1000 + RecoveryPolicy.COOLDOWN_MS - 1, true, true, true, true);
        assert r.allow(1000 + RecoveryPolicy.COOLDOWN_MS, true, true, true, true);
        assert r.allow(1000 + 2 * RecoveryPolicy.COOLDOWN_MS, true, true, true, true);
        assert !r.allow(1000 + 3 * RecoveryPolicy.COOLDOWN_MS, true, true, true, true);
        assert r.attempts() == 3;
        r.clear();
        assert r.attempts() == 0;
        assert r.allow(10, true, true, true, true);
        System.out.println("PASS: opt-in, observed heartbeat, all-clients-down, 15-min cooldown, 3-attempt cap");
    }
}
