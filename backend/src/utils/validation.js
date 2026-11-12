import { z } from "zod";

const email = z.string().trim().email().max(254).refine(value => !/[\r\n]/.test(value), "Invalid recipient.");
const subject = z.string().max(500).refine(value => !/[\r\n]/.test(value), "Subject cannot contain line breaks.");
const body = z.string().max(20000);
const timeZone = z.string().min(1).max(100).refine(value => {
  try { new Intl.DateTimeFormat("en-US", { timeZone: value }); return true; } catch { return false; }
}, "Invalid IANA time zone.");

export const scheduleEmailSchema = z.object({
  recipient: email,
  subject,
  body,
  scheduledAt: z.string().datetime({ offset: true }).transform(value => new Date(value)),
  timeZone
}).strict();

export const updateEmailSchema = z.object({
  recipient: email.optional(),
  subject: subject.optional(),
  body: body.optional(),
  scheduledAt: z.string().datetime({ offset: true }).transform(value => new Date(value)).optional(),
  timeZone: timeZone.optional()
}).strict().refine(value => Object.keys(value).length > 0, "At least one update field is required.");

export const sendEmailSchema = z.object({
  recipient: email,
  subject,
  body
}).strict();

export function normalizeError(error) {
  const code = Number(error?.response?.status ?? error?.status ?? 0);
  return {
    code,
    retryable: code === 429,
    definitiveFailure: code >= 400 && code < 500 && code !== 429,
    message: code ? `Gmail API returned HTTP ${code}.` : "Gmail delivery outcome could not be confirmed."
  };
}
