import { google } from "googleapis";
import { env } from "../config/env.js";
import { prisma } from "../database/prisma.js";
import { decryptRefreshToken, encryptRefreshToken } from "./token-vault.js";

export function createOAuthClient() {
  return new google.auth.OAuth2(
    env.googleClientId,
    env.googleClientSecret,
    env.googleRedirectUri
  );
}

export function authorizationUrl(state) {
  return createOAuthClient().generateAuthUrl({
    access_type: "offline",
    include_granted_scopes: false,
    prompt: "consent",
    state,
    scope: ["openid", "email", "https://www.googleapis.com/auth/gmail.send"]
  });
}

export async function exchangeAuthorizationCode(code) {
  const client = createOAuthClient();
  const { tokens } = await client.getToken(code);
  if (!tokens.access_token) throw new Error("Google did not return an access token.");
  client.setCredentials(tokens);
  const profile = await google.oauth2({ version: "v2", auth: client }).userinfo.get();
  const email = profile.data.email?.trim().toLowerCase();
  if (!email || !profile.data.verified_email) {
    throw new Error("Google did not provide a verified account email.");
  }
  return { email, refreshToken: tokens.refresh_token ?? null };
}

export async function getAccountEmail(accountId) {
  const account = await prisma.googleAccount.findUnique({ where: { id: accountId } });
  if (!account) return null;
  return account.email;
}

export async function createGmailClient(accountId) {
  const account = await prisma.googleAccount.findUnique({ where: { id: accountId } });
  if (!account || account.disconnectedAt || !account.encryptedRefreshToken) {
    throw new Error("Gmail account is disconnected.");
  }

  const auth = createOAuthClient();
  const refreshToken = decryptRefreshToken(account.encryptedRefreshToken);
  auth.setCredentials({ refresh_token: refreshToken });

  let tokenWrite = Promise.resolve();
  auth.on("tokens", tokens => {
    if (tokens.refresh_token) {
      tokenWrite = prisma.googleAccount.update({
        where: { id: accountId },
        data: { encryptedRefreshToken: encryptRefreshToken(tokens.refresh_token) }
      }).then(() => undefined);
    }
  });

  await auth.getAccessToken();
  await tokenWrite;
  return {
    gmail: google.gmail({ version: "v1", auth }),
    flushTokenUpdate: () => tokenWrite
  };
}
