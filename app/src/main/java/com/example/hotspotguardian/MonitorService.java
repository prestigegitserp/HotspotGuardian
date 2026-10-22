package com.example.hotspotguardian;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Explicit, read-only, RAM-only local monitor. Never edits connectivity or files. */
public final class MonitorService extends Service {
    private static final int PORT = 8765;
    private static final int FOREGROUND_ID = 101;
    private static final int ALERT_ID = 102;
    private static final String CHANNEL_RUNNING = "guardian_monitor_v1";
    private static final String CHANNEL_ALERTS = "guardian_alert_v1";
    private static final String ACTION_STOP = "com.example.hotspotguardian.STOP";

    public static volatile String currentUrl = "";
    public static volatile String status = "پایش خاموش است.";
    public static volatile boolean isRunning = false;
    public static volatile int activeClients = 0;

    public static volatile boolean keepAwakeSelected = false;
    public static volatile boolean radioGuardSelected = false;
    public static volatile boolean autoRecoverySelected = false;
    public static volatile boolean deferredRecovery = false;
    private final RecoveryPolicy recoveryPolicy = new RecoveryPolicy();
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock radioWakeLock;
    private final HeartbeatRegistry registry = new HeartbeatRegistry();
    private ExecutorService serverExecutor;
    private ScheduledExecutorService checkExecutor;
    private volatile ServerSocket serverSocket;
    private volatile boolean keepRunning;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_RUNNING, "پایش هات‌اسپات", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ALERTS, "هشدار قطعی", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (keepRunning) {
            applyPowerGuards();
            return START_NOT_STICKY;
        }
        Notification base = makeNotification(CHANNEL_RUNNING, "پایش فعال",
                "منتظر دریافت پیام از لپ‌تاپ", true);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_ID, base, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_ID, base);
        }
        keepRunning = true;
        isRunning = true;
        registry.clear();
        recoveryPolicy.clear();
        deferredRecovery = false;
        applyPowerGuards();
        currentUrl = "";
        status = "در حال یافتن نشانی محلی هات‌اسپات…";
        serverExecutor = Executors.newSingleThreadExecutor();
        checkExecutor = Executors.newSingleThreadScheduledExecutor();
        serverExecutor.execute(this::serveLoop);
        checkExecutor.scheduleWithFixedDelay(this::checkHeartbeats, 2, 2, TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }

    /**
     * Optional RAM-only CPU and Wi-Fi locks. Neither can force SoftAP to remain
     * discoverable or bypass system Doze/tethering failures. No auto screen unlock.
     */
    private void applyPowerGuards() {
        try {
            if (keepAwakeSelected && keepRunning) {
                if (cpuWakeLock == null) {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "HotspotGuardian:UserEnabledCpuGuard");
                    cpuWakeLock.setReferenceCounted(false);
                }
                if (!cpuWakeLock.isHeld()) cpuWakeLock.acquire();
            } else if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
                cpuWakeLock.release();
            }

            if (radioGuardSelected && keepRunning) {
                if (radioWakeLock == null) {
                    WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    if (wifi != null) {
                        radioWakeLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                                "HotspotGuardian:OptionalWifiGuard");
                        radioWakeLock.setReferenceCounted(false);
                    }
                }
                if (radioWakeLock != null && !radioWakeLock.isHeld()) radioWakeLock.acquire();
            } else if (radioWakeLock != null && radioWakeLock.isHeld()) {
                radioWakeLock.release();
            }
        } catch (RuntimeException ignored) {
            status = "فعال‌سازی محافظ انرژی توسط اندروید رد شد؛ پایش ادامه دارد.";
        }
    }

    private Notification makeNotification(String channel, String title,
            String message, boolean withStop) {
        Intent screen = new Intent(this, MainActivity.class);
        screen.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 0, screen,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(open)
                .setOngoing(withStop)
                .setOnlyAlertOnce(withStop)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (withStop) {
            Intent stop = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
            PendingIntent pi = PendingIntent.getService(this, 1, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف پایش", pi);
        }
        return builder.build();
    }

    private void checkHeartbeats() {
        if (!keepRunning) return;
        long now = SystemClock.elapsedRealtime();
        activeClients = registry.activeCount(now);
        int newAlarms = registry.takeNewAlarms(now);
        if (newAlarms > 0) {
            status = "هشدار: پیام لپ‌تاپ بیش از ۱۸ ثانیه نرسیده (یا مرورگر خوابیده).";
            getSystemService(NotificationManager.class).notify(ALERT_ID,
                makeNotification(CHANNEL_ALERTS, "احتمال قطع ارتباط هات‌اسپات",
                    "پیام پایش بیش از ۱۸ ثانیه نرسیده؛ اتصال را بررسی کن.", false));
        }
        // Continuous monitoring also runs with the screen off when Android permits.
        // A secure lock is never bypassed. Queue a recovery in RAM until the user unlocks.
        boolean outage = registry.hasAlarmedClient() && activeClients == 0
                && registry.totalSeen() > 0;
        if (outage && autoRecoverySelected && RecoveryAccessibilityService.isReady()) {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            boolean lockedOrAsleep = keyguard == null || keyguard.isKeyguardLocked()
                    || power == null || !power.isInteractive();
            if (lockedOrAsleep && recoveryPolicy.attempts() < RecoveryPolicy.MAX_ATTEMPTS) {
                if (!deferredRecovery) {
                    getSystemService(NotificationManager.class).notify(ALERT_ID,
                            makeNotification(CHANNEL_ALERTS, "قطع ارتباط؛ بازیابی منتظر بازشدن قفل",
                                    "صفحه را باز کن؛ اگر قطعی ادامه داشت، برنامه برای بازیابی تلاش می‌کند.", false));
                }
                deferredRecovery = true;
                status = "پایش فعال است؛ بازیابی در RAM منتظر بازکردن قفل گوشی است.";
            } else if (!lockedOrAsleep) {
                if (recoveryPolicy.attempt(now, true, true, true,
                        RecoveryAccessibilityService.canRecoverNow(),
                        RecoveryAccessibilityService::request)) {
                    deferredRecovery = false;
                    status = "بازیابی پذیرفته شد؛ تلاش " + recoveryPolicy.attempts()
                            + " از ۳. نتیجه اتصال را بررسی کن.";
                }
            } else {
                deferredRecovery = false;
                status = "سقف ۳ تلاش این قطعی تمام شد؛ پایش هنوز فعال است.";
            }
        } else {
            deferredRecovery = false;
        }
        if (!outage) {
            deferredRecovery = false;
            getSystemService(NotificationManager.class).cancel(ALERT_ID);
        }
    }

    private void serveLoop() {
        while (keepRunning && !Thread.currentThread().isInterrupted()) {
            InetAddress local = HotspotAddress.find();
            if (local == null) {
                currentUrl = "";
                status = "نشانی Wi-Fi/Hotspot پیدا نشد؛ هات‌اسپات را روشن کن.";
                nap();
                continue;
            }
            try (ServerSocket server = new ServerSocket(PORT, 4, local)) {
                serverSocket = server;
                server.setSoTimeout(3000);
                currentUrl = "http://" + local.getHostAddress() + ":" + PORT + "/";
                status = "پایش آماده است؛ نشانی را در لپ‌تاپ باز کن.";
                long nextCheck = SystemClock.elapsedRealtime() + 15000L;
                while (keepRunning && !Thread.currentThread().isInterrupted()) {
                    if (SystemClock.elapsedRealtime() >= nextCheck) {
                        InetAddress latest = HotspotAddress.find();
                        if (latest == null || !latest.equals(local)) break;
                        nextCheck = SystemClock.elapsedRealtime() + 15000L;
                    }
                    try (Socket client = server.accept()) {
                        client.setSoTimeout(1000);
                        handle(client);
                    } catch (SocketTimeoutException ignored) {
                        // Normal idle state; no disk logging.
                    } catch (IOException | RuntimeException ignored) {
                        // Ignore malformed clients; no logging or system mutation.
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                status = "درگاه محلی قابل استفاده نیست؛ تلاش مجدد انجام می‌شود.";
                nap();
            } finally {
                serverSocket = null;
                currentUrl = "";
            }
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream input = client.getInputStream();
        byte[] line = new byte[256];
        int length = 0;
        int ch;
        while (length < line.length && (ch = input.read()) != -1 && ch != '\n') {
            if (ch != '\r') line[length++] = (byte) ch;
        }
        if (length == line.length) {
            respond(client, 400, "text/plain; charset=utf-8", "Bad request");
            return;
        }
        int headerBytes = 0;
        boolean complete = false;
        while (headerBytes < 4096) {
            int count = 0;
            int next = -1;
            while (count < 1024 && (next = input.read()) != -1 && next != '\n') {
                headerBytes++;
                if (next != '\r') count++;
            }
            headerBytes++;
            if (next == -1 || count == 1024) break;
            if (count == 0) {
                complete = true;
                break;
            }
        }
        if (!complete) {
            respond(client, 400, "text/plain; charset=utf-8", "Bad headers");
            return;
        }
        String request = new String(line, 0, length, StandardCharsets.US_ASCII);
        if (request.equals("GET / HTTP/1.1") || request.equals("GET / HTTP/1.0")) {
            respond(client, 200, "text/html; charset=utf-8", CLIENT_PAGE);
        } else if (request.equals("GET /ping HTTP/1.1") ||
                   request.equals("GET /ping HTTP/1.0")) {
            long now = SystemClock.elapsedRealtime();
            boolean wasOffline = registry.hasAlarmedClient()
                    && registry.activeCount(now) == 0;
            registry.heartbeat(client.getInetAddress().getHostAddress(), now);
            if (wasOffline) {
                recoveryPolicy.onHeartbeatRestored();
                deferredRecovery = false;
            }
            activeClients = registry.activeCount(now);
            if (!registry.hasAlarmedClient()) {
                status = "ارتباط برقرار است؛ دستگاه‌های فعال: " + activeClients;
                getSystemService(NotificationManager.class).cancel(ALERT_ID);
            }
            respond(client, 200, "text/plain; charset=utf-8", "OK");
        } else {
            respond(client, 404, "text/plain; charset=utf-8", "Not found");
        }
    }

    private void respond(Socket client, int statusCode, String mime, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String label = statusCode == 200 ? "OK"
                : statusCode == 400 ? "Bad Request" : "Not Found";
        String headers = "HTTP/1.1 " + statusCode + " " + label + "\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Cache-Control: no-store, no-cache, must-revalidate\r\n"
                + "Pragma: no-cache\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                + "connect-src 'self'; style-src 'unsafe-inline'\r\n"
                + "Connection: close\r\n\r\n";
        BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream());
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    private static final String CLIENT_PAGE = "<!doctype html><html lang='fa' dir='rtl'><head>"
            + "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Hotspot Guardian</title>"
            + "<style>body{font:16px sans-serif;max-width:640px;margin:50px auto;padding:24px;}"
            + "h1{font-size:24px}p{line-height:1.9}#state{font-weight:bold}</style></head>"
            + "<body><h1>پایش محلی هات‌اسپات</h1>"
            + "<p>این صفحه را در لپ‌تاپ باز نگه دار. هر ۳ ثانیه یک پیام فقط به گوشی ارسال می‌شود.</p>"
            + "<p>اگر ۱۸ ثانیه پیام نرسد گوشی هشدار می‌دهد. خواب مرورگر نیز باعث هشدار کاذب می‌شود.</p>"
            + "<p id='state'>در حال بررسی…</p>"
            + "<script>async function tick(){try{const r=await fetch('/ping',{cache:'no-store'});"
            + "document.getElementById('state').textContent=r.ok?'ارتباط با گوشی برقرار است':'پاسخ نامعتبر';}"
            + "catch(_){document.getElementById('state').textContent='ارتباط قطع شده است';}}"
            + "tick();setInterval(tick,3000);document.addEventListener('visibilitychange',tick);</script>"
            + "</body></html>";

    private static void nap() {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void onDestroy() {
        keepRunning = false;
        isRunning = false;
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) cpuWakeLock.release();
        cpuWakeLock = null;
        if (radioWakeLock != null && radioWakeLock.isHeld()) radioWakeLock.release();
        radioWakeLock = null;
        autoRecoverySelected = false;
        keepAwakeSelected = false;
        radioGuardSelected = false;
        deferredRecovery = false;
        currentUrl = "";
        status = "پایش خاموش است.";
        activeClients = 0;
        ServerSocket server = serverSocket;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) {}
        }
        if (serverExecutor != null) serverExecutor.shutdownNow();
        if (checkExecutor != null) checkExecutor.shutdownNow();
        getSystemService(NotificationManager.class).cancel(ALERT_ID);
        registry.clear();
        recoveryPolicy.clear();
        super.onDestroy();
    }
}
