package com.example.hotspotguardian;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Explicitly opted-in local UI recovery. No gestures, screenshots, network
 * writes or arbitrary UI actions. Only clicks on a positively identified
 * Mobile Hotspot Quick Settings tile with a readable ON/OFF state.
 */
public final class RecoveryAccessibilityService extends AccessibilityService {
    private static volatile RecoveryAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean busy;
    private int stage;
    private int probes;
    private static final int BEGIN = 0, AFTER_OFF = 1, AFTER_ON = 2;

    public static boolean isReady() { return instance != null; }

    public static boolean request() {
        RecoveryAccessibilityService s = instance;
        if (s == null) return false;
        s.handler.post(s::beginRecovery);
        return true;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Deliberately no event recording or content collection.
    }

    @Override public void onInterrupt() { abort("بازیابی توسط اندروید متوقف شد."); }

    @Override public void onDestroy() {
        abort("سرویس دسترسی خاموش شد.");
        instance = null;
        super.onDestroy();
    }

    private void beginRecovery() {
        if (busy || !MonitorService.isRunning) return;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (km.isKeyguardLocked() || !pm.isInteractive()) {
            MonitorService.status = "برای بازیابی خودکار باید صفحه گوشی روشن و باز باشد.";
            return;
        }
        busy = true;
        stage = BEGIN;
        probes = 0;
        MonitorService.status = "تلاش امن برای بازنشانی هات‌اسپات از پنل سامسونگ…";
        showAndProbe();
    }

    private void showAndProbe() {
        if (!busy || !MonitorService.isRunning) { abort("عملیات متوقف شد."); return; }
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
            abort("بازکردن پنل سریع توسط سامسونگ مجاز نشد.");
            return;
        }
        handler.postDelayed(this::probe, 900);
    }

    private void probe() {
        if (!busy) return;
        if (!MonitorService.isRunning) { abort("پایش متوقف شد."); return; }
        if (++probes > 9) {
            abort("کلید یا وضعیت هات‌اسپات قابل تشخیص نیست؛ بدون لمس حدسی متوقف شد.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !root.getPackageName().toString().equals("com.android.systemui")) {
            handler.postDelayed(this::probe, 550);
            return;
        }
        Match match = findTile(root);
        if (match == null || match.state == -1 || match.actionNode == null) {
            handler.postDelayed(this::probe, 550);
            return;
        }

        if (stage == BEGIN) {
            if (match.state == 1) {
                if (!match.actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    abort("نتوانستم کلید هات‌اسپات را لمس کنم."); return;
                }
                stage = AFTER_OFF;
                probes = 0;
                handler.postDelayed(this::showAndProbe, 2100);
            } else {
                if (!match.actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    abort("نتوانستم هات‌اسپات را روشن کنم."); return;
                }
                stage = AFTER_ON;
                probes = 0;
                handler.postDelayed(this::showAndProbe, 2100);
            }
            return;
        }
        if (stage == AFTER_OFF) {
            if (match.state != 0) {
                // Never click again unless the UI confirms OFF.
                handler.postDelayed(this::probe, 600);
                return;
            }
            if (!match.actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                abort("هات‌اسپات خاموش شد اما روشن‌کردن خودکار ناموفق بود.");
                return;
            }
            stage = AFTER_ON;
            probes = 0;
            handler.postDelayed(this::showAndProbe, 2100);
            return;
        }
        if (stage == AFTER_ON) {
            if (match.state != 1) {
                handler.postDelayed(this::probe, 550);
                return;
            }
            busy = false;
            MonitorService.status = "کلید هات‌اسپات طبق پنل سامسونگ روشن است؛ اتصال لپ‌تاپ را بررسی کن.";
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private static class Match {
        AccessibilityNodeInfo actionNode;
        int state = -1; // -1 unknown, 0 off, 1 on
    }

    private Match findTile(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int explored = 0;
        while (!q.isEmpty() && explored++ < 350) {
            AccessibilityNodeInfo node = q.remove();
            if (isHotspotLabel(node)) {
                AccessibilityNodeInfo p = node;
                for (int levels=0; levels<5 && p!=null; levels++,p=p.getParent()) {
                    int state = readState(p);
                    if (p.isClickable() && state != -1) {
                        Match result = new Match();
                        result.actionNode = p;
                        result.state = state;
                        return result;
                    }
                }
            }
            for (int i=0; i<node.getChildCount() && i<50; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    private boolean isHotspotLabel(AccessibilityNodeInfo n) {
        String txt = n.getText() == null ? "" : n.getText().toString();
        String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
        String combined = (txt + " " + desc).toLowerCase(Locale.ROOT)
            .replace('‌', ' ').replace("  ", " ");
        return combined.contains("mobile hotspot") || combined.contains("wi-fi hotspot")
            || combined.contains("نقطه اتصال همراه")
            || combined.contains("نقطه اتصال تلفن همراه")
            || combined.contains("هات اسپات");
    }

    private static int readState(AccessibilityNodeInfo node) {
        // Prefer accessibility checkable state (not unrelated enable/disable).
        if (node.isCheckable()) return node.isChecked() ? 1 : 0;
        CharSequence state = android.os.Build.VERSION.SDK_INT >= 30 ? node.getStateDescription() : null;
        String info = state == null ? "" : state.toString().toLowerCase(Locale.ROOT);
        if (info.isEmpty() && node.getContentDescription() != null)
            info = node.getContentDescription().toString().toLowerCase(Locale.ROOT);
        if (info.matches("(?s).*(?:\\boff\\b|خاموش|غیرفعال).*$")) return 0;
        if (info.matches("(?s).*(?:\\bon\\b|روشن|فعال).*$")) return 1;
        return -1;
    }

    private void abort(String reason) {
        if (!busy) return;
        busy = false;
        MonitorService.status = reason;
    }
}
