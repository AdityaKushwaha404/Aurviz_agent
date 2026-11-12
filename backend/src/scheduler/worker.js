import { EMAIL_QUEUE, processEmailJob, reconcileUnqueuedEmails } from "../services/email-service.js";

export async function startEmailWorker(boss) {
  await reconcileUnqueuedEmails(boss);
  await boss.work(EMAIL_QUEUE, { batchSize: 1 }, async jobs => {
    for (const job of jobs) {
      await processEmailJob(boss, job);
    }
  });
}
