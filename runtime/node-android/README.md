# Android Node.js runtime

This directory records the reproducible baseline for the native arm64/Bionic
runtime. Version and checksum pins live in `versions.env`; downloaded archives,
extracted toolchains, Node sources, and build outputs stay outside Git.

Baseline:

- Node.js 24.18.1;
- Android NDK r29 stable;
- arm64 target at API 28;
- Linux x86_64 build host.

Node.js classifies Android as experimental and does not test it in upstream CI.
Its `android-configure` entry point is usable, but the host compilers must be
declared explicitly during a cross-build. The source release also ships an
official Android patch that disables the unsupported V8 trap handler; the build
wrapper applies it and refreshes its preserved timestamp before configuration.
Node 24.18.1 ships an Android trap-handler patch whose context no longer
matches its bundled V8 revision, while the wrapper ignores `patch` failures.
The pinned replacement in `patches/` disables the unsupported V8 trap handler
and the build wrapper verifies the resulting header before compiling. A second
pinned patch switches zlib's Android arm64 feature detection to Bionic's
`getauxval(AT_HWCAP)`. This preserves CRC32/PMULL acceleration without the
otherwise unlinked NDK `android_getCpuFeatures` compatibility implementation.

Given clean, checksum-verified source and NDK directories:

```bash
export NODE_SOURCE_DIR=/path/to/node-v24.18.1
export ANDROID_NDK_ROOT=/path/to/android-ndk-r29
export JOBS=8
./runtime/node-android/build-node.sh
```

The produced `out/Release/node` is not a shippable runtime until it passes the
Android device smoke tests for ELF architecture, dynamic dependencies, 16 KiB
page compatibility, Node core APIs, subprocesses, loopback networking, workers, and
the pinned DSH dependency closure. Node dynamically links the matching NDK
`libc++_shared.so`; the runtime packager requires and ships that exact arm64
library beside the executable.

`runtime/package-runtime.sh` includes this smoke test in the payload and
`runtime/deploy-runtime.sh` executes it after first installation. To run it
again manually, use a writable Android temp directory:

```bash
TMPDIR=/data/local/tmp \
LD_LIBRARY_PATH=/data/adb/dsh-android/runtime/lib \
  /data/adb/dsh-android/runtime/bin/node \
  /data/adb/dsh-android/runtime/tests/node-smoke.mjs
```
