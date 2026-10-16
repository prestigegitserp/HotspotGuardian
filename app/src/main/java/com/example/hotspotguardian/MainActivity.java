package com.example.hotspotguardian;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** No app data storage. Users explicitly start and stop monitoring. */
public final class MainActivity extends Activity {
    private TextView state, address;
    private Button start, stop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 32);
        scroll.addView(root);
        root.addView(text("نگهبان امن هات‌اسپات", 24));
        root.addView(text("پایش اتصال لپ‌تاپ، بدون روت، تغییر تنظیمات گوشی، حذف داده یا ثبت فایل توسط برنامه.", 16));
        state = text("پایش خاموش است.", 17);
        address = text("", 19);
        address.setTextIsSelectable(true);
        root.addView(state);
        root.addView(address);
        start = new Button(this);
        start.setText("شروع پایش");
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
                return;
            }
            launchMonitor();
        });
        root.addView(start);
        stop = new Button(this);
        stop.setText("توقف کامل پایش");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, MonitorService.class));
            refresh();
        });
        root.addView(stop);
        root.addView(text("۱) هات‌اسپات گوشی را روشن کن. ۲) شروع پایش را بزن. "
                + "۳) نشانی نمایش‌داده‌شده را در مرورگر لپ‌تاپ متصل به همان هات‌اسپات باز کن. "
                + "۴) صفحه را باز بگذار. اگر ۹۰ ثانیه پیام نرسد، هشدار صادر می‌شود.", 15));
        root.addView(text("توجه: برنامه فقط اتصال واقعی به صفحه محلی را پایش می‌کند؛ "
                + "SSID ناپدیدشده را تعمیر یا دوباره فعال نمی‌کند. خواب لپ‌تاپ و مرورگر "
                + "می‌تواند هشدار کاذب ایجاد کند. خود سیستم‌عامل ممکن است رویدادهای معمول را ثبت کند.", 15));
        setContentView(scroll);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, 12, 0, 12);
        t.setGravity(Gravity.START);
        return t;
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 77) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchMonitor();
            } else {
                state.setText("مجوز اعلان فعال نیست؛ پایش برای جلوگیری از هشدار بی‌صدا شروع نشد.");
            }
        }
    }

    private void launchMonitor() {
        try {
            startForegroundService(new Intent(this, MonitorService.class));
        } catch (RuntimeException ignored) {
            state.setText("اندروید اجازه شروع سرویس را نداد.");
        }
        refresh();
    }

    private void refresh() {
        if (state == null) return;
        state.setText(MonitorService.status);
        String url = MonitorService.currentUrl;
        address.setText(url.isEmpty() ? "" : "نشانی لپ‌تاپ:\n" + url);
        start.setEnabled(!MonitorService.isRunning);
        stop.setEnabled(MonitorService.isRunning);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(updater);
        handler.post(updater);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }
}
