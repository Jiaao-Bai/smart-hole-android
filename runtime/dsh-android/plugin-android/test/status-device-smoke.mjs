import assert from "node:assert/strict";

if (process.platform !== "android") {
  throw new Error(`device smoke test requires Android, got ${process.platform}`);
}

// The caller must run under the APK uid or install a temporary root-only
// loopback allowance. Reaching the endpoint also validates that boundary.
const request = {
  type: "client-request",
  rpcId: "status-device-smoke",
  method: "smartHoleStatus/balance",
  payload: { args: {} },
};
const response = await fetch("http://127.0.0.1:3080/api/smartHoleStatus/balance", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify(request),
});
assert.equal(response.status, 200);
const envelope = await response.json();
assert.equal(envelope.type, "server-response");
assert.equal(envelope.rpcId, request.rpcId);
assert.equal(envelope.result?.ok, true);
assert.equal(envelope.result?.value?.status, "available");
assert.ok(Array.isArray(envelope.result.value.balances));
assert.ok(envelope.result.value.balances.length > 0);
console.log(JSON.stringify(envelope.result.value));
