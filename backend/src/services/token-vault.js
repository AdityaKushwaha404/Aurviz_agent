import { createCipheriv, createDecipheriv, createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { env } from "../config/env.js";

const VERSION = "v1";

export function hashOpaqueToken(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

export function createOpaqueToken() {
  return randomBytes(32).toString("base64url");
}

export function safeEquals(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

export function encryptRefreshToken(plaintext) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", env.tokenEncryptionKey, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [VERSION, iv.toString("base64url"), tag.toString("base64url"), ciphertext.toString("base64url")].join(".");
}

export function decryptRefreshToken(payload) {
  const [version, ivText, tagText, ciphertextText] = payload.split(".");
  if (version !== VERSION || !ivText || !tagText || !ciphertextText) {
    throw new Error("Stored OAuth token has an unsupported format.");
  }
  const decipher = createDecipheriv("aes-256-gcm", env.tokenEncryptionKey, Buffer.from(ivText, "base64url"));
  decipher.setAuthTag(Buffer.from(tagText, "base64url"));
  return Buffer.concat([
    decipher.update(Buffer.from(ciphertextText, "base64url")),
    decipher.final()
  ]).toString("utf8");
}
