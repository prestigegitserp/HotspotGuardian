package com.example.hotspotguardian;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Proactive user-opted-in maintenance, not ping/traffic monitoring.
 * Zero sockets, laptop pairing, IPs, saved preferences, logs or disk writes.
 */
public final class MonitorService extends Service {
    static final String ACTION_START = "com.example.hotspotguardian.START_MAINTENANCE";
    static final String ACTION_STOP = "com.example.hotspotguardian.STOP_MAINTENANCE";
    static final String EXTRA_MINUTES = "minutes";
    private static final String CHANNEL = "hotspot_guardian_v3";
    private static final int NOTIFICATION_ID = 101;

    public static volatile boolean running;
    public static volatile boolean awaitingUnlock;
    public static volatile String status = "حالت خودکار خاموش است.";
    public static volatile long nextDueAt = -1L;
    public static volatile int selectedMinutes = 60;

    private final MaintenancePolicy policy = new MaintenancePolicy();
    private ScheduledExecutorService worker;
    private volatile boolean finishing;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "رفرش اختیاری هات‌اسپات", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int minutes = intent.getIntExtra(EXTRA_MINUTES, 60);
        if (minutes != 30 && minutes != 60 && minutes != 120) {
            status = "فاصله زمانی نامعتبر است.";
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!RecoveryAccessibilityService.isReady()) {
            status = "اول دسترسی بازیابی را فعال کن.";
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification notice = createNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notice,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notice);
            }
        } catch (RuntimeException ex) {
            status = "اندروید اجازه اجرای سرویس را نداد.";
            stopSelf();
            return START_NOT_STICKY;
        }

        selectedMinutes = minutes;
        policy.arm(SystemClock.elapsedRealtime(), minutes * 60_000L);
        nextDueAt = SystemClock.elapsedRealtime() + minutes * 60_000L;
        awaitingUnlock = false;
        status = "رفرش دوره‌ای فعال؛ هر " + minutes + " دقیقه، فقط با صفحه باز.";
        running = true;
        if (worker == null || worker.isShutdown()) {
            worker = Executors.newSingleThreadScheduledExecutor();
            worker.scheduleWithFixedDelay(this::tick, 20, 20, TimeUnit.SECONDS);
        }
        return START_NOT_STICKY;
    }

    private Notification createNotification() {
        Intent ui = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, ui,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("Hotspot Guardian · رفرش دوره‌ای فعال")
                .setContentText("فقط با اجازه تو؛ توقف از همین اعلان")
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "توقف کامل", stopAction)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
    }

    private void tick() {
        if (finishing || !running || !policy.isDue(SystemClock.elapsedRealtime())) return;
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (km == null || km.isKeyguardLocked() ||
                    pm == null || !pm.isInteractive()) {
                awaitingUnlock = true;
                status = "زمان رفرش رسیده؛ برای حفظ امنیت، منتظر بازشدن عادی قفل است.";
                return;
            }
            if (!RecoveryAccessibilityService.isReady()) {
                status = "سرویس دسترسی قطع شده؛ رفرش خودکار متوقف شد.";
                stopSelf();
                return;
            }
            if (!RecoveryAccessibilityService.canRecoverNow()) return;
            if (RecoveryAccessibilityService.request(true)) {
                awaitingUnlock = false;
                policy.accepted(SystemClock.elapsedRealtime());
                nextDueAt = SystemClock.elapsedRealtime() + selectedMinutes * 60_000L;
                status = "درخواست رفرش به سامسونگ فرستاده شد؛ تأیید نهایی در پنل انجام می‌شود.";
            }
        } catch (RuntimeException ignored) {
            status = "سامسونگ درخواست رفرش را رد کرد؛ حالت خودکار متوقف شد.";
            stopSelf();
        }
    }

    /**
     * Called ONLY when the Accessibility controller completes an actual
     * ON/OFF cycle or safely aborts. Never silently retry broken UI.
     */
    public static void recoveryResult(boolean success, String message, boolean scheduled) {
        status = message;
        if (scheduled && !success) {
            running = false;
            // Explicitly stop rather than silently attempting the same failing UI again.
            RecoveryAccessibilityService.requestStopMaintenance();
        }
    }

    @Override public void onDestroy() {
        finishing = true;
        running = false;
        awaitingUnlock = false;
        nextDueAt = -1;
        policy.stop();
        if (worker != null) worker.shutdownNow();
        worker = null;
        if (!status.startsWith("سامسونگ") && !status.startsWith("کلید") &&
                !status.startsWith("رفرش ناموفق")) status = "رفرش دوره‌ای خاموش است.";
        super.onDestroy();
    }
}
