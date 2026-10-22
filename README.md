# Hotspot Guardian Recovery v2.1 — Galaxy A35

**Downloadable and installable APK:** open [GitHub Actions](https://github.com/prestigegitserp/HotspotGuardian/actions/workflows/build-apk.yml), open the latest successful Build, download `HotspotGuardianRecovery-v2.1-APK` from **Artifacts**, extract and install `app-debug.apk`.

## Two actual interventions, both optional

1. **CPU Guard**: user-enabled partial WakeLock while monitoring. Can reduce CPU sleep related interruptions; cannot guarantee SoftAP reliability and uses more battery.
2. **Recovery via Accessibility**: when the phone is **unlocked** and Samsung Quick Settings exposes a Mobile Hotspot tile with readable on/off state, the app attempts a safe off/on sequence. A manual "Recover now" button is included. Auto-recovery must be explicitly enabled; it triggers only after all previously observed laptop heartbeats have been lost for >18s, with a 45-second cooldown and up to 3 attempts per continuous outage, then re-armed by a real laptop reconnect. The temporary disconnection is intentional. Browser sleep can produce false positives, so leave auto-recovery disabled unless you accept that risk.

**Heartbeats every 3 seconds; checks every 2 seconds while the user-started service can run.** No guarantees during Android Doze or radio firmware failures.

**No root, ADB, system shell, file deletion, app-created persistent logs, settings writes, screenshots, data harvesting, or cloud services.** The Android **Accessibility permission is sensitive** and not required for monitoring or CPU Guard. The service only examines the Quick Settings tile during recovery, cannot unlock a locked device, and stops without clicking if the tile/state cannot be verified.

Android does not grant ordinary third-party apps direct, reliable control over Samsung's Wi-Fi tethering state; this workaround is not guaranteed. Do not assume the radio is repaired merely because Quick Settings shows ON. No physical Galaxy A35 test has been performed.

**راهنمای کامل فارسی:** [docs/RECOVERY_V2_FA.md](docs/RECOVERY_V2_FA.md) · [Source security checks](tests/check_security.py) · [Legacy v1 guide](README_FA.md)

CI builds with Android SDK 35, JDK 17, Gradle 8.11.1 and AGP 8.9.2, running security, timeout/cooldown unit tests, APK build, and Android lint.

**Updating v1:** GitHub debug signing keys are ephemeral; Android may require uninstalling the older debug-signed APK before installing this debug build. This app itself does not delete any data. Android/Chrome OS internals can still create their own routine caches and system records.
