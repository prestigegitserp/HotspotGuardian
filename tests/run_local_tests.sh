#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check_security.py
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" app/src/main/java/com/example/hotspotguardian/HeartbeatRegistry.java tests/HeartbeatRegistryTest.java
java -ea -cp "$TMP" com.example.hotspotguardian.HeartbeatRegistryTest
javac -d "$TMP" app/src/main/java/com/example/hotspotguardian/RecoveryPolicy.java tests/RecoveryPolicyTest.java
java -ea -cp "$TMP" com.example.hotspotguardian.RecoveryPolicyTest
