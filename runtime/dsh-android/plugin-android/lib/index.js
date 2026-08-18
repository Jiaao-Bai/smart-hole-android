import { execFile } from "node:child_process";
import { credentialRef } from "@deepseek-ai/dsh-credentials";
import { defineTool } from "@deepseek-ai/dsh-tools";
import { Remote, TypertRemoteService } from "@deepseek-ai/dsh-typert-protocol";

export const name = "android-system-tools";
export const inject = ["tools", "systemPrompt", "credentials"];

const DEEPSEEK_API_KEY = credentialRef("DEEPSEEK_API_KEY");
const DEEPSEEK_BALANCE_URL = "https://api.deepseek.com/user/balance";
const BALANCE_CACHE_MS = 60_000;

const OPERATIONS = [
  "device_info",
  "list_packages",
  "package_info",
  "start_component",
  "force_stop",
  "get_setting",
  "put_setting",
  "list_services",
];

const NAMESPACES = ["system", "secure", "global"];

function requireText(args, key) {
  const value = args[key];
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${args.operation} requires a non-empty ${key}`);
  }
  return value.trim();
}

export function buildInvocation(args) {
  switch (args.operation) {
    case "device_info":
      return {
        command: "/system/bin/sh",
        argv: [
          "-c",
          "printf 'uid='; id -u; printf 'android='; getprop ro.build.version.release; printf 'api='; getprop ro.build.version.sdk; printf 'abi='; getprop ro.product.cpu.abi; printf 'model='; getprop ro.product.model; printf 'selinux='; getenforce",
        ],
      };
    case "list_packages": {
      const argv = ["list", "packages"];
      if (args.target?.trim()) argv.push(args.target.trim());
      return { command: "/system/bin/pm", argv };
    }
    case "package_info":
      return { command: "/system/bin/dumpsys", argv: ["package", requireText(args, "target")] };
    case "start_component":
      return { command: "/system/bin/am", argv: ["start", "-n", requireText(args, "target")] };
    case "force_stop":
      return { command: "/system/bin/am", argv: ["force-stop", requireText(args, "target")] };
    case "get_setting":
      return {
        command: "/system/bin/settings",
        argv: ["get", requireNamespace(args.namespace), requireText(args, "target")],
      };
    case "put_setting":
      return {
        command: "/system/bin/settings",
        argv: [
          "put",
          requireNamespace(args.namespace),
          requireText(args, "target"),
          requireText(args, "value"),
        ],
      };
    case "list_services":
      return { command: "/system/bin/service", argv: ["list"] };
    default:
      throw new Error(`unsupported Android operation: ${JSON.stringify(args.operation)}`);
  }
}

function requireNamespace(value) {
  if (!NAMESPACES.includes(value)) {
    throw new Error(`namespace must be one of: ${NAMESPACES.join(", ")}`);
  }
  return value;
}

function run(invocation, signal) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      invocation.command,
      invocation.argv,
      {
        encoding: "utf8",
        maxBuffer: 256 * 1024,
        timeout: 30_000,
        signal,
      },
      (error, stdout, stderr) => {
        const exitCode = typeof error?.code === "number" ? error.code : error ? -1 : 0;
        resolve({
          command: invocation.command,
          argv: invocation.argv,
          exitCode,
          stdout: stdout.trimEnd(),
          stderr: stderr.trimEnd(),
        });
      },
    );
    child.once("error", reject);
  });
}

export function parseDeepSeekBalance(payload, checkedAt = Date.now()) {
  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error("DeepSeek balance response is not an object");
  }
  const balances = Array.isArray(payload.balance_infos)
    ? payload.balance_infos.flatMap((entry) => {
      if (entry === null || typeof entry !== "object" || Array.isArray(entry)) return [];
      const currency = entry.currency;
      const total = entry.total_balance;
      if ((currency !== "CNY" && currency !== "USD") || typeof total !== "string") return [];
      return [{ currency, total }];
    })
    : [];
  if (typeof payload.is_available !== "boolean" || balances.length === 0) {
    throw new Error("DeepSeek balance response is incomplete");
  }
  return {
    status: "available",
    isAvailable: payload.is_available,
    balances,
    checkedAt,
  };
}

const statusInitializers = [];

class SmartHoleStatusService extends TypertRemoteService {
  constructor(ctx) {
    super(ctx, "smartHoleStatus");
    for (const initialize of statusInitializers) initialize.call(this);
    this.cachedBalance = undefined;
  }

  async balance(signal) {
    const now = Date.now();
    if (this.cachedBalance !== undefined && now - this.cachedBalance.checkedAt < BALANCE_CACHE_MS) {
      return this.cachedBalance;
    }
    const credential = await this.ctx.credentials.resolve(DEEPSEEK_API_KEY);
    if (credential === undefined) {
      return { status: "unconfigured", isAvailable: false, balances: [], checkedAt: now };
    }
    const timeout = AbortSignal.timeout(10_000);
    const response = await fetch(DEEPSEEK_BALANCE_URL, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${credential.value}`,
      },
      signal: AbortSignal.any([signal, timeout]),
    });
    if (!response.ok) {
      throw new Error(`DeepSeek balance request failed with HTTP ${response.status}`);
    }
    const parsed = parseDeepSeekBalance(await response.json(), now);
    this.cachedBalance = parsed;
    return parsed;
  }
}

Remote("balance")(SmartHoleStatusService.prototype.balance, {
  kind: "method",
  name: "balance",
  static: false,
  private: false,
  addInitializer(initializer) {
    statusInitializers.push(initializer);
  },
});

export function apply(ctx) {
  new SmartHoleStatusService(ctx);
  ctx.systemPrompt.section({
    name: "tool:android-system",
    order: 106,
    text: "Use android_system for common package, component, settings, service, and device operations; use bash for Android operations outside this typed surface.",
  });
  ctx.tools.register(defineTool({
    name: "android_system",
    description: "Run a typed Android system operation as the local Harness uid (root in the Android profile). force_stop, start_component, and put_setting change device state; the other operations are read-only.",
    parameters: {
      operation: {
        type: "string",
        required: true,
        enum: OPERATIONS,
        description: "Android operation to perform.",
      },
      target: {
        type: "string",
        description: "Package/component name, settings key, or optional package-list filter.",
      },
      namespace: {
        type: "string",
        enum: NAMESPACES,
        description: "Settings namespace for get_setting or put_setting.",
      },
      value: {
        type: "string",
        description: "New settings value for put_setting.",
      },
    },
    output: {
      schema: {
        type: "object",
        additionalProperties: false,
        properties: {
          command: { type: "string", required: true },
          argv: { type: "array", required: true, items: { type: "string" } },
          exitCode: { type: "integer", required: true },
          stdout: { type: "string", required: true },
          stderr: { type: "string", required: true },
        },
      },
      render: (_args, result) => [{
        type: "text",
        text: [
          result.stdout || "(no output)",
          result.stderr ? `[stderr]\n${result.stderr}` : "",
          result.exitCode === 0 ? "" : `[exit code: ${result.exitCode}]`,
        ].filter(Boolean).join("\n"),
      }],
    },
    execute(args, exec) {
      return run(buildInvocation(args), exec.signal);
    },
    presentCall: (args) => ({
      card: "generic",
      title: `Android: ${args.operation}`,
      kind: "execute",
      rawInput: args,
    }),
  }));
}
