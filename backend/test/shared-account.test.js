import test from "node:test";
import assert from "node:assert/strict";
import {
  assertCanDisconnect,
  assertRecipientAllowed,
  isSharedAccount,
  sharedTestEmail
} from "../src/services/shared-account.js";

const SHARED = "shared.inbox@example.com";

test("shared account detection is case-insensitive and env-overridable", () => {
  assert.equal(sharedTestEmail({}), "adityatesting404@gmail.com");
  assert.equal(sharedTestEmail({ SHARED_TEST_ACCOUNT_EMAIL: " Other@Example.com " }), "other@example.com");
  assert.equal(isSharedAccount("Shared.Inbox@Example.com", SHARED), true);
  assert.equal(isSharedAccount("someone@example.com", SHARED), false);
  assert.equal(isSharedAccount(undefined, SHARED), false);
});

test("the shared inbox can only send to itself", () => {
  assert.doesNotThrow(() => assertRecipientAllowed(SHARED, "SHARED.inbox@example.com", SHARED));
  assert.throws(() => assertRecipientAllowed(SHARED, "victim@example.com", SHARED), error => error.status === 403);
});

test("normal accounts are not restricted", () => {
  assert.doesNotThrow(() => assertRecipientAllowed("me@example.com", "anyone@example.com", SHARED));
  assert.doesNotThrow(() => assertCanDisconnect("me@example.com", SHARED));
});

test("the shared inbox cannot be disconnected", () => {
  assert.throws(() => assertCanDisconnect(SHARED, SHARED), error => error.status === 403);
});
