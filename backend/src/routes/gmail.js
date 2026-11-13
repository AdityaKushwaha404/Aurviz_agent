import { Router } from "express";
import { z } from "zod";
import { requireDeviceSession } from "../middleware/device-auth.js";
import { createGmailClient } from "../services/google.js";
import { createInboxService, MAX_SEARCH_RESULTS } from "../services/gmail-inbox.js";
import { sendEmailSchema } from "../utils/validation.js";

const inbox = createInboxService(createGmailClient);
const router = Router();
router.use(requireDeviceSession);

const searchSchema = z.object({
  q: z.string().max(500).optional(),
  max: z.coerce.number().int().min(1).max(MAX_SEARCH_RESULTS).optional()
});
const messageId = z.string().regex(/^[A-Za-z0-9_-]{6,64}$/, "Invalid message id.");

router.get("/messages", async (req, res, next) => {
  try {
    const input = searchSchema.parse(req.query);
    res.set("Cache-Control", "no-store").json(
      await inbox.search(req.accountId, { query: input.q?.trim(), maxResults: input.max })
    );
  } catch (error) {
    next(error);
  }
});

router.get("/messages/:id", async (req, res, next) => {
  try {
    const id = messageId.parse(req.params.id);
    res.set("Cache-Control", "no-store").json(await inbox.read(req.accountId, id));
  } catch (error) {
    next(error);
  }
});

router.post("/drafts", async (req, res, next) => {
  try {
    const input = sendEmailSchema.parse(req.body);
    res.status(201).json(await inbox.createDraft(req.accountId, input));
  } catch (error) {
    next(error);
  }
});

export default router;
