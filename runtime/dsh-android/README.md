# Android DSH runtime closure

This directory pins `@deepseek-ai/dsh` `0.1.0-rc.7` and prepares its npm
closure for a native Android/Bionic Node.js process. Run:

```bash
./prepare-runtime.sh
```

The preparation is deliberately reproducible: `npm ci --ignore-scripts
--omit=optional` starts from the lock file, then applies three narrow patches:

- load `node-pty` only if a PTY terminal is requested;
- use `/system/bin/sh` for local shell execution on `process.platform ===
  "android"`;
- load Sharp only if an image attachment is used.

The Android profile disables the desktop sandbox and fixed-mode permission
switcher, then mounts the ordinary local shell and filesystem providers in
`danger-full-access` mode. Text/code sessions and raw root shell are supported.
PTY terminals and image attachments remain unsupported until Android-native
providers replace `node-pty` and Sharp.

The packaged `android` agent preset is the default for every native session. It
uses a complete Android/Bionic persona so the official Web carrier cannot append
desktop GUI or HMR guidance to model requests. Run the real-Host regression test
on a development machine with:

```bash
node --test test/profile-smoke.test.mjs
```

`plugin-android/` contributes the compact typed `android_system` tool and the
native `smartHoleStatus/balance` Remote. Its initial operations cover device
facts, packages, components, Android settings, and Binder service inventory.
The status Remote resolves the existing DSH credential inside the Host and
returns sanitized DeepSeek balance totals without returning the API key to the APK.
Model-provided values are passed as positional arguments to fixed Android
commands rather than interpolated into a shell.

`control-android.sh` is the root lifecycle boundary. It starts or stops the
Host and installs an iptables owner rule that permits port 3080 only for the
native APK UID. This is required because Android apps share the loopback
network namespace; binding to `127.0.0.1` alone is not an authentication
boundary.
