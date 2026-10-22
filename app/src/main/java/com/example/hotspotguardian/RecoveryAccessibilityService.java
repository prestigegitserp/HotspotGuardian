package com.example.hotspotguardian;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Only actuates a verified Mobile Hotspot Quick Settings tile after
 * an explicit app UI action or consented periodic schedule.
 *
 * Does not log events, scrape other applications, use shell or bypass Keyguard.
 */
public final class RecoveryAccessibilityService extends AccessibilityService {
    private static volatile RecoveryAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean claimed = new AtomicBoolean();
    private volatile boolean busy;
    private boolean scheduled;
    private int stage, probes, reopenCount;
    private static final int INITIAL = 0, WAIT_OFF = 1, WAIT_ON = 2;
    private static final int MAX_PROBES = 12;

    public static boolean isReady() { return instance != null; }
    public static boolean isBusy() {
        RecoveryAccessibilityService s = instance;
        return s != null && (s.busy || s.claimed.get());
    }

    private boolean unlocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return km != null && pm != null && !km.isKeyguardLocked() && pm.isInteractive();
    }

    public static boolean canRecoverNow() {
        RecoveryAccessibilityService s = instance;
        return s != null && !isBusy() && s.unlocked();
    }

    public static boolean request(boolean isScheduled) {
        RecoveryAccessibilityService s = instance;
        if (s == null || !s.unlocked() || !s.claimed.compareAndSet(false, true))
            return false;
        if (!s.handler.post(() -> s.begin(isScheduled))) {
            s.claimed.set(false);
            return false;
        }
        return true;
    }

    public static void requestStopMaintenance() {
        RecoveryAccessibilityService s = instance;
        if (s != null) s.handler.post(() ->
                s.stopService(new Intent(s, MonitorService.class)));
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // NO UI events or user contents are collected/stored.
    }

    @Override public void onInterrupt() {
        if (busy) fail("بازیابی توسط سیستم قطع شد.");
    }

    @Override public void onDestroy() {
        if (busy) fail("سرویس دسترسی در میانه بازیابی متوقف شد.");
        claimed.set(false);
        busy = false;
        instance = null;
        super.onDestroy();
    }

    private void begin(boolean fromSchedule) {
        if (busy || !unlocked()) {
            claimed.set(false);
            MonitorService.recoveryResult(false, "گوشی قفل شد؛ بدون لمس متوقف شد.", fromSchedule);
            return;
        }
        scheduled = fromSchedule;
        busy = true;
        stage = INITIAL;
        probes = 0;
        reopenCount = 0;
        MonitorService.status = "پنل سریع برای بازیابی بررسی می‌شود…";
        openPanel();
    }

    private void openPanel() {
        if (!busy || !unlocked()) {
            fail("قفل/نمایشگر اجازه ادامه بازیابی نداد."); return;
        }
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
            fail("اندروید اجازه بازکردن Quick Settings را نداد."); return;
        }
        handler.postDelayed(this::probe, 850);
    }

    private void probe() {
        if (!busy) return;
        if (!unlocked()) {
            fail("بازیابی به‌علت قفل‌شدن قطع شد؛ ممکن است هات‌اسپات خاموش مانده باشد.");
            return;
        }
        if (++probes > MAX_PROBES) {
            fail(stage == WAIT_OFF
                ? "هات‌اسپات خاموش شد اما وضعیت OFF تأیید نشد؛ وضعیت را دستی بررسی کن."
                : "کلید هات‌اسپات یا وضعیتش قابل‌شناسایی نبود؛ هیچ لمس حدسی انجام نشد.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.android.systemui".contentEquals(root.getPackageName())) {
            if (reopenCount < 2 && probes % 4 == 0) {
                reopenCount++;
                openPanel();
            } else retry();
            return;
        }
        Tile tile = findHotspotTile(root);
        if (tile == null) {
            retry();
            return;
        }
        if (stage == INITIAL) {
            if (tile.state == 1) {
                if (!tile.click()) { fail("سامسونگ لمس کلید روشن را رد کرد."); return; }
                stage = WAIT_OFF;
                probes = 0; reopenCount = 0;
                handler.postDelayed(this::openPanel, 1850);
            } else if (tile.state == 0) {
                // Already OFF: do not turn it off again.
                if (!tile.click()) { fail("سامسونگ روشن‌کردن هات‌اسپات را رد کرد."); return; }
                stage = WAIT_ON;
                probes = 0; reopenCount = 0;
                handler.postDelayed(this::openPanel, 1850);
            }
            return;
        }
        if (stage == WAIT_OFF) {
            if (tile.state != 0) { retry(); return; }
            if (!tile.click()) { fail("هات‌اسپات خاموش شد اما روشن‌کردن رد شد."); return; }
            stage = WAIT_ON;
            probes = 0; reopenCount = 0;
            handler.postDelayed(this::openPanel, 1850);
            return;
        }
        if (stage == WAIT_ON) {
            if (tile.state != 1) { retry(); return; }
            succeed();
        }
    }

    private void retry() { handler.postDelayed(this::probe, 400); }

    private void succeed() {
        busy = false;
        claimed.set(false);
        MonitorService.recoveryResult(true,
                "کلید Mobile Hotspot به وضعیت روشن برگشت. حالا اتصال دستگاه‌ها را بررسی کن.",
                scheduled);
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void fail(String reason) {
        busy = false;
        claimed.set(false);
        MonitorService.recoveryResult(false, "رفرش ناموفق: " + reason, scheduled);
    }

    private static final class Tile {
        final AccessibilityNodeInfo clickable;
        final int state;
        Tile(AccessibilityNodeInfo node, int state) {
            clickable = node;
            this.state = state;
        }
        boolean click() { return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
    }

    private Tile findHotspotTile(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int explored = 0;
        while (!queue.isEmpty() && explored++ < 320) {
            AccessibilityNodeInfo n = queue.remove();
            if (hotspotLabel(n)) {
                AccessibilityNodeInfo parent = n;
                int state = safeState(n);
                for (int levels = 0; parent != null && levels < 4; levels++) {
                    if (parent.isClickable()) {
                        int candidateState = state != -1 ? state : safeState(parent);
                        if (candidateState != -1)
                            return new Tile(parent, candidateState);
                    }
                    parent = parent.getParent();
                }
            }
            for (int i=0; i<n.getChildCount() && i<40; i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private static boolean hotspotLabel(AccessibilityNodeInfo node) {
        String desc = node.getContentDescription() == null ? "" :
                node.getContentDescription().toString();
        String text = node.getText() == null ? "" : node.getText().toString();
        String s = (desc + " " + text).toLowerCase(Locale.ROOT)
                .replace('‌', ' ');
        return s.contains("mobile hotspot") || s.contains("wi-fi hotspot")
                || s.contains("mobile hot spot")
                || s.contains("نقطه اتصال همراه")
                || s.contains("نقطه اتصال تلفن همراه")
                || s.contains("هات اسپات") || s.contains("هات‌اسپات");
    }

    private static int safeState(AccessibilityNodeInfo n) {
        if (n.isCheckable()) return n.isChecked() ? 1 : 0;
        String desc = n.getContentDescription() == null ? "" :
                n.getContentDescription().toString();
        String state = Build.VERSION.SDK_INT >= 30 && n.getStateDescription() != null
                ? n.getStateDescription().toString() : "";
        String value = (state + " " + desc).toLowerCase(Locale.ROOT)
                .replace(',', ' ').replace('،', ' ');
        if (value.matches("(?s).*(?:\\boff\\b|خاموش|غیرفعال).*")) return 0;
        if (value.matches("(?s).*(?:\\bon\\b|روشن|فعال).*")) return 1;
        return -1;
    }
}
