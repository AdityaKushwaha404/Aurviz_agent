import test from "node:test";
import assert from "node:assert/strict";
import { scheduleEmailSchema, sendEmailSchema, normalizeError } from "../src/utils/validation.js";

test("scheduled email validates recipient, time zone and offset-aware time", () => {
  const parsed = scheduleEmailSchema.parse({
    recipient: "recipient@example.com",
    subject: "Reminder",
    body: "Meeting starts soon.",
    scheduledAt: "2026-11-13T09:00:00+05:30",
    timeZone: "Asia/Kolkata"
  });
  assert.equal(parsed.scheduledAt.toISOString(), "2026-11-13T03:30:00.000Z");
  assert.equal(parsed.timeZone, "Asia/Kolkata");
});

test("rejects header injection and invalid time zones", () => {
  assert.equal(sendEmailSchema.safeParse({
    recipient: "person@example.com",
    subject: "hello\r\nBcc: attacker@example.com",
    body: "x"
  }).success, false);
  assert.equal(scheduleEmailSchema.safeParse({
    recipient: "person@example.com",
    subject: "Reminder",
    body: "x",
    scheduledAt: "2026-11-13T09:00:00",
    timeZone: "Not/AZone"
  }).success, false);
});

test("classifies retryable Gmail status codes without logging body data", () => {
  assert.equal(normalizeError({ response: { status: 429 } }).retryable, true);
  assert.equal(normalizeError({ response: { status: 503 } }).retryable, false);
  assert.equal(normalizeError({ response: { status: 503 } }).definitiveFailure, false);
  assert.equal(normalizeError({ response: { status: 400 } }).retryable, false);
  assert.equal(normalizeError({ response: { status: 400 } }).definitiveFailure, true);
  assert.equal(normalizeError({ code: "ECONNRESET" }).message, "Gmail delivery outcome could not be confirmed.");
});
