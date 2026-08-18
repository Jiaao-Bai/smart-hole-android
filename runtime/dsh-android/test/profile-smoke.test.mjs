import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { cp, mkdir, mkdtemp, rm } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const runtimeDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");

async function freePort() {
  const server = createServer();
  await new Promise((resolveListen, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolveListen);
  });
  const { port } = server.address();
  await new Promise((resolveClose, reject) => server.close(error => error ? reject(error) : resolveClose()));
  return port;
}

async function rpc(port, method, payload = {}) {
  const rpcId = `test-${method}-${crypto.randomUUID()}`;
  const response = await fetch(`http://127.0.0.1:${port}/api/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ type: "client-request", rpcId, method, payload }),
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.rpcId, rpcId);
  assert.equal(body.result.ok, true, JSON.stringify(body.result));
  return body.result.value;
}

test("Android profile is the default and emits no desktop prompt", { timeout: 20_000 }, async () => {
  const root = await mkdtemp(join(tmpdir(), "dsh-android-profile-test-"));
  const home = join(root, "home");
  const profile = join(home, "profiles", "android");
  const workspace = join(root, "workspace");
  const port = await freePort();
  let child;
  try {
    await mkdir(join(profile, "node_modules"), { recursive: true });
    await mkdir(join(home, ".agent-presets"), { recursive: true });
    await mkdir(workspace, { recursive: true });
    await cp(join(runtimeDir, "profile", "package.json"), join(profile, "package.json"));
    await cp(join(runtimeDir, "profile", "cordis.patch.yml"), join(profile, "cordis.patch.yml"));
    await cp(join(runtimeDir, "profile", "agent-presets", "android"), join(home, ".agent-presets", "android"), { recursive: true });
    await cp(join(runtimeDir, "plugin-android"), join(profile, "node_modules", "dsh-plugin-android"), { recursive: true });

    child = spawn(process.execPath, [
      "--expose-internals",
      join(runtimeDir, "node_modules", "@deepseek-ai", "dsh", "lib", "bin.js"),
      "--profile", "android", "--host", "127.0.0.1", "--port", String(port),
    ], {
      cwd: workspace,
      env: {
        ...process.env,
        DSH_HOME: home,
        DSH_ANDROID_WORKSPACE: workspace,
        DSH_PERMISSION_MODE: "danger-full-access",
        DSH_TELEMETRY_DISABLED: "1",
      },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let diagnostics = "";
    child.stderr.on("data", chunk => { diagnostics += chunk; });
    await new Promise((resolveReady, reject) => {
      child.once("exit", code => reject(new Error(`Host exited ${code}: ${diagnostics}`)));
      child.stdout.on("data", chunk => {
        diagnostics += chunk;
        if (diagnostics.includes(`127.0.0.1:${port}`)) resolveReady();
      });
    });

    const created = await rpc(port, "session.create");
    assert.equal(created.agentPreset, "android");
    await rpc(port, "session.prompt", {
      sessionId: created.sessionId,
      mode: "queue",
      clientTimeZone: "UTC",
      content: [{ type: "text", text: "profile audit" }],
    });

    let header;
    for (let attempt = 0; attempt < 30 && !header; attempt += 1) {
      const history = await rpc(port, "session.history", {
        sessionId: created.sessionId,
        maxMessages: 100,
      });
      header = history.events
        .map(item => item.event)
        .find(event => event.type === "request/header")
        ?.data?.header;
      if (!header) await new Promise(resolveWait => setTimeout(resolveWait, 100));
    }
    assert.ok(header, "request/header was not persisted");
    assert.match(header.system, /^You are DeepSeek Harness for Android,/);
    for (const forbidden of ["Web GUI", "HMR", "macOS", "Windows", "systemd", "Homebrew", "apt"]) {
      assert.equal(header.system.includes(forbidden), false, `desktop prompt leaked: ${forbidden}`);
    }
    const tools = new Set(header.tools.map(tool => tool.name));
    for (const required of [
      "android_system", "ask_user_question", "bash", "read", "write", "edit", "glob", "grep",
      "get_goal", "create_goal", "update_goal", "exit_plan_mode",
      "subagent", "subagent_fork", "list_agents", "workflow", "ralph",
    ]) {
      assert.equal(tools.has(required), true, `missing Android profile tool: ${required}`);
    }
  } finally {
    child?.kill("SIGTERM");
    await rm(root, { recursive: true, force: true });
  }
});
