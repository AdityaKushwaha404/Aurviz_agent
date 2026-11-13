/**
 * The "shared test inbox": a throwaway Gmail account whose OAuth grant lives on the server so
 * that a reviewer can open the app and use real Gmail without any credentials.
 *
 * Because anyone holding the APK can use it, it is deliberately restricted:
 *  - mail can only be sent to the account itself (no spam relay),
 *  - it cannot be disconnected or revoked from the app,
 *  - sessions are short-lived and creation is rate limited.
 */

export const DEFAULT_SHARED_TEST_EMAIL = "adityatesting404@gmail.com";

export function sharedTestEmail(env = process.env) {
  return (env.SHARED_TEST_ACCOUNT_EMAIL ?? DEFAULT_SHARED_TEST_EMAIL).trim().toLowerCase();
}

export function isSharedAccount(email, shared = sharedTestEmail()) {
  return typeof email === "string" && email.trim().toLowerCase() === shared;
}

/** Throws a 403 when a shared-account session tries to send to anyone but itself. */
export function assertRecipientAllowed(accountEmail, recipient, shared = sharedTestEmail()) {
  if (!isSharedAccount(accountEmail, shared)) return;
  if (String(recipient ?? "").trim().toLowerCase() !== shared) {
    throw Object.assign(
      new Error(`The shared test inbox can only send mail to itself (${shared}). Use your own Gmail to email other people.`),
      { status: 403 }
    );
  }
}

export function assertCanDisconnect(accountEmail, shared = sharedTestEmail()) {
  if (isSharedAccount(accountEmail, shared)) {
    throw Object.assign(
      new Error("The shared test inbox cannot be disconnected. Switch to another mailbox in Settings instead."),
      { status: 403 }
    );
  }
}
