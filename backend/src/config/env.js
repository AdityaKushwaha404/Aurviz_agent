const required = [
  "DATABASE_URL",
  "SESSION_SECRET",
  "TOKEN_ENCRYPTION_KEY_B64",
  "GOOGLE_CLIENT_ID",
  "GOOGLE_CLIENT_SECRET",
  "GOOGLE_REDIRECT_URI",
  "ANDROID_REDIRECT_URI",
  "PUBLIC_BASE_URL"
];

for (const key of required) {
  if (!process.env[key]?.trim()) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
}

if (Buffer.from(process.env.TOKEN_ENCRYPTION_KEY_B64, "base64").length !== 32) {
  throw new Error("TOKEN_ENCRYPTION_KEY_B64 must encode exactly 32 random bytes.");
}
if (process.env.SESSION_SECRET.length < 32) {
  throw new Error("SESSION_SECRET must be at least 32 characters.");
}

export const env = Object.freeze({
  nodeEnv: process.env.NODE_ENV ?? "development",
  role: process.env.ROLE ?? "all",
  port: Number(process.env.PORT ?? 3000),
  databaseUrl: process.env.DATABASE_URL,
  sessionSecret: process.env.SESSION_SECRET,
  tokenEncryptionKey: Buffer.from(process.env.TOKEN_ENCRYPTION_KEY_B64, "base64"),
  googleClientId: process.env.GOOGLE_CLIENT_ID,
  googleClientSecret: process.env.GOOGLE_CLIENT_SECRET,
  googleRedirectUri: process.env.GOOGLE_REDIRECT_URI,
  androidRedirectUri: process.env.ANDROID_REDIRECT_URI,
  publicBaseUrl: process.env.PUBLIC_BASE_URL.replace(/\/$/, ""),
  accessTokenTtlDays: Number(process.env.ACCESS_TOKEN_TTL_DAYS ?? 30),
  oauthCodeTtlMinutes: Number(process.env.OAUTH_CODE_TTL_MINUTES ?? 5)
});
