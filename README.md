# Hotspot Guardian Safe

A tiny, read-only, user-started local hotspot connection monitor for Samsung Galaxy A35 and other Android phones. **It cannot force an Android hotspot to remain discoverable, restart SoftAP, or repair an undetectable Wi-Fi radio.**

**راهنمای فارسی:** [README_FA.md](README_FA.md) · **بازبینی امنیتی:** [SECURITY_AUDIT_FA.md](SECURITY_AUDIT_FA.md)

## Download the installable APK

**[Successful Android APK build — download the artifact](https://github.com/prestigegitserp/HotspotGuardian/actions/runs/38971523409)**

Open the run, go to **Artifacts**, download `HotspotGuardianSafe-1.0-debug-APK`, extract it, and install `app-debug.apk` on the phone. This build is signed with a **debug signing key**, intended for personal testing, not Play Store distribution.

## What this app really does

When you explicitly start monitoring, the phone opens a tiny HTTP listener on its detected local Wi-Fi/SoftAP IP on port 8765. You open its displayed URL on a laptop connected to that hotspot. The laptop browser sends a heartbeat every 12 seconds, and a missing heartbeat for over 90 seconds triggers an Android notification. Browser sleep or laptop disconnection can also produce an alert.

This app does **not** change Android networking, reboot hotspot, write app files, clear caches, or save activity logs. It does not require root, location, storage, VPN, accessibility or device administrator access. Android and the laptop browser can still create their own routine system records, which the app cannot prevent.

The monitoring listener is accessible to other devices on the **same local network** and must be started/stopped by the user. No external cloud endpoints or analytics SDKs are present.

## Reproducible build and checks

GitHub Actions builds with Java 17, Android API 35, Gradle 8.11.1 and AGP 8.9.2. The workflow runs source security checks, Java heartbeat regression tests, the Android Debug build, and Android Lint, then uploads the APK.

The compiled APK passed the CI build and lint checks. **It has not been device-tested on a physical Galaxy A35.**
