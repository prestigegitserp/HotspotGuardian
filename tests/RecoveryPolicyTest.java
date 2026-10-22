package com.example.hotspotguardian;

public final class RecoveryPolicyTest {
    public static void main(String[] args) {
        RecoveryPolicy r = new RecoveryPolicy();
        assert RecoveryPolicy.COOLDOWN_MS == 45_000L;
        assert RecoveryPolicy.MAX_ATTEMPTS == 3;
        assert !r.allow(1000, false, true, true, true);
        assert !r.allow(1000, true, false, true, true);
        assert !r.allow(1000, true, true, false, true);
        assert !r.allow(1000, true, true, true, false);
        assert r.allow(1000, true, true, true, true);
        assert !r.allow(1001, true, true, true, true);
        assert !r.allow(1000+RecoveryPolicy.COOLDOWN_MS-1,true,true,true,true);
        assert r.allow(1000+RecoveryPolicy.COOLDOWN_MS,true,true,true,true);
        assert r.allow(1000+2*RecoveryPolicy.COOLDOWN_MS,true,true,true,true);
        assert !r.allow(1000+3*RecoveryPolicy.COOLDOWN_MS,true,true,true,true);
        assert r.attempts()==3;
        // A genuine laptop reconnect re-arms the episode but MUST retain 45s cooldown.
        r.onHeartbeatRestored();
        assert r.attempts()==0;
        assert !r.allow(1000+2*RecoveryPolicy.COOLDOWN_MS+1,true,true,true,true);
        assert r.allow(1000+3*RecoveryPolicy.COOLDOWN_MS,true,true,true,true);
        // Locked or busy UI rejects the command without spending retry budget.
        r.clear();
        assert !r.attempt(100, true, true, true, true, () -> false);
        assert r.attempts() == 0;
        assert r.attempt(100, true, true, true, true, () -> true);
        assert r.attempts() == 1;
        assert !r.attempt(100 + RecoveryPolicy.COOLDOWN_MS - 1,
                true, true, true, true, () -> true);
        r.clear();
        assert r.attempts()==0;
        assert r.allow(10,true,true,true,true);
        System.out.println("PASS: fast cooldown, retries, deferred/denied operations do not count, real reconnection reset");
    }
}
