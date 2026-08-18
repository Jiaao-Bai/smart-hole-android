# Architecture

## Process boundary

```text
Jetpack Compose UI
        |
        | official JSON RPC over HTTP + event downlinks over WebSocket
        v
Official DSH Host on 127.0.0.1
        |
        | uid 0, Android/Bionic Node.js + dsh-plugin-android
        v
Android filesystem, apps, framework bridge and system services
```

The Android app never renders the upstream Web UI. It consumes the same Host
surface contract used by that UI. DSH plugins remain the owners of domain
projections and tool presentation; Compose maps their provider-neutral wire
views into native components instead of branching on tool names.

## Native feature layers

The app keeps four boundaries explicit:

```text
DSH JSON wire -> protocol models -> feature modules -> Compose components
                         |                |
                 generic snapshots   typed product state
```

- `protocol` preserves generic events, projections, and plugin presentations;
  it does not own Goal, Todo, subagent, or other product semantics.
- `features` contains small decoders and pure reducers. The core registry
  currently composes work state (Goal/Todo/Plan), session metrics, and the
  recursive subagent tree. Queue, history, Workspace, commands, and Jobs each
  own a separate typed contract and state reducer.
- `HarnessViewModel` owns I/O and lifecycle only. Live conversation replay is
  delegated to a pure reducer covered by fixture tests.
- `ui` renders typed feature state. It must not branch on DSH tool names or
  parse projection JSON.

Adding a native DSH capability means adding a feature module and a focused UI
component. Unknown projection keys remain in the protocol snapshot and are
ignored safely. This is a curated native compatibility layer, not a promise to
render arbitrary third-party plugin UI.

The feature boundary is deliberately symmetrical:

```text
features/<capability>/     wire decoder + immutable state + pure policy
ui/features/<capability>/ native Compose surface
HarnessViewModel          transport/lifecycle orchestration only
```

Queue code therefore does not know how Workspace rows render; Workspace code
does not inspect conversation events; the composer consumes a command directory
without hard-coding command names. A new DSH plugin capability can be added or
removed by mounting one feature pair rather than editing a central tool-name
switch.

## Platform scope

Runtime decisions are capability-driven. Production code must not branch on
`annibale`, Redmi, Xiaomi, or LineageOS. The first support tier is rooted arm64
Android 15/16 across AOSP-derived and OEM ROMs, with Magisk, KernelSU, and APatch
as root-provider families. K90 is a reference device, not a required platform.
Android 16 / API 36 is the current stable acceptance line. Android 17 / API 37
is compiled against and exercised as a preview target, but preview-only behavior
must not become a runtime requirement for stable devices.

Provider adapters own installation, daemon lifecycle, and policy discovery.
The agent-facing capability surface remains stable when the provider changes.
Every startup records Android API level, ABI, root provider, SELinux state, and
the result of functional capability probes; a provider name alone never proves
that an operation works.

## Root execution boundary

Root is the current Android execution foundation, not Smart Hole's product
objective or identity. The APK asks the device's `su` provider for root to:

1. verify, install, start, stop, and recover the pinned runtime;
2. run the DSH Host as uid 0;
3. let typed Android tools access root-owned files and system command surfaces;
4. bridge framework-only operations through the native APK;
5. report health, audit events, and logs.

The primary integration is a first-party Cordis plugin that registers typed
Android tools. Its current operations cover device facts, package inspection,
component launch/force-stop, Android settings, and Binder service inventory.
The standard local filesystem and raw shell providers run as root in DSH
`danger-full-access` mode; typed tools exist for reliability and inspectable UI,
not as an allowlist that removes root power. Native Binder/framework calls and
broader app-ops/intents are follow-up capability families.

The Android app provides a visible Host stop control, a live event trace, and
native cards for the official approval and user-question protocols; durable
audit storage is still planned. The Android profile is an owner-selected
full-root session without a filesystem sandbox, and its default `never`
approval policy does not insert a prompt before every tool. Plugins and modes
that explicitly request an answer can still use the native interaction cards.
Android SELinux,
hardware-backed keystore boundaries, lock state, and kernel capabilities remain
real platform limits even for uid 0.

Android apps share one loopback network namespace, so `127.0.0.1` is not an
authentication boundary by itself. The root lifecycle controller refuses to
start the Host unless it can install an iptables owner rule that permits TCP
port 3080 only for the native APK UID. It removes that exact rule after a
verified Host shutdown. Because Android can assign a new UID after an APK is
uninstalled and reinstalled, every lifecycle action first removes stale port
3080 rules carrying the runtime's unique comment before installing the rule for
the current UID. The Host remains loopback-only; LAN binding is outside the
supported security model.

## Native runtime strategy

The preferred runtime is Node.js built for Android arm64 and Bionic, with a
relocatable prefix under `/data/adb/dsh-android/runtime`. A conventional Linux
rootfs, chroot, VM, or proot is not part of the default architecture.

Keep the official `@deepseek-ai/dsh` package pinned and unmodified wherever it
runs on Android. Add `dsh-plugin-android` through the normal Cordis configuration.
Carry a small fork only when upstream assumes unsupported platform details such
as `process.platform`, PTY prebuild names, shell paths, or Linux-only sandbox
runners. Every fork patch must be isolated and covered by an upstream-compatibility
test so it can be removed when upstream gains Android support.

## Upstream compatibility

The APK and backend payload are released as one compatibility unit. The payload
pins an exact `@deepseek-ai/dsh` version. Upgrading DSH requires protocol fixture
tests before the new payload is offered.

The native boundary currently consumes:

- `host.describe`
- `workspace.list`, `workspace.rename`, `workspace.delete`,
  `workspace.archiveSession`
- `session.list`, `session.create`, `session.history`, `session.prompt`,
  `session.updateQueue`, `session.cancel`, `session.rename`,
  `session.fork`, `session.models`, `session.selectModel`
- the Smart Hole Host plugin's `smartHoleStatus/balance` Remote, which resolves
  the DSH credential inside the Host and returns sanitized DeepSeek totals only
- generated command remotes such as `commands/list` and `commands/execute`
- `/api/events.host` and `/api/events.mux` WebSocket downlinks
- `/api/respond` for approvals and questions
- `HistoryEntry.view` and live mux `view`, produced by plugin
  `presentCall`/`presentResult` callbacks
- history/list projection baselines and live `session/projection` frames, merged
  per key under higher-sequence-wins
- complete live `session/queue` and `session/jobs` snapshots

Unknown event and tool-card types must degrade to a generic JSON view rather
than crashing the native client. The adapter retains the raw view while native
renderers recognize the official `generic`, `terminal`, `diff`, `search`,
`read`, and `web` cards. The app checks the Host wire version separately from
the DSH package version recorded in the installed runtime manifest, and rejects
either when it is outside this build's exact compatibility unit. An upgrade
must update fixtures, the adapter, and the payload as one release.

Subagent sessions are retained from `session.list` instead of being hidden.
Their `parentSessionId`, `subagent`, `subagentTiming`, `tokenUsage`, and running
state are composed into a recursive native tree; the regular conversation
drawer continues to list root sessions only.

## Android model profile

Do not send the upstream Web deployment persona to the model in the native app.
The Host still reuses the official Web bundle for its loopback protocol carrier,
but native sessions always select a packaged `android` agent preset whose
`complete` persona suppresses all Web/desktop prompt contributors. Compose that
preset from the shared agent core:

- retain the short Harness identity and general agent behavior;
- identify the host as rooted Android/Bionic, its shell path, workspace, and
  root mode; use `android_system.device_info` for live version, ABI, and SELinux
  facts rather than baking one device's values into the prompt;
- disable Web GUI, HMR, macOS, Windows, PowerShell, and desktop-only guidance;
- replace Linux-only sandbox guidance with the actual Android root policy;
- expose only tools mounted in the Android profile.

Tool schemas are part of each model request even when their prose is not in the
system-prompt text. Avoid one micro-tool per Android command. Start with a small
set of orthogonal, typed capability families and use discriminated operations
inside each family. Keep the raw root shell as the escape hatch. The profile
smoke test boots the real pinned Host, creates a default session, captures its
persisted `request/header`, and fails if the assembled Android prompt contains
Web/HMR or named desktop-platform guidance. It also asserts the native system,
shell, and file tools remain mounted. Prompt and tool-schema token budgets should
be tracked as the preset grows.
