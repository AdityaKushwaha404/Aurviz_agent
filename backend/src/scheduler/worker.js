import { EMAIL_QUEUE } from "../services/email-queue.js";

export async function ensureEmailQueue(boss) {
  await boss.createQueue(EMAIL_QUEUE);
}

export async function startEmailWorker(boss, operations = {}) {
  await ensureEmailQueue(boss);
  const reconcileUnqueuedEmails = operations.reconcileUnqueuedEmails ??
    (await import("../services/email-service.js")).reconcileUnqueuedEmails;
  await reconcileUnqueuedEmails(boss);
  await boss.work(EMAIL_QUEUE, { batchSize: 1 }, async jobs => {
    const processEmailJob = operations.processEmailJob ??
      (await import("../services/email-service.js")).processEmailJob;
    for (const job of jobs) {
      await processEmailJob(boss, job);
    }
  });
}
