package com.example.hotspotguardian;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import android.content.pm.ResolveInfo;
import java.util.List;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 41, 68);
    private static final int BLUE = Color.rgb(29, 96, 197);
    private static final int SOFT = Color.rgb(241, 246, 252);
    private static final int MUTED = Color.rgb(91, 108, 128);
    private static final int ERROR = Color.rgb(173, 48, 48);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView accessStatus, registrationStatus, summary, scheduleStatus;
    private Button primary, arm, stop;
    private RadioGroup frequency;
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            updateScreen();
            handler.postDelayed(this, 3000);
        }
    };

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1.12f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    private void addGap(LinearLayout target, int dp) {
        View space = new View(this);
        target.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private LinearLayout section(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(17));
        card.setBackground(background(Color.WHITE, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        root.addView(card, params);
        return card;
    }

    private Button button(String text, boolean primaryStyle) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primaryStyle ? Color.WHITE : BLUE);
        b.setBackground(background(primaryStyle ? BLUE : SOFT, 12));
        b.setMinimumHeight(dp(54));
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(14, 36, 71));
        getWindow().setNavigationBarColor(SOFT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(SOFT);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(16), dp(24), dp(16), dp(20));
        scroll.addView(main);
        main.addView(label("Hotspot Guardian", 28, INK, true));
        main.addView(label("نسخه ۳.۱ · بازیابی یک‌طرفه از خود گوشی", 15, MUTED, false));
        addGap(main, 16);

        LinearLayout recovery = section(main);
        recovery.addView(label("بازیابی فوری هات‌اسپات", 19, INK, true));
        recovery.addView(label("بدون لپ‌تاپ، IP، HTML یا ارسال پیام. با اجازه تو کلید Mobile Hotspot سامسونگ خاموش و سپس روشن می‌شود.", 14, MUTED, false));
        addGap(recovery, 12);
        primary = button("بازیابی همین الان", true);
        recovery.addView(primary);
        primary.setOnClickListener(v -> {
            if (!RecoveryAccessibilityService.isReady()) {
                summary.setText("اول مراحل فعال‌سازی دسترسی را انجام بده.");
                return;
            }
            if (!RecoveryAccessibilityService.canRecoverNow()) {
                summary.setText("گوشی باید باز باشد و بازیابی قبلی تمام شده باشد.");
                return;
            }
            if (RecoveryAccessibilityService.request(false))
                summary.setText("درخواست پذیرفته شد؛ برنامه پنل سریع را بررسی می‌کند.");
            else summary.setText("درخواست پذیرفته نشد؛ وضعیت دسترسی را بررسی کن.");
        });
        addGap(recovery, 8);
        summary = label("آماده بررسی وضعیت.", 13, MUTED, false);
        recovery.addView(summary);
        addGap(recovery, 10);
        Button manual = button("بازکردن تنظیمات هات‌اسپات (روش قطعیِ دستی)", false);
        recovery.addView(manual);
        manual.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.TETHER_SETTINGS"));
            } catch (RuntimeException ignored) {
                try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
                catch (RuntimeException ignoredAgain) {
                    summary.setText("سامسونگ صفحه تنظیمات را در دسترس قرار نداد.");
                }
            }
        });

        LinearLayout permissions = section(main);
        permissions.addView(label("فعال‌سازی دسترسی بازیابی", 19, INK, true));
        addGap(permissions, 7);
        accessStatus = label("", 15, MUTED, true);
        permissions.addView(accessStatus);
        addGap(permissions, 7);
        registrationStatus = label("در حال بررسی ثبت سرویس توسط اندروید…", 13, MUTED, false);
        permissions.addView(registrationStatus);
        addGap(permissions, 7);
        Button diagnose = button("بررسی شناسایی سرویس توسط خود اندروید", false);
        diagnose.setOnClickListener(v -> diagnoseService());
        permissions.addView(diagnose);
        addGap(permissions, 10);
        permissions.addView(label("۱) اگر در بخش Accessibility برنامه پیدا نمی‌شود یا گزینه خاکستری است، ابتدا «اطلاعات برنامه» را باز کن؛ از منوی سه‌نقطه بالا «Allow restricted settings» را فعال کن. این دسترسی حساس است؛ فقط در صورت اعتماد به سورس برنامه فعالش کن.", 13, MUTED, false));
        Button info = button("۱. بازکردن اطلاعات برنامه", false);
        permissions.addView(info);
        info.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (RuntimeException ex) {
                accessStatus.setText("امکان باز کردن اطلاعات برنامه وجود ندارد.");
            }
        });
        addGap(permissions, 7);
        permissions.addView(label("۲) در Accessibility قسمت Installed apps، سرویس Hotspot Guardian را پیدا و فعال کن. بعد به برنامه برگرد.", 13, MUTED, false));
        Button access = button("۲. بازکردن Accessibility", false);
        permissions.addView(access);
        access.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException ex) {
                accessStatus.setText("تنظیمات Accessibility باز نشد.");
            }
        });

        LinearLayout periodic = section(main);
        periodic.addView(label("رفرش دوره‌ای اختیاری", 19, INK, true));
        periodic.addView(label("این قابلیت خرابی شبکه را تشخیص نمی‌دهد؛ در زمان انتخابی، حتی اگر هات‌اسپات سالم باشد، تلاش به خاموش/روشن‌کردنش می‌کند و اتصال لپ‌تاپ قطع می‌شود. فقط در صورت نیاز روشن کن.", 13, MUTED, false));
        addGap(periodic, 10);
        frequency = new RadioGroup(this);
        frequency.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton r30 = radio("۳۰ دقیقه", 30);
        RadioButton r60 = radio("۶۰ دقیقه", 60);
        RadioButton r120 = radio("۱۲۰ دقیقه", 120);
        frequency.addView(r30); frequency.addView(r60); frequency.addView(r120);
        frequency.check(r60.getId());
        periodic.addView(frequency);
        arm = button("فعال‌کردن رفرش دوره‌ای", true);
        periodic.addView(arm);
        arm.setOnClickListener(v -> startSchedule());
        addGap(periodic, 8);
        stop = button("توقف کامل رفرش دوره‌ای", false);
        periodic.addView(stop);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, MonitorService.class));
            scheduleStatus.setText("رفرش دوره‌ای خاموش شد.");
            handler.postDelayed(this::updateScreen, 150);
        });
        scheduleStatus = label("", 13, MUTED, false);
        periodic.addView(scheduleStatus);

        LinearLayout footer = section(main);
        footer.addView(label("محدودیت واقعی اندروید", 16, INK, true));
        footer.addView(label("هنگام قفل امن صفحه، این اپ قفل را دور نمی‌زند؛ رفرش تا بازکردن معمول قفل عقب می‌افتد. رفع خرابی فریم‌ور Wi‑Fi و جلوگیری قطعی از ناپدیدشدن SSID تضمین‌پذیر نیست. بدون ذخیره لاگ، بدون حذف داده و بدون ارسال اطلاعات به اینترنت.", 13, MUTED, false));
        setContentView(scroll);
        updateScreen();
    }

    private RadioButton radio(String text, int value) {
        RadioButton b = new RadioButton(this);
        b.setText(text);
        b.setId(View.generateViewId());
        b.setTag(value);
        b.setTextSize(12);
        b.setTextColor(INK);
        b.setPadding(0, 0, dp(5), 0);
        return b;
    }

    private void startSchedule() {
        if (!RecoveryAccessibilityService.isReady()) {
            scheduleStatus.setText("اول دسترسی بازیابی را فعال کن.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 88);
            return;
        }
        Intent action = new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_START);
        View selected = frequency.findViewById(frequency.getCheckedRadioButtonId());
        int minutes = selected != null && selected.getTag() instanceof Integer
                ? (Integer) selected.getTag() : 60;
        action.putExtra(MonitorService.EXTRA_MINUTES, minutes);
        try {
            startForegroundService(action);
            handler.postDelayed(this::updateScreen, 350);
        } catch (RuntimeException ex) {
            scheduleStatus.setText("اندروید اجرای برنامه دوره‌ای را نپذیرفت.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 88) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                startSchedule();
            else scheduleStatus.setText("برای زمان‌بندی همراه اعلان، مجوز اعلان لازم است.");
        }
    }

    /**
     * Uses Android's registered accessibility-service list, not a guessed
     * Settings UI path. No privileges, ADB, logs or persistent storage.
     */
    private void diagnoseService() {
        if (registrationStatus == null) return;
        try {
            AccessibilityManager manager =
                    (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> installed =
                    manager == null ? null : manager.getInstalledAccessibilityServiceList();
            if (installed == null) {
                registrationStatus.setText("اندروید فهرست سرویس‌ها را در اختیار برنامه قرار نداد.");
                return;
            }
            boolean registered = false;
            for (AccessibilityServiceInfo info : installed) {
                if (info == null) continue;
                ResolveInfo resolve = info.getResolveInfo();
                if (resolve == null || resolve.serviceInfo == null) continue;
                if (getPackageName().equals(resolve.serviceInfo.packageName)
                    && resolve.serviceInfo.name != null
                    && resolve.serviceInfo.name.endsWith("RecoveryAccessibilityService")) {
                    registered = true;
                    break;
                }
            }
            if (registered) {
                registrationStatus.setText(RecoveryAccessibilityService.isReady()
                    ? "✓ سرویس شناسایی و فعال شده است."
                    : "✓ اندروید سرویس را شناسایی کرده، ولی هنوز فعال نیست. منوی Allow restricted settings و Installed apps را بررسی کن.");
                registrationStatus.setTextColor(RecoveryAccessibilityService.isReady()
                    ? Color.rgb(24,119,79) : MUTED);
            } else {
                registrationStatus.setText("✕ اندروید سرویس بازیابی این نصب را در فهرست خود نشان نمی‌دهد. اگر پس از فعال‌کردن Restricted settings و یک Restart تغییری نکرد، نصب APK جدید را بررسی کن.");
                registrationStatus.setTextColor(ERROR);
            }
        } catch (RuntimeException ex) {
            registrationStatus.setText("پرس‌وجوی ثبت سرویس توسط اندروید امکان‌پذیر نبود.");
            registrationStatus.setTextColor(ERROR);
        }
    }

    private void updateScreen() {
        if (accessStatus == null) return;
        boolean enabled = RecoveryAccessibilityService.isReady();
        accessStatus.setText(enabled
                ? "● دسترسی بازیابی فعال است."
                : "● دسترسی بازیابی هنوز فعال نیست.");
        accessStatus.setTextColor(enabled ? Color.rgb(24, 119, 79) : ERROR);
        if (!MonitorService.running) {
            scheduleStatus.setText("خاموش · هیچ پایش مداوم یا اتصال لپ‌تاپ وجود ندارد.");
            arm.setEnabled(enabled);
            stop.setEnabled(false);
        } else {
            scheduleStatus.setText(MonitorService.status +
                    (MonitorService.awaitingUnlock ? " منتظر بازکردن قفل." : ""));
            arm.setEnabled(enabled);
            stop.setEnabled(true);
        }
        boolean busy = RecoveryAccessibilityService.isBusy();
        primary.setEnabled(enabled && !busy);
        if (!busy && MonitorService.status.startsWith("کلید Mobile Hotspot"))
            summary.setText(MonitorService.status);
        if (!busy && MonitorService.status.startsWith("رفرش ناموفق"))
            summary.setText(MonitorService.status);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
        diagnoseService();
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }
}
