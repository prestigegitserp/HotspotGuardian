#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check_security.py
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
javac -d "$TEMP" app/src/main/java/com/example/hotspotguardian/MaintenancePolicy.java tests/MaintenancePolicyTest.java
java -ea -cp "$TEMP" com.example.hotspotguardian.MaintenancePolicyTest
