# Assignment Submission Readiness

This is a requirements traceability report. "Unit-tested" means automated JVM/Node tests cover the logic. "Device check" means the behaviour depends on a phone, Google account or live service and must be demonstrated manually using the steps below.

## Functional requirements

| ID | Requirement | Implementation | Evidence |
|---|---|---|---|
| FR-01 | Voice input | `VoiceManager` (Android SpeechRecognizer), one-tap mic on Agent screen | Device check |
| FR-02 | Text input | Agent screen text field → `AgentOrchestrator.processUserPrompt` | `AgentOrchestratorTest` |
| FR-03 | Intent detection | Gemini function calling with planning rules in the system instruction | `AgentReliabilityTest.multiStepEmailWorkflow…` |
| FR-04 | Tool calling (Gmail search) | `search_emails`, `read_email`, `create_email_draft`, backend `/api/v1/gmail/*` | `GmailInboxToolsTest`, `backend/test/gmail-inbox.test.js` |
| FR-05 | Persistent memory | Room `memories` with an explicit migration (no destructive reset on upgrade) | Device check: save, force-stop, reopen |
| FR-06 | Memory retrieval | Lexical + recent memories injected into every prompt; `manage_memory recall` | `AgentReliabilityTest.savedPreference…`, `MemoryToolTest` |
| FR-07 | Memory deletion / editing | Memory screen edit + delete (with confirmation); agent `update` / `delete` | `MemoryToolTest` |
| FR-08 | Email sending after confirmation | `send_email` → editable confirmation dialog → backend send with idempotency key | `AgentOrchestratorTest.processUserPrompt_sensitiveTool…` |
| FR-09 | Web automation | Controlled WebView tools: open, search, read, scroll, back/forward, approved click/type | `ControlledWebScraperTest`; device check |
| FR-10 | Task scheduling | `schedule_task` → Room + WorkManager periodic/one-time work; natural-language time parsing | `TimeUtilsTest`, `TaskExecutionPolicyTest` |
| FR-11 | Background execution | `ScheduledTaskWorker` runs the agent with the app closed; backend holds Gmail refresh token | `AgentOrchestratorTest.scheduledTask_*`; device check |
| FR-12 | Multi-step execution | Bounded reason→act loop (8 interactive / 6 background steps) | `AgentReliabilityTest`, `AgentOrchestratorTest` |
| FR-13 | Task history | Room `task_executions`; Tasks → History tab with status, time, duration, attempt | Device check |
| FR-14 | Error reporting | Real error text in chat, Retry for transient errors, step-limit message, failed tool rows | `AgentReliabilityTest.networkFailure…`, `…StepLimit…` |

Also covered: task pause/resume, run-now/retry, edit/delete task, result notifications, Gmail connect/disconnect, permission management shortcut, and a performance dashboard.

## Non-functional requirements

| NFR | How it is addressed |
|---|---|
| Performance | On-device latency recorder with median/p95 in Settings ([PERFORMANCE.md](PERFORMANCE.md)). Bounded context and step limits. |
| Security | OAuth (no passwords). Refresh tokens AES-GCM encrypted server-side only. Session token and Gemini key in Keystore-backed encrypted prefs, excluded from backup. No secrets in git (CI-checkable). Confirmation before sending. |
| Reliability | Failed runs are never marked successful. Transient failures are retried with backoff (`retryable` propagated from tools). Task state is preserved. Ambiguous email delivery is stored as `UNKNOWN`, not resent. |
| Accuracy | Draft-vs-send rule (`create_email_draft` when told not to send). Clarification for ambiguous recipients. "Never invent inbox content" in both modes. |
| Maintainability / scalability | `AgentTool` contract + `ToolRegistry`. A new tool needs no orchestrator change ([ARCHITECTURE.md](ARCHITECTURE.md#extending-maintainability-and-scalability)). |
| Availability / fault tolerance | Offline check before background runs. Network errors become Retry actions, not crashes. Gemini model fallback chain. |
| Usability | One-tap mic, tool timeline, editable confirmation dialogs, readable history, delete confirmations. |
| Resource efficiency | WorkManager only (no persistent services), network constraints, bounded tables and context. |
| Compatibility | minSdk 26 / targetSdk 34. Runtime mic and notification (API 33+) permissions. WorkManager handles Doze and background limits. |

## Verification

### Automated
- `./gradlew app:testDebugUnitTest`: 64 tests pass.
- `./gradlew app:assembleDebug`: builds.
- `cd backend && npm test`: 14 tests pass (Node 22).
- Room migration SQL was compared against Room's generated schema for the new tables and matches.
- CI (`.github/workflows/build.yml`) runs Android tests + APK build and backend tests + `npm audit`.

### On a physical device (realme RMX5116, Android 16), 2026-11-13, against the deployed backend
| Check | Result |
|---|---|
| Upgrade install over the previous build | Chat history and memories kept (no destructive reset) |
| Gmail OAuth connect via backend | Connected as the test account |
| Old session without inbox scope → "check my emails" | Agent showed "reconnect Gmail" message (clear error, no invented data) |
| FR-04/12: "Check my emails from the last 7 days and summarize them" | `search_emails` ran and returned a summary of the real inbox messages |
| Accuracy: "Draft an email to my professor… do not send it" | Agent first asked for the missing address; then `create_email_draft` saved a formal draft, **not sent** |
| FR-08: "Send an email to <test account>…" | Editable approval dialog (To / Subject / Body) shown; after approval the message was queued and a follow-up inbox search found it |
| FR-06: saved preference "formal emails" | Applied to the next draft |
| FR-05: memory survives force-stop and relaunch | Verified |
| FR-07: delete by voice command and on-screen delete (with confirmation) | Both verified |
| FR-10/11/13: "Every morning at 9 AM…" | Daily task created ("Daily at 09:00", next run shown). "Run now" executed through WorkManager: Success row in History with duration and summary, plus a notification |
| FR-09: "Open news.ycombinator.com and tell me the top 3 stories" | Controlled browser opened the site on its own, the page was read, and the top 3 stories were listed in chat |
| Performance table | 30 samples recorded on the device (see PERFORMANCE.md) |
| Light and dark themes | Both checked |

### Not verified on the device
- FR-01 voice input in this session. Voice-sourced messages exist in the chat history from earlier runs of the app, but speech was not re-tested.
- Offline behaviour (Retry button and "Retrying" history state) is covered by unit tests only. Toggling Wi-Fi would have dropped the wireless debugging link.
- A background run with the app fully closed and the device idle for hours (Doze). "Run now" ran the real WorkManager worker, but the scheduled 09:00 trigger has not been observed yet.

## Manual demo script (device checks)

1. **Setup:** Settings → enter the Gemini key → Connect Gmail (grant send, read and compose).
2. **FR-05/06:** Say "Remember that I prefer formal emails." Force-stop the app and reopen it. Memory tab shows the entry.
3. **Accuracy:** "Draft an email to my professor about extending the deadline, don't send it." Expect `create_email_draft` with a formal tone, and the draft appears in Gmail Drafts. With a name-only recipient, the agent should ask for the address.
4. **FR-04/12:** "Check my emails, find interview invitations, and summarize them." Expect timeline rows `search_emails` → `read_email` → summary.
5. **FR-08:** "Send an email to `<your address>` saying I will be late." Confirmation dialog appears; approve; the email arrives.
6. **FR-10/11/13:** "Every morning at 9 AM, check my important emails and summarize them." Tasks tab shows the task; tap Run now, then close the app. Expect a notification, and History shows a Success row.
7. **FR-14 / fault tolerance:** Turn on airplane mode and send a prompt. Expect an error with Retry. Run a task offline: History shows Retrying, and it succeeds after the network returns.
8. **FR-07:** "Forget my email tone preference." The memory is removed; the edit icon updates another memory.
9. **FR-09:** "Open news.ycombinator.com and tell me the top 3 stories."

## Known limits (stated honestly)

- WorkManager periodic work is not exact-to-the-minute; Android may defer it under Doze.
- `gmail.readonly` is a restricted Google scope: it works for OAuth test users in Testing mode, and public release requires Google verification.
- Accounts connected before inbox scopes were added must disconnect/reconnect; read calls return a clear reconnect message until then.
- Memory retrieval is lexical, not semantic/vector.
- The controlled WebView is not a hardened sandbox; avoid authenticated or financial sites.
- Gemini key entered on-device is extractable on rooted devices; production should proxy Gemini through the backend.
- Recurrence is daily or one-time; weekday-specific rules are not implemented.
