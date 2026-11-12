import test from "node:test";
import assert from "node:assert/strict";

test("token vault encrypts and decrypts refresh tokens and hashes opaque credentials", async () => {
  process.env.DATABASE_URL = "postgresql://unused:unused@localhost:5432/unused";
  process.env.SESSION_SECRET = "a-local-session-secret-with-at-least-32-characters";
  process.env.TOKEN_ENCRYPTION_KEY_B64 = Buffer.alloc(32, 7).toString("base64");
  process.env.GOOGLE_CLIENT_ID = "test-client";
  process.env.GOOGLE_CLIENT_SECRET = "test-secret";
  process.env.GOOGLE_REDIRECT_URI = "http://localhost:3000/auth/google/callback";
  process.env.ANDROID_REDIRECT_URI = "aurviz://oauth/callback";
  process.env.PUBLIC_BASE_URL = "http://localhost:3000";

  const vault = await import("../src/services/token-vault.js");
  const token = "refresh-token-that-must-not-be-stored-in-plaintext";
  const encrypted = vault.encryptRefreshToken(token);
  assert.notEqual(encrypted, token);
  assert.equal(vault.decryptRefreshToken(encrypted), token);
  assert.equal(vault.hashOpaqueToken("same-token"), vault.hashOpaqueToken("same-token"));
  assert.notEqual(vault.hashOpaqueToken("same-token"), vault.hashOpaqueToken("other-token"));
  assert.match(vault.createOpaqueToken(), /^[A-Za-z0-9_-]{40,}$/);
});

test("tampered encrypted token is rejected", async () => {
  process.env.DATABASE_URL = "postgresql://unused:unused@localhost:5432/unused";
  process.env.SESSION_SECRET = "a-local-session-secret-with-at-least-32-characters";
  process.env.TOKEN_ENCRYPTION_KEY_B64 = Buffer.alloc(32, 9).toString("base64");
  process.env.GOOGLE_CLIENT_ID = "test-client";
  process.env.GOOGLE_CLIENT_SECRET = "test-secret";
  process.env.GOOGLE_REDIRECT_URI = "http://localhost:3000/auth/google/callback";
  process.env.ANDROID_REDIRECT_URI = "aurviz://oauth/callback";
  process.env.PUBLIC_BASE_URL = "http://localhost:3000";

  const vault = await import("../src/services/token-vault.js");
  const encrypted = vault.encryptRefreshToken("secret");
  const tampered = encrypted.slice(0, -2) + "aa";
  assert.throws(() => vault.decryptRefreshToken(tampered));
});
