import test from "node:test";
import assert from "node:assert/strict";
import {
  buildRawMessage,
  createInboxService,
  extractPlainText,
  htmlToText,
  mapGmailError,
  summarizeMessage
} from "../src/services/gmail-inbox.js";

const b64 = text => Buffer.from(text, "utf8").toString("base64url");

test("extractPlainText prefers text/plain and skips attachments", () => {
  const payload = {
    mimeType: "multipart/mixed",
    parts: [
      {
        mimeType: "multipart/alternative",
        parts: [
          { mimeType: "text/plain", body: { data: b64("Interview on Monday at 10am") } },
          { mimeType: "text/html", body: { data: b64("<p>Interview</p>") } }
        ]
      },
      { mimeType: "text/plain", filename: "notes.txt", body: { data: b64("attachment text") } }
    ]
  };
  assert.equal(extractPlainText(payload), "Interview on Monday at 10am");
});

test("extractPlainText falls back to HTML converted to text", () => {
  const payload = { mimeType: "text/html", body: { data: b64("<style>x{}</style><p>Hello&nbsp;Rahul</p><br>Thanks") } };
  assert.equal(extractPlainText(payload), "Hello Rahul\n\nThanks");
  assert.equal(htmlToText("<script>alert(1)</script>Safe"), "Safe");
});

test("summarizeMessage reads headers case-insensitively and flags unread", () => {
  const summary = summarizeMessage({
    id: "abc123",
    threadId: "t1",
    snippet: "Please confirm",
    labelIds: ["INBOX", "UNREAD", "IMPORTANT"],
    payload: { headers: [{ name: "from", value: "HR <hr@example.com>" }, { name: "SUBJECT", value: "Interview" }] }
  });
  assert.equal(summary.from, "HR <hr@example.com>");
  assert.equal(summary.subject, "Interview");
  assert.equal(summary.unread, true);
});

test("buildRawMessage encodes non-ASCII subjects and rejects header injection", () => {
  const raw = buildRawMessage({ recipient: "a@example.com", subject: "Namaste 🙏", body: "line1\nline2" });
  const decoded = Buffer.from(raw, "base64url").toString("utf8");
  assert.match(decoded, /^To: a@example.com\r\nSubject: =\?UTF-8\?B\?/);
  assert.match(decoded, /line1\r\nline2$/);
  assert.throws(() => buildRawMessage({ recipient: "a@example.com", subject: "x\r\nBcc: evil@example.com", body: "" }));
});

test("mapGmailError identifies missing scopes, expiry and outages", () => {
  assert.equal(mapGmailError({ response: { status: 403, data: { error: { status: "PERMISSION_DENIED" } } } }).code, "INSUFFICIENT_SCOPE");
  assert.equal(mapGmailError({ message: "invalid_grant" }).status, 401);
  assert.equal(mapGmailError({ response: { status: 500 } }).status, 503);
  assert.equal(mapGmailError({ response: { status: 429 } }).status, 429);
});

test("inbox search caps result count and returns summaries", async () => {
  let requestedMax;
  const gmail = {
    users: {
      messages: {
        list: async ({ maxResults }) => {
          requestedMax = maxResults;
          return { data: { messages: [{ id: "m1" }], resultSizeEstimate: 1 } };
        },
        get: async ({ id }) => ({
          data: { id, threadId: "t", snippet: "s", labelIds: [], payload: { headers: [{ name: "Subject", value: "Hi" }] } }
        })
      }
    }
  };
  const service = createInboxService(async () => ({ gmail, flushTokenUpdate: async () => {} }));
  const result = await service.search("acct", { query: "is:important", maxResults: 500 });
  assert.equal(requestedMax, 15);
  assert.equal(result.messages[0].subject, "Hi");
  assert.equal(result.query, "is:important");
});

test("disconnected accounts surface a reconnect error", async () => {
  const service = createInboxService(async () => { throw new Error("Gmail account is disconnected."); });
  await assert.rejects(() => service.read("acct", "m1"), error => error.status === 401);
});
