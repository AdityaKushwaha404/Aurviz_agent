import { prisma } from "../database/prisma.js";
import { hashOpaqueToken } from "../services/token-vault.js";

export async function requireDeviceSession(req, res, next) {
  try {
    const authorization = req.get("authorization") ?? "";
    const match = /^Bearer ([A-Za-z0-9_-]{40,})$/.exec(authorization);
    if (!match) return res.status(401).json({ error: "Authentication required." });

    const session = await prisma.deviceSession.findUnique({
      where: { tokenHash: hashOpaqueToken(match[1]) },
      include: { account: { select: { id: true, email: true, disconnectedAt: true } } }
    });
    if (!session || session.revokedAt || session.expiresAt <= new Date() || session.account.disconnectedAt) {
      return res.status(401).json({ error: "The app session is invalid or expired. Reconnect Gmail." });
    }
    req.deviceSession = session;
    req.accountId = session.accountId;
    req.accountEmail = session.account.email;
    next();
  } catch (error) {
    next(error);
  }
}
