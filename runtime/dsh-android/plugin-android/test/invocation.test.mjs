import assert from "node:assert/strict";
import test from "node:test";
import { buildInvocation, parseDeepSeekBalance } from "../lib/index.js";

test("maps package inspection without a shell", () => {
  assert.deepEqual(buildInvocation({ operation: "package_info", target: "com.example.app" }), {
    command: "/system/bin/dumpsys",
    argv: ["package", "com.example.app"],
  });
});

test("maps settings writes to positional arguments", () => {
  assert.deepEqual(
    buildInvocation({ operation: "put_setting", namespace: "global", target: "demo", value: "a b" }),
    {
      command: "/system/bin/settings",
      argv: ["put", "global", "demo", "a b"],
    },
  );
});

test("rejects incomplete operations", () => {
  assert.throws(
    () => buildInvocation({ operation: "start_component", target: "" }),
    /non-empty target/,
  );
  assert.throws(
    () => buildInvocation({ operation: "get_setting", namespace: "private", target: "x" }),
    /namespace must be one of/,
  );
});

test("sanitizes the DeepSeek balance response", () => {
  assert.deepEqual(
    parseDeepSeekBalance({
      is_available: true,
      balance_infos: [{
        currency: "CNY",
        total_balance: "12.34",
        topped_up_balance: "10.00",
        granted_balance: "2.34",
      }],
    }, 123),
    {
      status: "available",
      isAvailable: true,
      balances: [{ currency: "CNY", total: "12.34" }],
      checkedAt: 123,
    },
  );
});

test("rejects incomplete balance payloads", () => {
  assert.throws(() => parseDeepSeekBalance({ is_available: true }), /incomplete/);
});
