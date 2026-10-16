package com.example.hotspotguardian;
public final class HeartbeatRegistryTest {
    public static void main(String[] args) {
        HeartbeatRegistry r = new HeartbeatRegistry();
        assert r.takeNewAlarms(100000) == 0;
        r.heartbeat("invalid", 0);
        assert r.totalSeen() == 0;
        r.heartbeat("192.168.43.2", 1000);
        assert r.activeCount(2000) == 1;
        assert r.takeNewAlarms(91000) == 0;
        assert r.takeNewAlarms(91001) == 1;
        assert r.takeNewAlarms(99999) == 0;
        r.heartbeat("192.168.43.2", 100000);
        assert r.activeCount(100010) == 1;
        assert r.takeNewAlarms(190001) == 1;
        r.heartbeat("192.168.43.2", 200000);
        r.heartbeat("192.168.43.2", 250000);
        assert r.totalSeen() == 1; // Same laptop after reload
        assert r.takeNewAlarms(290001) == 0;
        r.heartbeat("192.168.43.3", 300000);
        assert r.activeCount(300001) == 2;
        assert r.takeNewAlarms(340001) == 1;
        r.clear();
        assert r.totalSeen() == 0 && r.activeCount(300000) == 0;
        for (int i=0; i<100; i++) r.heartbeat("192.168.43."+(i+2), 300000+i);
        assert r.totalSeen() == 100 && r.activeCount(300100) == 4;
        System.out.println("PASS: timer boundary, alarm, recovery, reload, multiple clients, memory bound");
    }
}
