const ELIGIBLE_STATUSES = new Set(["Sudah Diantar", "Siap Diambil"]);
const THREE_DAYS_MILLIS = 3 * 24 * 60 * 60 * 1000;

function hasRequiredStatusProof(order) {
  return Boolean(order.statusProofs?.[order.status]);
}

function isAutoCompletable(order, now = Date.now()) {
  return ELIGIBLE_STATUSES.has(order.status) &&
    hasRequiredStatusProof(order) &&
    !order.receiptProofUrl &&
    Number(order.autoCompletionDeadlineAtMillis) > 0 &&
    Number(order.autoCompletionDeadlineAtMillis) <= now;
}

module.exports = { ELIGIBLE_STATUSES, THREE_DAYS_MILLIS, hasRequiredStatusProof, isAutoCompletable };
