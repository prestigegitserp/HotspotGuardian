package com.example.hotspotguardian;
import java.util.HashMap;
import java.util.Map;

/** RAM-only bounded heartbeat bookkeeping; never reads, writes or deletes files. */
public final class HeartbeatRegistry {
    private static final int MAX_CLIENTS = 4;
    public static final long TIMEOUT_MS = 18000L;
    private final Map<String, Client> clients = new HashMap<>();
    private int everSeen;

    private static final class Client {
        long lastSeen;
        boolean alarmed;
        Client(long now) { lastSeen = now; }
    }

    public synchronized void heartbeat(String id, long now) {
        if (id == null || !id.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) return;
        Client c = clients.get(id);
        if (c == null) {
            if (clients.size() >= MAX_CLIENTS) {
                String oldest = null;
                long earliest = Long.MAX_VALUE;
                for (Map.Entry<String, Client> e : clients.entrySet()) {
                    if (e.getValue().lastSeen < earliest) {
                        earliest = e.getValue().lastSeen;
                        oldest = e.getKey();
                    }
                }
                if (oldest != null) clients.remove(oldest);
            }
            clients.put(id, new Client(now));
            everSeen++;
        } else {
            c.lastSeen = now;
            c.alarmed = false;
        }
    }

    public synchronized int takeNewAlarms(long now) {
        int count = 0;
        for (Client c : clients.values()) {
            if (!c.alarmed && now >= c.lastSeen && now - c.lastSeen > TIMEOUT_MS) {
                c.alarmed = true;
                count++;
            }
        }
        return count;
    }

    public synchronized int activeCount(long now) {
        int count = 0;
        for (Client c : clients.values()) {
            if (now >= c.lastSeen && now - c.lastSeen <= TIMEOUT_MS) count++;
        }
        return count;
    }
    public synchronized boolean hasAlarmedClient() {
        for (Client c : clients.values()) if (c.alarmed) return true;
        return false;
    }
    public synchronized int totalSeen() { return everSeen; }
    public synchronized void clear() { clients.clear(); everSeen = 0; }
}
