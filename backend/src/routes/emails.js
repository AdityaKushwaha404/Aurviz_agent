import { Router } from "express";
import { requireDeviceSession } from "../middleware/device-auth.js";
import {
  cancelEmail,
  getEmail,
  listEmails,
  scheduleEmail,
  updateEmail
} from "../services/email-service.js";
import { scheduleEmailSchema, sendEmailSchema, updateEmailSchema } from "../utils/validation.js";

const router = Router();
router.use(requireDeviceSession);

function parseIdempotencyKey(req, res) {
  const value = req.get("idempotency-key");
  if (!value || value.length < 8 || value.length > 120 || !/^[A-Za-z0-9._:-]+$/.test(value)) {
    res.status(400).json({ error: "Send an Idempotency-Key header between 8 and 120 safe characters." });
    return null;
  }
  return value;
}

router.post("/scheduled", async (req, res, next) => {
  try {
    const idempotencyKey = parseIdempotencyKey(req, res);
    if (!idempotencyKey) return;
    const input = scheduleEmailSchema.parse(req.body);
    if (input.scheduledAt.getTime() < Date.now() + 30_000) {
      return res.status(400).json({ error: "Scheduled send time must be at least 30 seconds in the future." });
    }
    const email = await scheduleEmail(req.app.locals.boss, req.accountId, input, idempotencyKey);
    res.status(202).json(email);
  } catch (error) {
    next(error);
  }
});

router.post("/send", async (req, res, next) => {
  try {
    const idempotencyKey = parseIdempotencyKey(req, res);
    if (!idempotencyKey) return;
    const input = sendEmailSchema.parse(req.body);
    const email = await scheduleEmail(
      req.app.locals.boss,
      req.accountId,
      { ...input, scheduledAt: new Date(), timeZone: "UTC" },
      idempotencyKey,
      { ignoreScheduledAt: true }
    );
    res.status(202).json(email);
  } catch (error) {
    next(error);
  }
});

router.get("/scheduled", async (req, res, next) => {
  try {
    res.json(await listEmails(req.accountId));
  } catch (error) {
    next(error);
  }
});

router.get("/scheduled/:id", async (req, res, next) => {
  try {
    const email = await getEmail(req.accountId, req.params.id);
    if (!email) return res.status(404).json({ error: "Scheduled email was not found." });
    res.json(email);
  } catch (error) {
    next(error);
  }
});

router.patch("/scheduled/:id", async (req, res, next) => {
  try {
    const input = updateEmailSchema.parse(req.body);
    const email = await updateEmail(req.app.locals.boss, req.accountId, req.params.id, input);
    if (!email) return res.status(404).json({ error: "Scheduled email was not found." });
    res.json(email);
  } catch (error) {
    next(error);
  }
});

router.delete("/scheduled/:id", async (req, res, next) => {
  try {
    const email = await cancelEmail(req.app.locals.boss, req.accountId, req.params.id);
    if (!email) return res.status(404).json({ error: "Scheduled email was not found." });
    res.json(email);
  } catch (error) {
    next(error);
  }
});

export default router;
