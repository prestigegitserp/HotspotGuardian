#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
base = Path(__file__).resolve().parents[1]
root = ET.parse(base/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
allowed={
    'android.permission.INTERNET',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
}
permissions={node.attrib[ns+'name'] for node in root.findall('uses-permission')}
assert permissions == allowed, f'Unexpected permissions: {permissions ^ allowed}'
app=root.find('application')
assert app.attrib[ns+'allowBackup']=='false'
services=app.findall('service')
assert len(services)==1 and services[0].attrib[ns+'exported']=='false'
sources='\n'.join(p.read_text() for p in (base/'app/src/main/java').rglob('*.java'))
for forbidden in [
 'SharedPreferences','openFileOutput','FileOutputStream','FileWriter',
 'SQLiteDatabase','File.delete(', 'deleteDatabase(', 'deleteFile(',
 'Runtime.getRuntime(', 'ProcessBuilder(', 'Settings.System.put',
 'Settings.Global.put', 'WifiManager.setWifiEnabled',
 'ConnectivityManager.startTethering', 'Log.', 'printStackTrace(',
 'WebView', 'HttpURLConnection', 'URL.openConnection',
 'WakeLock', 'PowerManager.newWakeLock'
]:
    assert forbidden not in sources, f'Forbidden API: {forbidden}'
assert 'new ServerSocket(PORT, 4, local)' in sources
assert 'Cache-Control: no-store' in sources
assert 'public static volatile boolean isRunning' in sources
print('PASS: minimal permissions, no backup, private service, no disk or system-setting writes')
