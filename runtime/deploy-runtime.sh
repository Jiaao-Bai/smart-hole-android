#!/usr/bin/env bash
set -euo pipefail

archive=${1:-}
serial=${ANDROID_SERIAL:-}
if [[ -z "$archive" || ! -f "$archive" ]]; then
  echo "usage: ANDROID_SERIAL=<optional> $0 runtime.tar.gz" >&2
  exit 2
fi

adb_args=()
if [[ -n "$serial" ]]; then adb_args=(-s "$serial"); fi
adb_cmd=(adb "${adb_args[@]}")

state=$("${adb_cmd[@]}" get-state)
[[ "$state" == device ]] || { echo "ADB device is not online: $state" >&2; exit 1; }
abi=$("${adb_cmd[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')
[[ "$abi" == arm64-v8a ]] || { echo "unsupported primary ABI: $abi" >&2; exit 1; }
uid=$("${adb_cmd[@]}" shell "su -c 'id -u'" | tr -d '\r')
[[ "$uid" == 0 ]] || { echo "ADB shell has no granted root access" >&2; exit 1; }

if "${adb_cmd[@]}" shell "su -c 'test -e /data/adb/dsh-android'"; then
  echo "refusing to overwrite existing /data/adb/dsh-android" >&2
  exit 1
fi

remote_archive=/data/local/tmp/dsh-android-arm64.tar.gz
local_sha=$(shasum -a 256 "$archive" | awk '{print $1}')
"${adb_cmd[@]}" push "$archive" "$remote_archive"
remote_sha=$("${adb_cmd[@]}" shell sha256sum "$remote_archive" | awk '{print $1}')
[[ "$local_sha" == "$remote_sha" ]] || { echo "ADB transfer checksum mismatch" >&2; exit 1; }

"${adb_cmd[@]}" shell "su -c 'umask 077; tar -xzf $remote_archive -C /data/adb; chmod 0700 /data/adb/dsh-android/runtime/bin/node /data/adb/dsh-android/runtime/start-android.sh /data/adb/dsh-android/runtime/control-android.sh'"
"${adb_cmd[@]}" shell "su -c 'LD_LIBRARY_PATH=/data/adb/dsh-android/runtime/lib /data/adb/dsh-android/runtime/bin/node --version; cat /data/adb/dsh-android/runtime/manifest.json'"
"${adb_cmd[@]}" shell "su -c 'LD_LIBRARY_PATH=/data/adb/dsh-android/runtime/lib TMPDIR=/data/local/tmp /data/adb/dsh-android/runtime/bin/node /data/adb/dsh-android/runtime/tests/node-smoke.mjs'"
"${adb_cmd[@]}" shell "su -c 'LD_LIBRARY_PATH=/data/adb/dsh-android/runtime/lib /data/adb/dsh-android/runtime/bin/node /data/adb/dsh-android/runtime/node_modules/dsh-plugin-android/test/device-smoke.mjs'"
"${adb_cmd[@]}" shell rm -f "$remote_archive"
