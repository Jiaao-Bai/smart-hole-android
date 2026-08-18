#!/system/bin/sh
set -eu

runtime_root=${DSH_ANDROID_RUNTIME_ROOT:-/data/adb/dsh-android}
workspace=${DSH_ANDROID_WORKSPACE:-$runtime_root/workspaces/default}

export DSH_HOME="$runtime_root/state/dsh-home"
export DSH_ANDROID_WORKSPACE="$workspace"
export DSH_PERMISSION_MODE=danger-full-access
export DSH_TELEMETRY_DISABLED=1
export LD_LIBRARY_PATH="$runtime_root/runtime/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

profile_dir="$DSH_HOME/profiles/android"
mkdir -p "$workspace"
mkdir -p "$profile_dir"
# These files are release-owned configuration, not user state. Refresh them on
# every start so an APK/runtime update cannot keep an obsolete Android profile.
cp "$runtime_root/runtime/profile/android/package.json" "$profile_dir/package.json"
cp "$runtime_root/runtime/profile/android/cordis.patch.yml" "$profile_dir/cordis.patch.yml"
mkdir -p "$profile_dir/node_modules"
# This package is release-owned like the profile files above. Replace its exact
# directory so runtime upgrades cannot leave an older plugin or stale files in
# the writable DSH home.
rm -rf "$profile_dir/node_modules/dsh-plugin-android"
cp -R "$runtime_root/runtime/profile/android/node_modules/dsh-plugin-android" \
  "$profile_dir/node_modules/dsh-plugin-android"
android_preset_dir="$DSH_HOME/.agent-presets/android"
mkdir -p "$android_preset_dir"
cp "$runtime_root/runtime/profile/android/agent-presets/android/preset.yml" \
  "$android_preset_dir/preset.yml"
cp "$runtime_root/runtime/profile/android/agent-presets/android/agent.cordis.yml" \
  "$android_preset_dir/agent.cordis.yml"

cd "$workspace"
exec "$runtime_root/runtime/bin/node" --expose-internals \
  "$runtime_root/runtime/node_modules/@deepseek-ai/dsh/lib/bin.js" \
  --profile android --host 127.0.0.1 --port 3080 "$@"
