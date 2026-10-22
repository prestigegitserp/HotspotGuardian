package com.example.hotspotguardian;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** No app data storage. Users explicitly start and stop monitoring. */
public final class MainActivity extends Activity {
    private TextView state, address;
    private Button start, stop, recover;
    private CheckBox cpuGuard, radioGuard, autoFix;
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
        root.addView(text("Hotspot Guardian Recovery v2.2", 24));
        root.addView(text("پایش + پیشگیری آزمایشی از خواب CPU + تلاش اختیاری برای بازیابی هات‌اسپات. نه درمان تضمینی مودم گوشی.", 16));
        state = text("پایش خاموش است.", 17);
        address = text("", 19);
        address.setTextIsSelectable(true);
        root.addView(state);
        root.addView(address);
        cpuGuard = new CheckBox(this);
        cpuGuard.setText("حفاظت از خواب CPU هنگام پایش (مصرف باتری بیشتر؛ آزمایشی)");
        cpuGuard.setChecked(MonitorService.keepAwakeSelected);
        cpuGuard.setOnCheckedChangeListener((button, checked) -> {
            MonitorService.keepAwakeSelected = checked;
            if (MonitorService.isRunning)
                startService(new Intent(this, MonitorService.class));
        });
        root.addView(cpuGuard);
        radioGuard = new CheckBox(this);
        radioGuard.setText("Wi-Fi Guard اختیاری در پس‌زمینه/صفحه قفل (آزمایشی؛ مصرف باتری بیشتر)");
        radioGuard.setChecked(MonitorService.radioGuardSelected);
        radioGuard.setOnCheckedChangeListener((button, checked) -> {
            MonitorService.radioGuardSelected = checked;
            if (MonitorService.isRunning)
                startService(new Intent(this, MonitorService.class));
        });
        root.addView(radioGuard);
        autoFix = new CheckBox(this);
        autoFix.setText("بازیابی خودکار اختیاری پس از قطع ۱۸ ثانیه‌ای همه کلاینت‌ها (حداکثر ۳ بار؛ فاصله ۴۵ ثانیه)");
        autoFix.setChecked(MonitorService.autoRecoverySelected);
        autoFix.setOnCheckedChangeListener((button, checked) ->
            MonitorService.autoRecoverySelected = checked);
        root.addView(autoFix);
        root.addView(text("بازیابی خودکار به مجوز اختیاری Accessibility نیاز دارد، "
            + "ممکن است اپ جلوی گوشی را باز کند، هات‌اسپات را لحظه‌ای قطع کند، "
            + "و هنگام قفل بودن صفحه، بازیابی در RAM منتظر باز شدن معمول قفل می‌ماند. "
            + "صرفاً کلید Mobile Hotspot در پنل سریع بررسی می‌شود؛ "
            + "هیچ متن صفحه ذخیره یا ارسال نمی‌شود.", 14));
        Button accessibility = new Button(this);
        accessibility.setText("تنظیم مجوز بازیابی (Accessibility)");
        accessibility.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (RuntimeException ignored) {
                state.setText("پنجره تنظیمات Accessibility باز نشد.");
            }
        });
        root.addView(accessibility);
        recover = new Button(this);
        recover.setText("تلاش برای بازیابی همین حالا (صفحه باز و بدون قفل)");
        recover.setOnClickListener(v -> {
            if (!MonitorService.isRunning) {
                state.setText("اول پایش را شروع کن.");
            } else if (!RecoveryAccessibilityService.isReady()) {
                state.setText("ابتدا مجوز اختیاری Accessibility را به Hotspot Guardian بده.");
            } else {
                boolean accepted = RecoveryAccessibilityService.request();
                state.setText(accepted
                    ? "دستور بازیابی پذیرفته شد؛ وضعیت را بررسی کن."
                    : "درخواست اجرا نشد: گوشی را باز نگه دار و اجازه بده بازیابی قبلی تمام شود.");
            }
        });
        root.addView(recover);
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
                + "۴) صفحه را باز بگذار. هر ۳ ثانیه پیام می‌فرستد؛ اگر بیش از ۱۸ ثانیه پیام نرسد، هشدار صادر می‌شود.", 15));
        root.addView(text("حالت صفحه قفل: CPU/Wi-Fi Guard در صورت اجازه اندروید ادامه دارند. "
                + "اگر اتصال قطع شود، اعلان می‌آید؛ ریست هات‌اسپات تا بازکردن قفل انجام نمی‌شود. "
                + "این برنامه قفل گوشی را دور نمی‌زند و مجوز تغییر مستقیم Tethering ندارد.", 15));
        root.addView(text("توجه: قطع هات‌اسپات ممکن است ناشی از سخت‌افزار یا One UI باشد و هیچ "
                + "اپ معمولی نمی‌تواند جلوی آن را تضمین کند. روشن نگه داشتن CPU راهکار آزمایشی است؛ "
                + "خواب لپ‌تاپ/مرورگر نیز می‌تواند بازیابی خودکار اشتباه ایجاد کند. "
                + "برای ایمنی، در حالت پیش‌فرض بازیابی خودکار خاموش است. "
                + "از روت، حذف داده، لاگ و اتصال به اینترنت خارجی استفاده نمی‌کنیم.", 15));
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
