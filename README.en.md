<p align="center">
  <img src="docs/assets/smart-hole-avatar-badge.png" width="144" alt="Smart Hole avatar badge">
</p>

# Smart Hole

[简体中文](README.md) | English

**A native agent for smartphones.**

*Powered by [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness).*

Smart Hole is built on DeepSeek Harness and will continue to support the full
DSH feature set. Rooted Android is currently supported.

## Highlights

- Native Jetpack Compose UI, not a WebView wrapper.
- Official DSH agent, session, model, tool, and plugin runtime.
- Android/Bionic arm64 Node.js without a Linux VM, rootfs, chroot, or proot.
- A local uid 0 Host with Android file, app, settings, and system capabilities.
- Official JSON RPC, WebSocket, questions, approvals, goals, todos, Plan Mode,
  and subagents.
- Native Queue/Steer, history pagination, slash-command discovery, collapsible Reasoning,
  and background Job state.
- Native Markdown, per-turn tool traces, Workspace grouping, session rename/fork/archive,
  and model selection.
- Layered native feature modules; the subagent tree exposes status, duration,
  token usage, and navigation into each child session.
- Native composer status for DSH context pressure and DeepSeek API balance; the
  balance query never returns the API key to the app.
- Global and directory-scoped `AGENTS.md`/`CLAUDE.md` workspace instructions.
- Direct consumption of plugin-owned DSH tool presentations and session projections,
  with safe fallback for unknown cards.
- No dependency on GApps, Google Play services, or a vendor-specific ROM.

## Architecture

```text
Native Compose app (normal Android UID)
        |
        | JSON RPC + WebSocket on protected loopback
        v
Official DSH Host (uid 0, Android/Bionic Node.js)
        |
        +-- standard DSH tools and plugins
        +-- dsh-plugin-android
        v
Android files, apps, settings, commands, and system services
```

## Support

| Tier | Android / device | Root | Status |
| --- | --- | --- | --- |
| Validated | Android 16 / API 36, Redmi K90, LineageOS 23.2 | Magisk 30.7 | Reference acceptance passed |
| Compatibility target | Android 15–16, arm64-v8a, AOSP-derived and OEM ROMs | Magisk, KernelSU, APatch | Validation pending |
| Compile target | Android 17 / API 37 | — | Compiles; runtime not yet validated |
| Technical floor | Android 9 / API 28 | — | APK and Node build floor only |

32-bit, x86, and unrooted devices are not currently supported. The K90 is a
reference device, not a runtime requirement.

## Current gaps

- Signed runtime installer and update channel.
- More phones, ROMs, KernelSU, and APatch validation.
- PTY terminal and file/image attachments.
- Complete provider, model-catalog, and plugin-management UI.
- Session ZIP export, message feedback, and trajectory inspection UI.
- Complete management interactions for goals, todos, and subagents, plus native
  surfaces for Workflow, Ralph, and other core semantics.
- Durable audit history and broader Android Framework tools.

See [reference-device validation](docs/reference-validation.md) and
[architecture](docs/architecture.md) for details.

## Security

The DSH Host runs as uid 0 in `danger-full-access` mode. It binds only to
`127.0.0.1`; an `iptables owner` rule restricts access to the current APK UID.
SELinux, the lock screen, hardware-backed keystore, and kernel limits still
apply.

The current profile does not prompt before every tool. Use it only on a device
where you accept a model-controlled root agent.

## Build the app

Requires JDK 17, Android SDK Platform 37, and Build Tools 36.0.0:

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
./gradlew lint test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build the runtime

```bash
./runtime/dsh-android/prepare-runtime.sh

NODE_LICENSE=/path/to/node-v24.18.1/LICENSE \
NODE_LIBCXX=/path/to/android-ndk-r29/.../aarch64-linux-android/libc++_shared.so \
  ./runtime/package-runtime.sh /path/to/android-arm64/node \
  runtime/dist/dsh-android-arm64.tar.gz

ANDROID_SERIAL=<serial> \
  ./runtime/deploy-runtime.sh runtime/dist/dsh-android-arm64.tar.gz
```

The runtime is installed under `/data/adb/dsh-android/`, which contains the
runtime, workspaces, sessions, credentials, and logs.

## Upstream notice

Smart Hole is an independent community project. It is not an official DeepSeek
product and is not endorsed by DeepSeek AI. DeepSeek Harness is licensed by its
upstream authors; distributions must preserve its license and third-party notices.
