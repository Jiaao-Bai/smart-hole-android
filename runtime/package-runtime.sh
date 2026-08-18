#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)
node_binary=${1:-}
output=${2:-"$script_dir/dist/dsh-android-arm64.tar.gz"}
export COPYFILE_DISABLE=1

if [[ -z "$node_binary" || ! -x "$node_binary" ]]; then
  echo "usage: $0 /path/to/android-arm64/node [output.tar.gz]" >&2
  exit 2
fi

binary_description=$(file -b "$node_binary")
if [[ "$binary_description" != *ELF* || "$binary_description" != *ARM\ aarch64* ]]; then
  echo "not an Android arm64 ELF Node binary: $binary_description" >&2
  exit 2
fi

node_license=${NODE_LICENSE:-}
if [[ -z "$node_license" || ! -f "$node_license" ]]; then
  echo "set NODE_LICENSE to the LICENSE file from the exact Node.js source build" >&2
  exit 2
fi

node_libcxx=${NODE_LIBCXX:-}
if [[ -z "$node_libcxx" || ! -f "$node_libcxx" ]]; then
  echo "set NODE_LIBCXX to the arm64 libc++_shared.so from the exact Android NDK" >&2
  exit 2
fi
libcxx_description=$(file -b "$node_libcxx")
if [[ "$libcxx_description" != *ELF* || "$libcxx_description" != *ARM\ aarch64* || "$libcxx_description" != *shared\ object* ]]; then
  echo "not an Android arm64 libc++ shared library: $libcxx_description" >&2
  exit 2
fi

dsh_dir="$script_dir/dsh-android"
source "$script_dir/node-android/versions.env"
if [[ ! -d "$dsh_dir/node_modules/@deepseek-ai/dsh" ]]; then
  echo "run runtime/dsh-android/prepare-runtime.sh first" >&2
  exit 2
fi

stage=$(mktemp -d "${TMPDIR:-/tmp}/dsh-android-package.XXXXXX")
cleanup() {
  rm -rf -- "$stage"
}
trap cleanup EXIT

payload="$stage/dsh-android/runtime"
mkdir -p "$payload/bin" "$payload/lib" "$payload/profile/android" "$payload/tests" "$(dirname "$output")"
install -m 0755 "$node_binary" "$payload/bin/node"
install -m 0644 "$node_libcxx" "$payload/lib/libc++_shared.so"
install -m 0644 "$script_dir/node-android/smoke-test.mjs" "$payload/tests/node-smoke.mjs"
cp -R "$dsh_dir/node_modules" "$payload/node_modules"
cp -R "$dsh_dir/plugin-android" "$payload/node_modules/dsh-plugin-android"
cp "$dsh_dir/package.json" "$dsh_dir/package-lock.json" "$payload/"
cp "$dsh_dir/profile/package.json" "$payload/profile/android/package.json"
cp "$dsh_dir/profile/cordis.patch.yml" "$payload/profile/android/cordis.patch.yml"
cp -R "$dsh_dir/profile/agent-presets" "$payload/profile/android/agent-presets"
mkdir -p "$payload/profile/android/node_modules"
cp -R "$dsh_dir/plugin-android" "$payload/profile/android/node_modules/dsh-plugin-android"
install -m 0755 "$dsh_dir/start-android.sh" "$payload/start-android.sh"
install -m 0755 "$dsh_dir/control-android.sh" "$payload/control-android.sh"
cp "$project_dir/LICENSE" "$payload/LICENSE.deepseek-harness-android"
cp "$node_license" "$payload/LICENSE.nodejs"

dsh_version=$(node -p "require('$dsh_dir/node_modules/@deepseek-ai/dsh/package.json').version")
android_plugin_version=$(node -p "require('$dsh_dir/plugin-android/package.json').version")
cat > "$payload/manifest.json" <<EOF
{
  "schemaVersion": 1,
  "architecture": "arm64-v8a",
  "platform": "android-bionic",
  "nodeVersion": "$NODE_VERSION",
  "dshVersion": "$dsh_version",
  "androidPluginVersion": "$android_plugin_version"
}
EOF

tar -C "$stage" -czf "$output" dsh-android
shasum -a 256 "$output" > "$output.sha256"
printf 'payload: %s\n' "$output"
du -h "$output"
cat "$output.sha256"
