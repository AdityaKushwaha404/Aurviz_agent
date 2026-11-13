/**
 * Read-side Gmail operations (search, read, draft) for the agent.
 *
 * The pure helpers (header lookup, body extraction, MIME building, error mapping)
 * are exported separately so they can be unit-tested without Google credentials.
 */

export const MAX_SEARCH_RESULTS = 15;
export const MAX_BODY_CHARS = 6000;

export function headerValue(headers, name) {
  const wanted = name.toLowerCase();
  return (headers ?? []).find(header => header?.name?.toLowerCase() === wanted)?.value ?? "";
}

function decodeBase64Url(data) {
  if (!data) return "";
  return Buffer.from(data, "base64url").toString("utf8");
}

/** Strips tags, scripts, styles and common entities from an HTML email body. */
export function htmlToText(html) {
  return html
    .replace(/<(script|style|head)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|tr|li|h[1-6])>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, "\"")
    .replace(/&#39;/gi, "'")
    .replace(/[ \t]+/g, " ")
    .replace(/\n\s*\n+/g, "\n\n")
    .trim();
}

/**
 * Walks a Gmail message payload and returns the best plain-text body. Prefers
 * text/plain parts, falling back to converted text/html.
 */
export function extractPlainText(payload) {
  const plain = [];
  const html = [];
  const visit = part => {
    if (!part) return;
    const mime = (part.mimeType ?? "").toLowerCase();
    if (part.filename) return; // attachment
    if (mime === "text/plain" && part.body?.data) plain.push(decodeBase64Url(part.body.data));
    else if (mime === "text/html" && part.body?.data) html.push(decodeBase64Url(part.body.data));
    (part.parts ?? []).forEach(visit);
  };
  visit(payload);
  const text = plain.length > 0 ? plain.join("\n") : htmlToText(html.join("\n"));
  return text.replace(/\r\n?/g, "\n").trim();
}

export function summarizeMessage(message) {
  const headers = message?.payload?.headers ?? [];
  return {
    id: message.id,
    threadId: message.threadId,
    from: headerValue(headers, "From"),
    to: headerValue(headers, "To"),
    subject: headerValue(headers, "Subject") || "(no subject)",
    date: headerValue(headers, "Date"),
    snippet: message.snippet ?? "",
    labels: message.labelIds ?? [],
    unread: (message.labelIds ?? []).includes("UNREAD")
  };
}

function encodeHeader(value) {
  return /^[\x20-\x7E]*$/.test(value) ? value : `=?UTF-8?B?${Buffer.from(value).toString("base64")}?=`;
}

/** Builds a base64url RFC 2822 message suitable for Gmail's raw field. */
export function buildRawMessage({ recipient, subject, body }) {
  if (/[\r\n]/.test(recipient) || /[\r\n]/.test(subject)) {
    throw Object.assign(new Error("Headers cannot contain line breaks."), { status: 400 });
  }
  return Buffer.from([
    `To: ${recipient}`,
    `Subject: ${encodeHeader(subject)}`,
    "MIME-Version: 1.0",
    "Content-Type: text/plain; charset=UTF-8",
    "Content-Transfer-Encoding: 8bit",
    "",
    body.replace(/\r\n?/g, "\n").replace(/\n/g, "\r\n")
  ].join("\r\n"), "utf8").toString("base64url");
}

/**
 * Maps a Google API error to an HTTP error the Android client can act on. A 403 for
 * missing scopes means the account was connected before inbox access was requested.
 */
export function mapGmailError(error) {
  const status = Number(error?.response?.status ?? error?.code ?? error?.status ?? 0);
  const reason = String(error?.response?.data?.error?.status ?? error?.errors?.[0]?.reason ?? "");
  if (status === 401 || /invalid_grant/i.test(String(error?.message ?? ""))) {
    return Object.assign(new Error("Gmail authorization expired. Reconnect Gmail in Settings."), { status: 401 });
  }
  if (status === 403 && /PERMISSION|insufficient/i.test(reason + String(error?.message ?? ""))) {
    return Object.assign(
      new Error("Gmail inbox access was not granted. Disconnect and reconnect Gmail in Settings to allow reading and drafts."),
      { status: 403, code: "INSUFFICIENT_SCOPE" }
    );
  }
  if (status === 404) return Object.assign(new Error("That email was not found."), { status: 404 });
  if (status === 429) return Object.assign(new Error("Gmail rate limit reached. Try again shortly."), { status: 429 });
  if (status >= 500) return Object.assign(new Error("Gmail is temporarily unavailable. Try again shortly."), { status: 503 });
  return error;
}

async function withGmail(createGmailClient, accountId, operation) {
  try {
    const { gmail, flushTokenUpdate } = await createGmailClient(accountId);
    const result = await operation(gmail);
    await flushTokenUpdate();
    return result;
  } catch (error) {
    if (error?.message === "Gmail account is disconnected.") {
      throw Object.assign(new Error("Gmail is disconnected. Reconnect Gmail in Settings."), { status: 401 });
    }
    throw mapGmailError(error);
  }
}

export function createInboxService(createGmailClient) {
  return {
    async search(accountId, { query, maxResults }) {
      return withGmail(createGmailClient, accountId, async gmail => {
        const list = await gmail.users.messages.list({
          userId: "me",
          q: query || "in:inbox",
          maxResults: Math.min(Math.max(maxResults ?? 10, 1), MAX_SEARCH_RESULTS)
        });
        const ids = (list.data.messages ?? []).map(message => message.id);
        const messages = await Promise.all(ids.map(id => gmail.users.messages.get({
          userId: "me",
          id,
          format: "metadata",
          metadataHeaders: ["From", "To", "Subject", "Date"]
        })));
        return {
          query: query || "in:inbox",
          resultSizeEstimate: list.data.resultSizeEstimate ?? ids.length,
          messages: messages.map(response => summarizeMessage(response.data))
        };
      });
    },

    async read(accountId, id) {
      return withGmail(createGmailClient, accountId, async gmail => {
        const response = await gmail.users.messages.get({ userId: "me", id, format: "full" });
        const body = extractPlainText(response.data.payload);
        return {
          ...summarizeMessage(response.data),
          body: body.length > MAX_BODY_CHARS ? `${body.slice(0, MAX_BODY_CHARS)}\n[truncated]` : body
        };
      });
    },

    async createDraft(accountId, input) {
      return withGmail(createGmailClient, accountId, async gmail => {
        const response = await gmail.users.drafts.create({
          userId: "me",
          requestBody: { message: { raw: buildRawMessage(input) } }
        });
        return { id: response.data.id, messageId: response.data.message?.id ?? null };
      });
    }
  };
}
