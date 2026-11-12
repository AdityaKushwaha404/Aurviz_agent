import test from "node:test";
import assert from "node:assert/strict";
import { EMAIL_QUEUE } from "../src/services/email-queue.js";
import { ensureEmailQueue, startEmailWorker } from "../src/scheduler/worker.js";

test("email worker creates its queue and registers before reconciling pending jobs", async () => {
  const calls = [];
  const boss = {
    async createQueue(name) {
      calls.push(["createQueue", name]);
    },
    async work(name, options) {
      calls.push(["work", name, options.batchSize]);
    }
  };
  const reconcile = async queueBoss => {
    assert.equal(queueBoss, boss);
    calls.push(["reconcile"]);
  };

  await startEmailWorker(boss, {
    reconcileUnqueuedEmails: reconcile,
    processEmailJob: async () => {}
  });

  assert.deepEqual(calls, [
    ["createQueue", EMAIL_QUEUE],
    ["reconcile"],
    ["work", EMAIL_QUEUE, 1]
  ]);
});

test("API-only queue provisioning uses the same durable queue name", async () => {
  const created = [];
  await ensureEmailQueue({ createQueue: async name => created.push(name) });
  assert.deepEqual(created, [EMAIL_QUEUE]);
});
