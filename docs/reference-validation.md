# Reference-device validation

Validated on 2026-08-19. This record describes the development MVP, not a
signed public release.

## Device and runtime

- Device: Redmi K90 (`2510DRK44C`, `annibale`), arm64-v8a only
- OS: LineageOS 23.2 / Android 16 / API 36
- Root: Magisk 30.7; DSH Host verified as uid/gid 0
- SELinux: Enforcing
- App package: `com.smartaodi.dshandroid`; the initial validation UID was 10169
  and changed to 10171 after uninstall/reinstall, so tooling must discover it
  live rather than persist it
- Node.js: 24.18.1, Android/Bionic arm64, NDK r29, API 28 target
- DeepSeek Harness: `0.1.0-rc.7`
- Android plugin: `0.2.0`
- Installed runtime size: approximately 404 MiB

Development artifact checksums:

```text
af4729c0770da8c6aaad6bdcc70d2ef389ac9e8b849b44abcdaeb9d2612720c0  node-v24.18.1-android-arm64
0c52cfab2df0d957d8b346a2bdc5ae8d71feca2591924d77e1cd724d5bf74352  libc++_shared.so
56f1a3e64322245d4e9444f399e07d6222db58de0e25262d8a6c4d1366d9e89f  dsh-android-arm64.tar.gz
0fc58d0e09861bdd25630251986d8e0a1c122abbf69ed75225a6fd0948ab66bc  app-debug.apk
```

## Passed checks

- The Node ELF uses `/system/bin/linker64`, has 16 KiB-compatible load
  alignment, and has no unresolved `android_getCpuFeatures` dependency.
- On-device Node smoke passed filesystem I/O, `/system/bin/sh` subprocesses,
  Worker threads, `node:sqlite`, Zstd, and loopback `fetch`.
- The typed Android plugin loaded under Bionic and its `device_info` invocation
  returned uid 0, Android 16/API 36, arm64-v8a, the device model, and Enforcing.
- The official Host bound only `127.0.0.1:3080`; ordinary ADB-shell access was
  rejected while the app UID completed RPC calls.
- `host.describe`, session create/list/history/prompt, workspace list,
  provider/model discovery, and both event WebSockets worked from the APK UID.
- Credential storage passed a temporary set/describe/unset cycle. Responses
  never returned the value, the file was mode 0600, and the temporary value was
  absent after unset.
- The native status Remote resolved the existing credential inside the Host,
  called DeepSeek's official balance endpoint, and returned only a CNY total.
  The balance response did not contain the API key. The temporary root-only
  probe firewall rule was removed after the check.
- Start is idempotent; stop removed both the listener and the exact firewall
  rule; restart restored one rule and the APK reconnected.
- After Android reassigned the APK from UID 10169 to 10171, the lifecycle
  controller removed the stale tagged firewall rule, rebound the current UID,
  and restored APK-to-Host connectivity.
- A real request header used the `android` preset, contained the short Android
  persona and `android_system`/shell/file tools, and contained no inherited Web
  GUI, HMR, or named desktop-platform guidance.
- The no-key prompt was accepted and persisted through the event/history path,
  then ended with the expected `MISSING_CREDENTIAL` error without making a
  model request.
- After owner-authorized credential import, a real request was routed to
  `deepseek-official` / `deepseek-v4-flash` with `high` reasoning and completed
  in one step with the expected `V4 Flash on Android OK` response. The measured
  turn used 2,913 uncached input tokens and 36 output tokens; time to first token
  was 753 ms and total LLM time was 1,040 ms.
- A second real turn required `android_system.device_info`. V4 Flash emitted the
  typed tool call, the root plugin returned the live K90 facts, and the model
  correctly summarized `2510DRK44C`, Android 16/API 36, arm64-v8a, uid 0, and
  SELinux Enforcing. The two-turn session totaled 3,219 uncached input, 5,760
  cache-read, and 163 output tokens; typed-tool execution took 138 ms.
- The 26-call system-log history replayed as one collapsed turn activity, with
  every call/result pair available through two-level drill-down. Its final GFM
  response rendered headings, emphasis, lists, inline code, links, code blocks,
  and a phone-width two-column table without exposing Markdown markers.
- V4 Flash emitted a real `ask_user_question` call and the native client
  received the pending interaction through the official question protocol.
  Unit tests cover both answer and cancellation envelopes for `/api/respond`.
- The Android preset exposed the official goal, Plan Mode, spawn/fork subagent,
  list-agents, workflow, and Ralph tools in a real Host request header without
  reintroducing desktop prompt text.
- The generated `commands/execute` Remote accepted `/plan` and `/plan off` on
  the K90. `session.selectModel` accepted V4 Pro/Low and restored V4 Flash/High;
  the native UI consumes the same directory and selection contracts.
- The K90's pinned rc.7 Host returned the live `commands/list` value as a bare
  descriptor array and `workspace.list` as `{items, archivedSessionIds}`. The
  native feature codecs were checked against those final `/api` responses rather
  than only against the generated Remote-layer declarations.
- Queue/Steer, history pagination, Workspace/session management, slash-command
  discovery, Job snapshots, and reasoning rendering are isolated native Feature
  Modules. The upgraded debug APK preserved package UID 10171, sessions, runtime,
  loopback firewall ownership, and disabled auto-rotation.
- `./gradlew lint test assembleDebug assembleRelease`, the plugin invocation tests, the
  real-Host profile smoke test, shell syntax checks, and `git diff --check`
  passed.

## Remaining validation

- A final unlocked-device visual pass remains for the new interaction, Plan,
  and model controls. PTY terminals, image attachments, signed runtime updates,
  and durable audit UI are not implemented.
- Magisk on the K90 is the only validated root/ROM combination. KernelSU,
  APatch, other Android 15/16 ROMs, and the provisional API 28 floor remain
  compatibility targets rather than verified support claims.

## Recovery

The app's **停止 Host** control is the normal emergency stop. From an already
authorized ADB shell, the equivalent is:

First discover the current UID with
`pm list packages -U com.smartaodi.dshandroid`, then run:

```sh
su -c '/data/adb/dsh-android/runtime/control-android.sh stop <current-app-uid>'
```

Only after the Host is stopped, the development runtime can be removed by
deleting the exact `/data/adb/dsh-android` tree. Uninstalling the APK alone does
not delete this root-owned runtime or its credential/session state.
