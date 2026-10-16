package com.example.hotspotguardian;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

/** Best-effort interface discovery; does not alter Wi-Fi or tethering state. */
final class HotspotAddress {
    static InetAddress find() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return null;
            InetAddress fallback = null;
            while (all.hasMoreElements()) {
                NetworkInterface iface = all.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                String name = iface.getName().toLowerCase(Locale.ROOT);
                if (!(name.startsWith("wlan") || name.startsWith("swlan")
                        || name.startsWith("ap") || name.startsWith("softap")
                        || name.startsWith("wlap"))) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (!(ip instanceof Inet4Address) || !ip.isSiteLocalAddress()) continue;
                    if (!name.equals("wlan0") && !name.equals("wlan1")) return ip;
                    if (fallback == null) fallback = ip;
                }
            }
            return fallback;
        } catch (SocketException | SecurityException ignored) {
            return null;
        }
    }
}
