# Hotspot Guardian Recovery v2.2 — Galaxy A35

**APK:** Go to [the latest successful GitHub Actions build](https://github.com/prestigegitserp/HotspotGuardian/actions/workflows/build-apk.yml) and download `HotspotGuardianRecovery-v2.2-APK` from **Artifacts**. Unzip and install `app-debug.apk`.

## Screen locked: what really works

**Monitoring and optional wake locks can continue while the screen is locked.** Toggle CPU Guard and/or the experimental Wi-Fi Guard before locking. Both can increase battery consumption; neither guarantees that Samsung's SoftAP will stay discoverable, and Wi-Fi locks may not affect the hotspot radio.

**Direct off/on recovery while the phone is securely locked is NOT supported by ordinary Android app privileges.** Android's tethering control is privileged; this app does not bypass the Keyguard. If the monitored laptop heartbeat disappears for >18 seconds while locked, it keeps a pending recovery in RAM and sends an alert. The 2-second monitoring loop checks for a **normal user unlock**, and if connectivity remains absent, it attempts the verified Quick Settings recovery. This requires optional Accessibility service and explicit Auto Recovery opt-in.

## Runtime and protections

- Laptop local-only ping: every 3 seconds; monitor checks every 2 seconds (Android may suspend operations in Doze).
- Offline threshold: 18 seconds; recovery attempts at most three times per continuous outage, minimum 45 seconds apart.
- One recovery request at a time, de-duplicated. Denied commands (such as attempts while locked) **do not count** toward three-attempt limit.
- Before **every** Accessibility tile interaction: check user is unlocked and screen is interactive. Never unlock, display over Keyguard, capture screen, or perform guessed taps.
- CPU and Wi-Fi locks are released when monitoring stops. Opt-ins are volatile in RAM. No file/database/log writer, data wipe, shell, cloud, root or hidden APIs.
- Android and the laptop's browser may create independent routine system data/caches and their behavior is outside this app's control.

## Set up

1. Turn on the Samsung A35 hotspot and connect your laptop.
2. Open the app and select CPU Guard; optional Wi-Fi Guard is experimental and can be disabled if it makes no difference.
3. Start monitoring, then open the IP:8765 page shown by the app on the laptop and keep that page visible/active.
4. If automatic recovery after **normal unlock** is desired, turn on Accessibility service for the app and explicitly enable Auto Recovery. It remains OFF by default.
5. Use the notification Stop action to release held CPU/Wi-Fi locks at any time.

**This is not a validated fix for a dead/hung SoftAP, and has not been physically tested on a Galaxy A35.** Android Lint and automated tests only validate code correctness/security checks, not hardware repair. If a lock starts mid-toggle, OS security restrictions can prevent completion; verify the hotspot afterward.

**راهنمای فارسی:** [docs/RECOVERY_V2_FA.md](docs/RECOVERY_V2_FA.md)
