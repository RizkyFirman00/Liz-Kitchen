const test = require("node:test");
const assert = require("node:assert/strict");
const { isAutoCompletable } = require("./orderCompletion");

test("only completes eligible overdue orders with admin proof", () => {
  const order = {
    status: "Siap Diambil",
    statusProofs: { "Siap Diambil": "https://example.test/proof.jpg" },
    autoCompletionDeadlineAtMillis: 100,
    receiptProofUrl: "",
  };

  assert.equal(isAutoCompletable(order, 101), true);
  assert.equal(isAutoCompletable({ ...order, status: "Sedang Dikirim", statusProofs: { "Sedang Dikirim": "proof.jpg" } }, 101), true);
  assert.equal(isAutoCompletable(order, 99), false);
  assert.equal(isAutoCompletable({ ...order, receiptProofUrl: "manual.jpg" }, 101), false);
  assert.equal(isAutoCompletable({ ...order, statusProofs: {} }, 101), false);
  assert.equal(isAutoCompletable({ ...order, status: "Sedang Diproses" }, 101), false);
});
