import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { buildInvocation } from "../lib/index.js";

if (process.platform !== "android") {
  throw new Error(`device smoke test requires Android, got ${process.platform}`);
}

const invocation = buildInvocation({ operation: "device_info" });
const output = execFileSync(invocation.command, invocation.argv, { encoding: "utf8" });

assert.match(output, /^uid=0$/m);
assert.match(output, /^android=\d+/m);
assert.match(output, /^api=\d+/m);
assert.match(output, /^abi=arm64-v8a$/m);
assert.match(output, /^selinux=Enforcing$/m);

console.log(output.trim());
