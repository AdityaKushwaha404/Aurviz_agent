import { prisma } from "../database/prisma.js";
import { normalizeError } from "../utils/validation.js";
import { createGmailClient } from "./google.js";

export const EMAIL_QUEUE = "aurviz-send-email";

function sameRequest(email, accountId, input, ignoreScheduledAt) {
  return email.accountId === accountId &&
    email.recipient === input.recipient &&
    email.subject === input.subject &&
    email.body === input.body &&
    (ignoreScheduledAt || email.scheduledAt.getTime() === input.scheduledAt.getTime()) &&
    email.timeZone === input.timeZone;
}

async function enqueue(boss, email) {
  const jobId = await boss.sendAfter(
    EMAIL_QUEUE,
    { emailId: email.id, jobVersion: email.jobVersion },
    {
      retryLimit: 3,
      retryDelay: 30,
      retryBackoff: true,
      singletonKey: email.id,
      expireInSeconds: 300
    },
    email.scheduledAt
  );
  if (jobId) {
    await prisma.scheduledEmail.update({ where: { id: email.id }, data: { pgBossJobId: jobId } });
  }
  return jobId;
}

export async function scheduleEmail(boss, accountId, input, idempotencyKey, { ignoreScheduledAt = false } = {}) {
  const stableKey = `${accountId}:${idempotencyKey}`;
  const existing = await prisma.scheduledEmail.findUnique({ where: { idempotencyKey: stableKey } });
  if (existing) {
    if (!sameRequest(existing, accountId, input, ignoreScheduledAt)) {
      const error = new Error("This idempotency key was already used for a different email.");
      error.status = 409;
      throw error;
    }
    return existing;
  }

  const email = await prisma.scheduledEmail.create({
    data: {
      accountId,
      ...input,
      status: "SCHEDULED",
      jobVersion: 1,
      idempotencyKey: stableKey
    }
  });
  try {
    await enqueue(boss, email);
    return await prisma.scheduledEmail.findUnique({ where: { id: email.id }, include: { history: true } });
  } catch (error) {
    await prisma.scheduledEmail.update({
      where: { id: email.id },
      data: { status: "FAILED", lastError: "The durable job could not be queued." }
    });
    throw error;
  }
}

export async function listEmails(accountId) {
  return prisma.scheduledEmail.findMany({
    where: { accountId },
    include: { history: { orderBy: { createdAt: "desc" }, take: 10 } },
    orderBy: [{ scheduledAt: "asc" }, { createdAt: "desc" }]
  });
}

export async function getEmail(accountId, id) {
  return prisma.scheduledEmail.findFirst({
    where: { accountId, id },
    include: { history: { orderBy: { createdAt: "desc" } } }
  });
}

export async function cancelEmail(boss, accountId, id) {
  const email = await prisma.scheduledEmail.findFirst({ where: { accountId, id } });
  if (!email) return null;
  if (email.status !== "SCHEDULED") {
    const error = new Error("Only a scheduled email can be cancelled.");
    error.status = 409;
    throw error;
  }
  await prisma.$transaction(async tx => {
    const changed = await tx.scheduledEmail.updateMany({
      where: { id, accountId, status: "SCHEDULED" },
      data: { status: "CANCELLED" }
    });
    if (changed.count !== 1) {
      const error = new Error("The email is already being processed.");
      error.status = 409;
      throw error;
    }
    await tx.emailExecution.create({
      data: { scheduledEmailId: id, attempt: email.attempts + 1, status: "CANCELLED" }
    });
  });
  if (email.pgBossJobId) await boss.cancel(EMAIL_QUEUE, email.pgBossJobId);
  return getEmail(accountId, id);
}

export async function updateEmail(boss, accountId, id, changes) {
  const previous = await prisma.scheduledEmail.findFirst({ where: { accountId, id } });
  if (!previous) return null;
  if (previous.status !== "SCHEDULED") {
    const error = new Error("Only a scheduled email can be edited.");
    error.status = 409;
    throw error;
  }
  const nextValues = { ...previous, ...changes };
  const nextDate = nextValues.scheduledAt;
  if (nextDate <= new Date()) {
    const error = new Error("The updated send time must be in the future.");
    error.status = 400;
    throw error;
  }
  const updated = await prisma.$transaction(async tx => {
    const result = await tx.scheduledEmail.updateMany({
      where: { id, accountId, status: "SCHEDULED" },
      data: {
        recipient: changes.recipient ?? previous.recipient,
        subject: changes.subject ?? previous.subject,
        body: changes.body ?? previous.body,
        scheduledAt: changes.scheduledAt ?? previous.scheduledAt,
        timeZone: changes.timeZone ?? previous.timeZone,
        pgBossJobId: null,
        jobVersion: { increment: 1 }
      }
    });
    if (result.count !== 1) {
      const error = new Error("The email is already being processed.");
      error.status = 409;
      throw error;
    }
    return tx.scheduledEmail.findUnique({ where: { id } });
  });

  if (previous.pgBossJobId) await boss.cancel(EMAIL_QUEUE, previous.pgBossJobId);
  try {
    await enqueue(boss, updated);
  } catch (error) {
    await prisma.scheduledEmail.update({
      where: { id },
      data: { status: "FAILED", lastError: "The updated job could not be queued." }
    });
    throw error;
  }
  return getEmail(accountId, id);
}

export async function reconcileUnqueuedEmails(boss) {
  const pending = await prisma.scheduledEmail.findMany({
    where: { status: "SCHEDULED", pgBossJobId: null }
  });
  for (const email of pending) await enqueue(boss, email);
}

export async function processEmailJob(boss, job) {
  const email = await prisma.scheduledEmail.findUnique({
    where: { id: job.data.emailId },
    include: { account: true }
  });
  if (!email || job.data.jobVersion !== email.jobVersion || email.status === "CANCELLED" || email.status === "SENT" || email.status === "FAILED") return;
  if (email.status === "PROCESSING") {
    const leaseExpired = email.updatedAt.getTime() < Date.now() - 5 * 60_000;
    if (leaseExpired) {
      await prisma.scheduledEmail.update({
        where: { id: email.id },
        data: { status: "UNKNOWN", lastError: "Worker stopped during delivery; delivery outcome is unknown." }
      });
    }
    return;
  }
  if (email.status !== "SCHEDULED") return;

  const attempt = email.attempts + 1;
  const claimed = await prisma.$transaction(async tx => {
    const update = await tx.scheduledEmail.updateMany({
      where: { id: email.id, status: "SCHEDULED", jobVersion: email.jobVersion },
      data: { status: "PROCESSING", attempts: { increment: 1 } }
    });
    if (update.count !== 1) return false;
    await tx.emailExecution.create({
      data: { scheduledEmailId: email.id, attempt, status: "PROCESSING" }
    });
    return true;
  });
  if (!claimed) return;

  try {
    const { gmail, flushTokenUpdate } = await createGmailClient(email.accountId);
    const stableMessageId = `<${email.idempotencyKey.replace(/[^a-zA-Z0-9._-]/g, ".")}@aurviz>`;
    const raw = Buffer.from([
      `To: ${email.recipient}`,
      `Subject: =?UTF-8?B?${Buffer.from(email.subject).toString("base64")}?=`,
      `Message-ID: ${stableMessageId}`,
      "MIME-Version: 1.0",
      "Content-Type: text/plain; charset=UTF-8",
      "Content-Transfer-Encoding: 8bit",
      "",
      email.body.replace(/\r\n?/g, "\n").replace(/\n/g, "\r\n")
    ].join("\r\n"), "utf8").toString("base64url");

    const response = await gmail.users.messages.send({
      userId: "me",
      requestBody: { raw }
    });
    await flushTokenUpdate();
    const messageId = response.data.id;
    if (!messageId) throw Object.assign(new Error("Gmail did not return a message ID."), { status: 502 });

    await prisma.$transaction([
      prisma.scheduledEmail.update({
        where: { id: email.id },
        data: { status: "SENT", messageId, executionAt: new Date(), lastError: null }
      }),
      prisma.emailExecution.update({
        where: { scheduledEmailId_attempt: { scheduledEmailId: email.id, attempt } },
        data: { status: "SENT", gmailMessageId: messageId }
      })
    ]);
  } catch (error) {
    const normalized = normalizeError(error);
    const exhausted = Number(job.retryCount ?? 0) >= Number(job.retryLimit ?? 3);
    const retry = normalized.retryable && !exhausted;
    const finalStatus = retry ? "SCHEDULED" : normalized.definitiveFailure || normalized.retryable ? "FAILED" : "UNKNOWN";
    await prisma.$transaction([
      prisma.scheduledEmail.update({
        where: { id: email.id },
        data: {
          status: finalStatus,
          lastError: normalized.message,
          executionAt: retry ? null : new Date()
        }
      }),
      prisma.emailExecution.update({
        where: { scheduledEmailId_attempt: { scheduledEmailId: email.id, attempt } },
        data: { status: finalStatus, errorDetails: normalized.message }
      })
    ]);
    if (retry) throw error;
  }
}
