# Backend API

Base URL is the deployed HTTPS service. All `/api/v1` calls except the OAuth exchange need `Authorization: Bearer <session token>`. Errors return `{ "error": "message" }`.

## Health
| Method | Path | Notes |
|---|---|---|
| GET | `/health` | `{"status":"ok"}` when the database and job queue are reachable |

## Authentication
| Method | Path | Description |
|---|---|---|
| GET | `/auth/google` | Starts Google OAuth (adds CSRF state), redirects to Google |
| GET | `/auth/google/callback` | Exchanges the code, stores the encrypted refresh token, redirects to `aurviz://oauth/callback?code=<one-time code>` |
| POST | `/api/v1/auth/exchange` | Body `{ "code": "<one-time code>" }`, returns `{ accessToken, expiresAt }` |
| GET | `/api/v1/auth/me` | Connected account and expiry |
| POST | `/api/v1/auth/session/refresh` | Rotates the session token |
| DELETE | `/api/v1/auth/session` | Revokes this session |
| DELETE | `/api/v1/auth/google` | Disconnects the account, cancels pending sends, revokes the Google token |

## Mailbox
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/gmail/messages?q=&max=` | Search with Gmail syntax. `max` 1 to 15. Returns id, from, to, subject, date, snippet, unread |
| GET | `/api/v1/gmail/messages/:id` | One message as plain text (HTML converted, attachments skipped, 6,000 character cap) |
| POST | `/api/v1/gmail/drafts` | Body `{ recipient, subject, body }`. Saves a draft, sends nothing |

Example:
```http
GET /api/v1/gmail/messages?q=subject:interview%20newer_than:7d&max=5
Authorization: Bearer <token>
```
```json
{ "query": "subject:interview newer_than:7d", "messages": [
  { "id": "19a1d…", "from": "HR <hr@example.com>", "subject": "Interview invitation",
    "date": "Thu, 12 Nov 2026", "snippet": "Please confirm…", "unread": true } ] }
```

## Sending and scheduling
| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/emails/send` | Queue an immediate email. Header `Idempotency-Key` (8 to 120 safe characters) required. Returns 202 |
| POST | `/api/v1/emails/scheduled` | Schedule one email for a future time (at least 30 s ahead) |
| GET | `/api/v1/emails/scheduled` | List scheduled emails with recent history |
| GET | `/api/v1/emails/scheduled/:id` | One email with execution history |
| PATCH | `/api/v1/emails/scheduled/:id` | Update a pending email (stale queued jobs cannot send the old version) |
| DELETE | `/api/v1/emails/scheduled/:id` | Cancel a pending email |

## Test account restrictions
The reviewer test account (`SHARED_TEST_ACCOUNT_EMAIL`) has its credentials shown in the app, so the server enforces:
- `POST /emails/send` and `POST /emails/scheduled` return **403** unless the recipient is the test account itself or an `example.com`, `example.org`, `example.net`, `*.example` or `*.test` address.
- `DELETE /auth/google` returns **403** (the grant cannot be revoked from the app). `DELETE /auth/session` ends only that device's session.

## Delivery semantics
Gmail acceptance and the database commit cannot be atomic. Definite 4xx failures become `FAILED`; quota (429) is retried with backoff; network or 5xx outcomes become `UNKNOWN` and are **never** resent automatically. The same idempotency key never produces two messages.

## Security
HTTPS, CSRF state on OAuth, one-time codes, hashed session tokens, AES-256-GCM refresh tokens, Helmet, rate limiting (120 requests per minute), 32 KB body limit, schema validation, header-injection rejection, redacted logs.
