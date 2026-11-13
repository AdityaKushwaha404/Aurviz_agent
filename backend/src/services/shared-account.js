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

const NON_DELIVERABLE = /@(?:[a-z0-9-]+\.)*(?:example\.(?:com|org|net)|[a-z0-9-]+\.example|[a-z0-9-]+\.test)$/i;

/** True for the test account itself and for reserved example addresses that reach no real person. */
export function isAllowedTestRecipient(recipient, shared = sharedTestEmail()) {
  const value = String(recipient ?? "").trim().toLowerCase();
  return value === shared || NON_DELIVERABLE.test(value);
}

/** Throws a 403 when the shared test account tries to email a real third party. */
export function assertRecipientAllowed(accountEmail, recipient, shared = sharedTestEmail()) {
  if (!isSharedAccount(accountEmail, shared)) return;
  if (!isAllowedTestRecipient(recipient, shared)) {
    throw Object.assign(
      new Error(`The test account can only send to itself (${shared}) or to example.com addresses. Use your own Gmail to email other people.`),
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
