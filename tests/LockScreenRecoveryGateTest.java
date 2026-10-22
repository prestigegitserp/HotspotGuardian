package com.example.hotspotguardian;

public final class LockScreenRecoveryGateTest {
    public static void main(String[] args) {
        for (boolean locked : new boolean[]{true,false})
        for (boolean interactive : new boolean[]{true,false}) {
            boolean can = LockScreenRecoveryGate.mayRequestUi(
                    true,true,true,locked,interactive,true);
            boolean defer = LockScreenRecoveryGate.shouldDefer(
                    true,true,true,locked,interactive,true);
            assert can == (!locked && interactive);
            assert defer == (locked || !interactive);
            assert can != defer;
        }
        assert !LockScreenRecoveryGate.shouldDefer(false,true,true,true,false,true);
        assert !LockScreenRecoveryGate.shouldDefer(true,false,true,true,false,true);
        assert !LockScreenRecoveryGate.mayRequestUi(true,true,false,false,true,true);
        assert !LockScreenRecoveryGate.shouldDefer(true,true,true,true,false,false);
        assert !LockScreenRecoveryGate.mayRequestUi(true,true,true,false,true,false);
        System.out.println("PASS: locked + display off never authorizes UI recovery; no consent or quota also blocks");
    }
}
