package com.example.hotspotguardian;

public final class HeartbeatRegistryTest {
    public static void main(String[] args) {
        HeartbeatRegistry r = new HeartbeatRegistry();
        final long t = HeartbeatRegistry.TIMEOUT_MS;
        assert t == 18000L;
        assert r.takeNewAlarms(t + 1) == 0;
        r.heartbeat("invalid", 0);
        assert r.totalSeen() == 0;
        r.heartbeat("192.168.43.2", 1000L);
        assert r.activeCount(1000L + t) == 1;
        assert r.takeNewAlarms(1000L + t) == 0;
        assert r.takeNewAlarms(1001L + t) == 1;
        assert r.takeNewAlarms(1002L + t) == 0;
        assert r.activeCount(1002L + t) == 0;
        r.heartbeat("192.168.43.2", 2000L + t);
        assert !r.hasAlarmedClient();
        assert r.activeCount(2000L + t) == 1;
        assert r.totalSeen() == 1; // Reload uses same laptop IP
        r.heartbeat("192.168.43.3", 3000L + t);
        assert r.activeCount(3000L + t) == 2;
        assert r.takeNewAlarms(3001L + 2*t) == 2;
        r.clear();
        assert r.totalSeen() == 0;
        for (int i=0; i<100; i++)
            r.heartbeat("192.168.43."+(i+2), 100000L+i);
        assert r.totalSeen() == 100;
        assert r.activeCount(100101L) == 4;
        System.out.println("PASS: 18s timeout, no repeats, reconnect, multiple clients, capped RAM");
    }
}
