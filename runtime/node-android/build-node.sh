#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=versions.env
source "$script_dir/versions.env"

: "${NODE_SOURCE_DIR:?Set NODE_SOURCE_DIR to a fresh, extracted Node.js source tree}"
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to the extracted Android NDK}"

for tool in python3 make gcc g++ ar; do
  command -v "$tool" >/dev/null || {
    echo "Missing host build tool: $tool" >&2
    exit 1
  }
done

[[ -x "$NODE_SOURCE_DIR/android-configure" ]] || {
  echo "Not a Node.js source tree: $NODE_SOURCE_DIR" >&2
  exit 1
}

[[ ! -e "$NODE_SOURCE_DIR/out/Makefile" ]] || {
  echo "Refusing to reuse an already configured source tree: $NODE_SOURCE_DIR" >&2
  echo "Use a fresh extraction so host and target objects cannot be mixed." >&2
  exit 1
}

# Node's Android wrapper sets target CC/CXX but currently leaves GYP to infer
# the host compiler from them. Without these overrides, Linux host tools are
# incorrectly configured as Android arm64 binaries.
export CC_host=${CC_host:-gcc}
export CXX_host=${CXX_host:-g++}
export AR_host=${AR_host:-ar}

patches=(
  "$script_dir/patches/node-v${NODE_VERSION}-disable-v8-trap-handler.patch"
  "$script_dir/patches/node-v${NODE_VERSION}-android-zlib-auxv.patch"
)
for compatibility_patch in "${patches[@]}"; do
  [[ -f "$compatibility_patch" ]] || {
    echo "Missing pinned Android compatibility patch: $compatibility_patch" >&2
    exit 1
  }
done

(
  cd "$NODE_SOURCE_DIR"
  for compatibility_patch in "${patches[@]}"; do
    patch --batch --forward -p1 < "$compatibility_patch"
  done
  trap_handler_header=deps/v8/src/trap-handler/trap-handler.h
  if grep -Eq '^#define V8_TRAP_HANDLER_SUPPORTED true$|^#define V8_TRAP_HANDLER_VIA_SIMULATOR$' \
      "$trap_handler_header" ||
      [[ $(grep -c '^#define V8_TRAP_HANDLER_SUPPORTED false$' "$trap_handler_header") -ne 1 ]]; then
    echo "Android V8 trap-handler patch did not produce the expected header" >&2
    exit 1
  fi
  if ! grep -Fq "arm_cpu_enable_crc32 = !!(features & HWCAP_CRC32)" deps/zlib/cpu_features.c; then
    echo "Android arm64 zlib feature detection was not switched to auxv" >&2
    exit 1
  fi
  ./android-configure "$ANDROID_NDK_ROOT" "$ANDROID_API" "$ANDROID_ARCH"
  grep -E '^(CC|CXX|AR)\.host' out/Makefile
  make -j"${JOBS:-4}"
)
