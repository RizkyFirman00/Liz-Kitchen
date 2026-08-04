const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const {
  ELIGIBLE_STATUSES,
  THREE_DAYS_MILLIS,
  hasRequiredStatusProof,
  isAutoCompletable,
} = require("./orderCompletion");

initializeApp();
const db = getFirestore();
const REGION = "asia-southeast2";

exports.startAutoCompletionWindow = onDocumentUpdated(
  { document: "orders/{orderId}", region: REGION },
  async (event) => {
    const before = event.data.before.data();
    const order = event.data.after.data();
    if (!ELIGIBLE_STATUSES.has(order.status) || !hasRequiredStatusProof(order)) return;
    if (before.status === order.status && Number(order.autoCompletionDeadlineAtMillis) > 0) return;

    const startedAt = Date.now();
    const updates = {
      fulfillmentStartedAtMillis: startedAt,
      autoCompletionDeadlineAtMillis: startedAt + THREE_DAYS_MILLIS,
    };
    const batch = db.batch();
    batch.set(event.data.after.ref, updates, { merge: true });
    const userId = order.user?.userId;
    if (userId) {
      batch.set(db.doc(`users/${userId}/orders/${event.params.orderId}`), updates, { merge: true });
    }
    await batch.commit();
  }
);

exports.completeOverdueOrders = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Asia/Jakarta", region: REGION },
  async () => {
    const now = Date.now();
    const overdue = await db.collection("orders")
      .where("autoCompletionDeadlineAtMillis", "<=", now)
      .limit(200)
      .get();

    await Promise.all(overdue.docs.map((snapshot) => db.runTransaction(async (transaction) => {
      const currentSnapshot = await transaction.get(snapshot.ref);
      const order = currentSnapshot.data();
      if (!order || !isAutoCompletable(order, now)) return;

      const updates = {
        status: "Selesai",
        completionType: "AUTO_SYSTEM",
        completionLabel: "Diterima Otomatis oleh Sistem",
        autoCompletedAtMillis: now,
      };
      transaction.set(snapshot.ref, updates, { merge: true });
      const userId = order.user?.userId;
      if (userId) {
        transaction.set(db.doc(`users/${userId}/orders/${snapshot.id}`), updates, { merge: true });
      }
    })));
  }
);
