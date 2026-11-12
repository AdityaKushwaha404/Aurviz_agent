# Aurviz Gmail scheduling backend

Standalone Node.js API and worker for Google OAuth and durable Gmail scheduling. PostgreSQL stores application data and sessions; pg-boss persists jobs in PostgreSQL.

> Not production-ready until deployed and integration-tested. Live OAuth, Gmail delivery, token refresh, and restart recovery are not verified here. Gmail is an external side effect and cannot provide exactly-once delivery: ambiguous network failures are marked `UNKNOWN` and are not automatically resent.

## Requirements

- Node.js 22.12 or newer, npm
- Docker Compose (local PostgreSQL)
- Google Cloud Web OAuth client with Gmail API enabled
- Public HTTPS backend URL for phone-based OAuth and production

## Local setup

1. Copy `.env.example` to `.env`. Never commit `.env`.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Generate separate secrets: `openssl rand -base64 32` for `TOKEN_ENCRYPTION_KEY_B64`, and `openssl rand -hex 32` for `SESSION_SECRET`.
4. Enter the Google Web OAuth client ID and replacement secret in the local environment file only. The secret previously shared in chat must be rotated before use.
5. For desktop-local testing register the exact OAuth callback `http://localhost:3000/auth/google/callback`. For a physical phone, use a public HTTPS tunnel/deployment and register its exact callback. Phone localhost means the phone, not your computer.
6. Set `GOOGLE_REDIRECT_URI`, `PUBLIC_BASE_URL`, and `ANDROID_REDIRECT_URI` to the corresponding exact origins/scheme.
7. Run `npm install`, `npm run prisma:generate`, `npm run prisma:migrate`, then `npm start`.

Health check: `GET /health`. To begin OAuth open `/auth/google`; successful approval redirects to the app URI with a short-lived one-time code. Android exchanges it at `POST /api/v1/auth/exchange` and stores the opaque returned token in secure storage.

Publish or configure the OAuth consent screen test users. Enable Gmail API. Requested scopes are `openid`, `email`, and `https://www.googleapis.com/auth/gmail.send`.

## API

Authenticated routes use `Authorization: Bearer <device-session-token>`. Send/schedule routes require an `Idempotency-Key` header (8–120 safe characters); reuse the same key only for retries of the same action.

| Method | Path | Purpose |
|---|---|---|
| GET | `/auth/google` | Start OAuth with server-session CSRF state |
| GET | `/auth/google/callback` | Validate state, exchange code, encrypt refresh token |
| POST | `/api/v1/auth/exchange` | One-time OAuth app-code exchange |
| GET | `/api/v1/auth/me` | Connected account and session expiry |
| POST | `/api/v1/auth/session/refresh` | Rotate app bearer token |
| DELETE | `/api/v1/auth/session` | Revoke current session |
| DELETE | `/api/v1/auth/google` | Disconnect account and cancel pending sends |
| POST | `/api/v1/emails/send` | Queue immediate email; returns 202 |
| POST | `/api/v1/emails/scheduled` | Schedule one email |
| GET | `/api/v1/emails/scheduled` | List scheduled emails/history |
| GET | `/api/v1/emails/scheduled/:id` | Get one email and execution history |
| PATCH | `/api/v1/emails/scheduled/:id` | Update a pending email |
| DELETE | `/api/v1/emails/scheduled/:id` | Cancel a pending email |

Schedule JSON uses an ISO-8601 instant plus IANA timezone for display/audit:

```json
{"recipient":"person@example.com","subject":"Meeting reminder","body":"See you soon.","scheduledAt":"2026-11-13T03:30:00.000Z","timeZone":"Asia/Kolkata"}
```

One-shot sends must be at least 30 seconds in the future. Body limit is 20,000 characters. Recurrence is not yet modeled.

## Worker and delivery semantics

The default process runs API and worker together. Set `ROLE=api` and `ROLE=worker` on separate deployments sharing the same PostgreSQL database. pg-boss jobs survive process restart; startup reconciles scheduled database rows lacking queue jobs. Keep a worker alive and back up both application tables and pg-boss data.

Explicit Gmail HTTP 429 quota rejections receive bounded retries. Other 4xx responses are recorded as `FAILED`; 5xx and network failures have ambiguous delivery and are stored as `UNKNOWN` without automatic retry to avoid duplicate sends. A crash after Gmail accepted an email but before the database commit is also ambiguous. Gmail message IDs are saved when returned.

## Security

- Use HTTPS in deployment; keep web OAuth secret, session secret, encryption key and database URL in a secret manager.
- Refresh tokens are AES-256-GCM encrypted with a dedicated 32-byte key. Back up and rotate that key deliberately.
- OAuth state has a short TTL; app codes are one-time and hashed; session cookies are HttpOnly/SameSite; bearer tokens are hashed at rest.
- Helmet, bounded JSON requests, schema validation, rate limiting, and generic internal errors are enabled. Logs omit mail content, OAuth codes, tokens and upstream response bodies.
- Configure trusted proxy count and secure cookies for hosting. Use database TLS, least-privilege networking, and encrypted backups.
- The Android agent must request explicit user confirmation before calling send/schedule endpoints. The backend does not provide a user confirmation UI.

## Tests and unverified work

`npm test` runs deterministic token-vault and validation unit tests. `npm run prisma:generate` checks generated client compatibility; migrate against a disposable/local database.

Not yet tested: real OAuth, real Gmail delivery or refresh, worker restart with a live PostgreSQL instance, scheduling/cancel races, or Android-to-hosted-backend communication. These require rotated Google credentials in a private environment, Cloud consent/API configuration, and a reachable database/backend. Unit tests do not prove delivery.
