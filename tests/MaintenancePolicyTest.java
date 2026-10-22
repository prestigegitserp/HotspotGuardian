package com.example.hotspotguardian;

public final class MaintenancePolicyTest {
    public static void main(String[] args) {
        MaintenancePolicy p = new MaintenancePolicy();
        assert !p.hasConsent();
        assert !p.isDue(Long.MAX_VALUE);
        assert !p.arm(0, 1000);
        assert !p.arm(0, 121*60_000L);
        assert !p.hasConsent();
        for (int minutes : new int[]{30,60,120}) {
            p.stop();
            assert p.arm(100_000L, minutes * 60_000L);
            assert p.hasConsent();
            assert !p.isDue(100_000L + minutes * 60_000L - 1);
            assert p.isDue(100_000L + minutes * 60_000L);
            // If phone locked the due moment remains pending until an accepted request.
            assert p.isDue(100_000L + (minutes + 2) * 60_000L);
            p.accepted(100_000L + (minutes + 2) * 60_000L);
            assert !p.isDue(100_000L + (minutes + 2) * 60_000L + 1000L);
            assert p.remainingMs(100_000L + (minutes + 2) * 60_000L) ==
                    minutes * 60_000L;
        }
        p.stop();
        assert !p.isDue(Long.MAX_VALUE);
        assert p.remainingMs(10) == -1;
        System.out.println("PASS: consent, 30/60/120 minutes, locked deferral, accepted execution, stop");
    }
}
