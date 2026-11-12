import express from "express";
import helmet from "helmet";
import session from "express-session";
import connectPgSimple from "connect-pg-simple";
import { rateLimit } from "express-rate-limit";
import { Pool } from "pg";
import { PgBoss } from "pg-boss";
import { Prisma } from "@prisma/client";
import { env } from "./config/env.js";
import { prisma } from "./database/prisma.js";
import authRoutes from "./routes/auth.js";
import emailRoutes from "./routes/emails.js";
import { startEmailWorker } from "./scheduler/worker.js";

const app = express();
app.disable("x-powered-by");
app.set("trust proxy", env.nodeEnv === "production" ? 1 : false);
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'none'"],
      frameAncestors: ["'none'"],
      baseUri: ["'none'"],
      formAction: ["'none'"]
    }
  },
  referrerPolicy: { policy: "no-referrer" }
}));
app.use(express.json({ limit: "32kb", strict: true }));
app.use(rateLimit({
  windowMs: 60_000,
  limit: 120,
  standardHeaders: "draft-8",
  legacyHeaders: false
}));

const sessionPool = new Pool({ connectionString: env.databaseUrl, max: 5 });
const PgSessionStore = connectPgSimple(session);
app.use(session({
  name: "aurviz.oauth",
  store: new PgSessionStore({ pool: sessionPool, createTableIfMissing: true, tableName: "oauth_sessions" }),
  secret: env.sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: {
    httpOnly: true,
    secure: env.nodeEnv === "production",
    sameSite: "lax",
    maxAge: 10 * 60_000
  }
}));

app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.use("/auth", authRoutes);
app.use("/api/v1/auth", authRoutes);
app.use("/api/v1/emails", emailRoutes);

app.use((error, _req, res, _next) => {
  if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") {
    return res.status(409).json({ error: "This request already exists." });
  }
  if (error?.name === "ZodError") {
    return res.status(400).json({ error: "Request validation failed.", issues: error.issues });
  }
  const status = Number.isInteger(error?.status) ? error.status : 500;
  if (status >= 500) {
    console.error("Request failed", { status, code: error?.code ?? "UNEXPECTED" });
  }
  res.status(status).json({
    error: status >= 500 ? "The server could not complete the request." : error.message
  });
});

let server;
let boss;

async function shutdown(signal) {
  console.info(`Shutting down (${signal}).`);
  if (server) await new Promise(resolve => server.close(resolve));
  if (boss) await boss.stop({ graceful: true, timeout: 30_000 });
  await prisma.$disconnect();
  await sessionPool.end();
  process.exit(0);
}

process.once("SIGINT", () => void shutdown("SIGINT"));
process.once("SIGTERM", () => void shutdown("SIGTERM"));

async function main() {
  boss = new PgBoss({
    connectionString: env.databaseUrl,
    schema: "pgboss",
    application_name: "aurviz-backend"
  });
  boss.on("error", error => console.error("Persistent job queue error", { code: error?.code ?? "QUEUE_ERROR" }));
  await boss.start();

  if (env.role === "all" || env.role === "worker") {
    await startEmailWorker(boss);
  }
  if (env.role === "all" || env.role === "api") {
    app.locals.boss = boss;
    server = app.listen(env.port, "0.0.0.0", () => {
      console.info(`Aurviz API listening on port ${env.port} (${env.role}).`);
    });
  }
}

main().catch(async error => {
  console.error("Backend startup failed", { code: error?.code ?? "STARTUP_ERROR" });
  if (boss) await boss.stop({ graceful: false }).catch(() => {});
  await prisma.$disconnect();
  await sessionPool.end();
  process.exit(1);
});
