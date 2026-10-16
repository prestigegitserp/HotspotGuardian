# Security scope / محدوده امنیت

این پروژه **فقط پایش محلی** انجام می‌دهد و مجوزهای زیر را دارد:
INTERNET (صرفاً سرور محلی)، POST_NOTIFICATIONS، FOREGROUND_SERVICE و FOREGROUND_SERVICE_SPECIAL_USE.

- بدون مجوز Storage، Location، VPN، Accessibility، Root، تغییر Wi-Fi یا تنظیمات سیستم
- بدون API خواندن یا پاک‌کردن فایل، تغییر رجیستری، ذخیره‌سازی لاگ، کش یا تنظیمات
- بدون Analytics یا اتصال به سرور ابری؛ اتصال باز روی IP لوکال است
- وضعیت حداکثر ۴ IP در RAM نگهداری می‌شود؛ با توقف سرویس پاک می‌شود
- پاسخ HTML با Cache-Control: no-store ارائه می‌شود
- سرویس توسط کاربر شروع و متوقف می‌شود؛ auto-start ندارد
- No false guarantee: OS-level logs/caches and laptop browser storage are outside app control
- No false guarantee: Cannot prevent SSID radio loss, detect it reliably without a client, or repair SoftAP
- Debug builds are signed with debug keystore and intended for personal sideload evaluation

See tests/check_security.py and GitHub Actions for repeatable source audits.
