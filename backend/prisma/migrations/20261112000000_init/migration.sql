CREATE TYPE "ScheduledEmailStatus" AS ENUM ('SCHEDULED', 'PROCESSING', 'UPDATING', 'SENT', 'FAILED', 'CANCELLED', 'UNKNOWN');

CREATE TABLE "GoogleAccount" (
    "id" UUID NOT NULL,
    "email" TEXT NOT NULL,
    "encryptedRefreshToken" TEXT NOT NULL,
    "disconnectedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "GoogleAccount_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "AppAuthCode" (
    "id" UUID NOT NULL,
    "codeHash" TEXT NOT NULL,
    "accountId" UUID NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "usedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "AppAuthCode_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "DeviceSession" (
    "id" UUID NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "accountId" UUID NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "revokedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "DeviceSession_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "ScheduledEmail" (
    "id" UUID NOT NULL,
    "accountId" UUID NOT NULL,
    "recipient" TEXT NOT NULL,
    "subject" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "timeZone" TEXT NOT NULL,
    "status" "ScheduledEmailStatus" NOT NULL DEFAULT 'SCHEDULED',
    "idempotencyKey" TEXT NOT NULL,
    "pgBossJobId" TEXT,
    "jobVersion" INTEGER NOT NULL DEFAULT 1,
    "messageId" TEXT,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "executionAt" TIMESTAMP(3),
    "lastError" VARCHAR(500),
    CONSTRAINT "ScheduledEmail_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "EmailExecution" (
    "id" UUID NOT NULL,
    "scheduledEmailId" UUID NOT NULL,
    "attempt" INTEGER NOT NULL,
    "status" "ScheduledEmailStatus" NOT NULL,
    "errorDetails" VARCHAR(500),
    "gmailMessageId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "EmailExecution_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "GoogleAccount_email_key" ON "GoogleAccount"("email");
CREATE UNIQUE INDEX "AppAuthCode_codeHash_key" ON "AppAuthCode"("codeHash");
CREATE INDEX "AppAuthCode_expiresAt_idx" ON "AppAuthCode"("expiresAt");
CREATE UNIQUE INDEX "DeviceSession_tokenHash_key" ON "DeviceSession"("tokenHash");
CREATE INDEX "DeviceSession_accountId_expiresAt_idx" ON "DeviceSession"("accountId", "expiresAt");
CREATE UNIQUE INDEX "ScheduledEmail_idempotencyKey_key" ON "ScheduledEmail"("idempotencyKey");
CREATE INDEX "ScheduledEmail_accountId_createdAt_idx" ON "ScheduledEmail"("accountId", "createdAt");
CREATE INDEX "ScheduledEmail_accountId_status_scheduledAt_idx" ON "ScheduledEmail"("accountId", "status", "scheduledAt");
CREATE UNIQUE INDEX "EmailExecution_scheduledEmailId_attempt_key" ON "EmailExecution"("scheduledEmailId", "attempt");
CREATE INDEX "EmailExecution_scheduledEmailId_createdAt_idx" ON "EmailExecution"("scheduledEmailId", "createdAt");

ALTER TABLE "AppAuthCode" ADD CONSTRAINT "AppAuthCode_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "GoogleAccount"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "DeviceSession" ADD CONSTRAINT "DeviceSession_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "GoogleAccount"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ScheduledEmail" ADD CONSTRAINT "ScheduledEmail_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "GoogleAccount"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "EmailExecution" ADD CONSTRAINT "EmailExecution_scheduledEmailId_fkey" FOREIGN KEY ("scheduledEmailId") REFERENCES "ScheduledEmail"("id") ON DELETE CASCADE ON UPDATE CASCADE;
