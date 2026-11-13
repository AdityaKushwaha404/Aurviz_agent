import { randomBytes } from "node:crypto";
import { Router } from "express";
import { env } from "../config/env.js";
import { prisma } from "../database/prisma.js";
import { requireDeviceSession } from "../middleware/device-auth.js";
import { authorizationUrl, createOAuthClient, exchangeAuthorizationCode } from "../services/google.js";
import { decryptRefreshToken } from "../services/token-vault.js";
import { assertCanDisconnect, isSharedAccount, sharedTestEmail } from "../services/shared-account.js";
import { createOpaqueToken, encryptRefreshToken, hashOpaqueToken, safeEquals } from "../services/token-vault.js";

const router = Router();

router.get("/google", (req, res, next) => {
  const state = randomBytes(32).toString("base64url");
  req.session.oauthState = state;
  req.session.oauthCreatedAt = Date.now();
  req.session.save(error => {
    if (error) return next(error);
    res.redirect(302, authorizationUrl(state));
  });
});

router.get("/google/callback", async (req, res, next) => {
  try {
    if (req.query.error) return res.status(400).send("Google authorization was not completed. Return to Aurviz and try again.");
    const state = typeof req.query.state === "string" ? req.query.state : "";
    const code = typeof req.query.code === "string" ? req.query.code : "";
    const storedState = req.session.oauthState;
    const createdAt = Number(req.session.oauthCreatedAt ?? 0);
    delete req.session.oauthState;
    delete req.session.oauthCreatedAt;
    if (!state || !code || !storedState || !safeEquals(state, storedState) ||
        Date.now() - createdAt > 10 * 60_000) {
      return res.status(400).send("OAuth state is invalid or expired. Return to Aurviz and reconnect.");
    }

    const { email, refreshToken } = await exchangeAuthorizationCode(code);
    const existing = await prisma.googleAccount.findUnique({ where: { email } });
    if (!refreshToken && (!existing || existing.disconnectedAt)) {
      return res.status(400).send("Google did not return offline access. Disconnect the Aurviz app in Google Account settings, then connect again.");
    }
    const account = await prisma.googleAccount.upsert({
      where: { email },
      create: {
        email,
        encryptedRefreshToken: encryptRefreshToken(refreshToken)
      },
      update: {
        encryptedRefreshToken: refreshToken ? encryptRefreshToken(refreshToken) : existing.encryptedRefreshToken,
        disconnectedAt: null
      }
    });

    const oneTimeCode = createOpaqueToken();
    await prisma.appAuthCode.create({
      data: {
        codeHash: hashOpaqueToken(oneTimeCode),
        accountId: account.id,
        expiresAt: new Date(Date.now() + env.oauthCodeTtlMinutes * 60_000)
      }
    });
    const redirect = new URL(env.androidRedirectUri);
    redirect.searchParams.set("code", oneTimeCode);
    res.redirect(302, redirect.toString());
  } catch (error) {
    next(error);
  }
});

router.post("/exchange", async (req, res, next) => {
  try {
    const code = req.body?.code;
    if (typeof code !== "string" || !/^[A-Za-z0-9_-]{40,}$/.test(code)) {
      return res.status(400).json({ error: "Invalid authorization code." });
    }
    const appCode = await prisma.appAuthCode.findUnique({
      where: { codeHash: hashOpaqueToken(code) }
    });
    if (!appCode || appCode.usedAt || appCode.expiresAt <= new Date()) {
      return res.status(401).json({ error: "Authorization code is expired or already used." });
    }
    const claimed = await prisma.appAuthCode.updateMany({
      where: { id: appCode.id, usedAt: null, expiresAt: { gt: new Date() } },
      data: { usedAt: new Date() }
    });
    if (claimed.count !== 1) return res.status(401).json({ error: "Authorization code has already been used." });

    const accessToken = createOpaqueToken();
    const expiresAt = new Date(Date.now() + env.accessTokenTtlDays * 86_400_000);
    await prisma.deviceSession.create({
      data: {
        accountId: appCode.accountId,
        tokenHash: hashOpaqueToken(accessToken),
        expiresAt
      }
    });
    res.set("Cache-Control", "no-store").json({ accessToken, expiresAt });
  } catch (error) {
    next(error);
  }
});

router.get("/me", requireDeviceSession, async (req, res) => {
  res.set("Cache-Control", "no-store").json({
    connected: true,
    shared: isSharedAccount(req.accountEmail),
    email: req.accountEmail,
    expiresAt: req.deviceSession.expiresAt
  });
});

router.post("/session/refresh", requireDeviceSession, async (req, res, next) => {
  try {
    const accessToken = createOpaqueToken();
    const expiresAt = new Date(Date.now() + env.accessTokenTtlDays * 86_400_000);
    await prisma.$transaction([
      prisma.deviceSession.update({ where: { id: req.deviceSession.id }, data: { revokedAt: new Date() } }),
      prisma.deviceSession.create({
        data: { accountId: req.accountId, tokenHash: hashOpaqueToken(accessToken), expiresAt }
      })
    ]);
    res.set("Cache-Control", "no-store").json({ accessToken, expiresAt });
  } catch (error) {
    next(error);
  }
});

router.delete("/session", requireDeviceSession, async (req, res, next) => {
  try {
    await prisma.deviceSession.update({
      where: { id: req.deviceSession.id },
      data: { revokedAt: new Date() }
    });
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});

router.delete("/google", requireDeviceSession, async (req, res, next) => {
  try {
    assertCanDisconnect(req.accountEmail);
    const account = await prisma.googleAccount.findUnique({ where: { id: req.accountId } });
    const pending = await prisma.scheduledEmail.findMany({
      where: { accountId: req.accountId, status: "SCHEDULED" },
      select: { id: true, pgBossJobId: true }
    });
    for (const email of pending) {
      if (email.pgBossJobId && req.app.locals.boss) {
        await req.app.locals.boss.cancel("aurviz-send-email", email.pgBossJobId);
      }
    }
    const now = new Date();
    await prisma.$transaction([
      prisma.scheduledEmail.updateMany({
        where: { accountId: req.accountId, status: "SCHEDULED" },
        data: { status: "CANCELLED", executionAt: now, lastError: "Account disconnected by user." }
      }),
      prisma.deviceSession.updateMany({
        where: { accountId: req.accountId, revokedAt: null },
        data: { revokedAt: now }
      }),
      prisma.googleAccount.update({
        where: { id: req.accountId },
        data: { disconnectedAt: now, encryptedRefreshToken: "" }
      })
    ]);
    if (account?.encryptedRefreshToken) {
      const oauth = createOAuthClient();
      try {
        await oauth.revokeToken(decryptRefreshToken(account.encryptedRefreshToken));
      } catch {
        // Local revocation remains effective. The user can also remove access in Google Account settings.
      }
    }
    res.status(204).end();
  } catch (error) {
    next(error);
  }
});

export default router;
