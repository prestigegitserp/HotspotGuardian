#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root = Path(__file__).resolve().parents[1]
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
permissions = {x.attrib[ns+'name'] for x in manifest.findall('uses-permission')}
assert permissions == {
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
}, f'Unexpected permissions: {permissions}'
app = manifest.find('application')
assert app.attrib[ns+'allowBackup'] == 'false'
assert ns+'usesCleartextTraffic' not in app.attrib
services = app.findall('service')
assert len(services) == 2
monitor = next(x for x in services if x.attrib[ns+'name'] == '.MonitorService')
ui = next(x for x in services if x.attrib[ns+'name'] == '.RecoveryAccessibilityService')
assert monitor.attrib[ns+'exported'] == 'false'
assert ui.attrib[ns+'permission'] == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
assert ui.attrib[ns+'label'] == '@string/accessibility_service_label'
assert ui.find('meta-data').attrib[ns+'resource'] == '@xml/recovery_accessibility'
text = '\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.java'))
for disallowed in [
    'HttpURLConnection', 'ServerSocket', 'Socket(', 'InetAddress',
    'WebView', 'SharedPreferences', 'FileOutputStream', 'FileWriter',
    'openFileOutput', 'SQLiteDatabase', 'deleteDatabase(', 'deleteFile(',
    'Runtime.getRuntime(', 'ProcessBuilder(', 'WifiManager.setWifiEnabled',
    'ConnectivityManager.startTethering', 'TetheringManager.startTethering',
    'Log.', 'printStackTrace(', 'requestDismissKeyguard', 'setShowWhenLocked',
    'ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS', 'ACCESSIBILITY_SCREENSHOT',
    'setImportantForAccessibility', 'GLOBAL_ACTION_POWER_DIALOG',
]:
    assert disallowed not in text, f'Forbidden or deprecated access: {disallowed}'
assert 'new ServerSocket(' not in text
assert 'android.permission.INTERNET' not in permissions
assert 'android.permission.WAKE_LOCK' not in permissions
assert 'getInstalledAccessibilityServiceList()' in text
assert 'getPackageName().equals(resolve.serviceInfo.packageName)' in text
assert 'MaintenancePolicy' in text and '30L * 60 * 1000' in text
assert 'KEYGUARD_SERVICE' in text and 'isKeyguardLocked()' in text
assert 'claimed.compareAndSet(false, true)' in text
assert 'getRootInActiveWindow()' in text
assert 'com.android.systemui' in text
assert 'MAX_PROBES = 12' in text
assert 'allowBackup' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert not list((root/'app/src/main/java').rglob('*Heartbeat*.java'))
print('PASS: no network, no file writes, no high-risk permissions, UI guardrails')
