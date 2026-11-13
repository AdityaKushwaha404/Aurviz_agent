# Test Report

**Build under test:** Aurviz 1.1 debug build, commit history on `main`
**Date:** 13 November 2026
**Device:** realme RMX5116, Android 16 (API 36), Wi-Fi
**Backend:** deployed instance (`/health` OK), Gmail test account in OAuth testing mode

## 1. Summary

| Area | Result |
|---|---|
| Android unit tests | **86 of 86 pass** |
| Backend tests | **18 of 18 pass** (Node 22) |
| Device checks | **All listed in section 3 pass** |
| Build | `assembleDebug` succeeds; CI runs tests and the build on every push |
| Dependency audit | `npm audit` reports no known vulnerabilities |

Not verified on the device (stated openly): speech input in the latest build; offline Retry behaviour (unit-tested only, because switching Wi-Fi off would disconnect the wireless debugging link); the real 09:00 trigger firing unattended overnight; and the relaxed recipient rule (example.com addresses) on the deployed server, which needs the latest backend version deployed; its logic is unit-tested.

## 2. Automated tests

### 2.1 Android (JVM, `./gradlew app:testDebugUnitTest`)

| Suite | Tests | What it proves |
|---|---:|---|
| `AgentOrchestratorTest` | 8 | Direct answers, conversation persistence, function-call id round-trip, multi-step background runs, sensitive tools never run in the background, approval flow with edited fields |
| `AgentReliabilityTest` | 10 | Real errors with retry (no generic greeting), step limit never silent, multi-step email workflow, preference injected into the prompt, transient tool failure is retryable, latency recorded, empty model answer retried then reported, background runs never use screen-bound tools |
| `GeminiAgentClientTest` | 11 | Request building, model fallback, error classification, response parsing, thought-signature preservation |
| `TaskExecutionPolicyTest` | 11 | Success, skip, retry and failure decisions; recurring tasks stay registered; backoff limits |
| `TimeUtilsTest` | 6 | Natural-language time parsing and next-run delay |
| `MailGatewayTest` | 2 | Demo drafts and sent mail survive a restart and reset restores the sample; the gateway routes by source and raises a revision signal on every change |
| `DemoMailServiceTest` | 6 | Gmail-style operators, ordering and caps, read and 404, drafts and sent folders, idempotent send |
| `ExecutionLogFormatterTest` | 6 | Raw errors become plain language with a next step |
| `MemoryToolTest` | 4 | Upsert by key, update, delete, ambiguous delete deletes nothing, recall |
| `EmailTaskContextTest` | 4 | Email-task detection and safe instructions |
| `BrowserUrlTest` | 5 | Domain, http upgrade, search fallback, blank input, host display |
| `GmailInboxToolsTest` | 3 | Result formatting, no invented data, transient versus permanent error classification |
| `ControlledWebScraperTest` | 3 | Public-page extraction and private-address blocking |
| `MarkdownParserTest` | 3 | Bold, italic, code, lists, malformed markers |
| `SchedulerToolTest` | 2 | The model's instruction is stored unchanged; invalid time rejected |
| `MetricMathTest` | 2 | Percentiles and summary statistics |
| **Total** | **86** | |

### 2.2 Backend (`npm test`)

| Suite | Tests | What it proves |
|---|---:|---|
| `shared-account.test.js` | 4 | Test account detection; it may send only to itself or reserved example addresses (real third parties rejected); it cannot be disconnected; normal accounts are unrestricted |
| `gmail-inbox.test.js` | 7 | MIME body extraction, HTML-to-text, header lookup, raw draft building and header-injection rejection, Google error mapping, result cap, disconnected-account error |
| `validation.test.js` | 3 | Recipient, time zone and offset validation; injection rejected |
| `token-vault.test.js` | 2 | Refresh-token encryption and tamper detection |
| `worker.test.js` | 2 | Durable queue provisioning |
| **Total** | **18** | |

### 2.3 Database migration
The `MIGRATION_1_2` SQL was compared against the schema Room generates for the new tables. They match exactly. On a physical device an upgrade install kept the existing chat history and memories.

## 3. Device test log

Tested on the physical device against the deployed backend.

| # | Requirement | Steps | Expected | Result |
|---|---|---|---|---|
| D1 | FR-18 | Settings, Connect Gmail, choose account, grant | Connected as the account | Pass |
| D2 | FR-14 | Ask for email with an old session that lacks the inbox scope | Clear "reconnect Gmail" message, no invented data | Pass |
| D3 | FR-03, 04, 12, 15 | "Check my emails from the last 7 days and summarize them" | `search_emails` runs; summary of real messages | Pass |
| D4 | FR-03, 16 | "Draft an email to my professor asking for an extension. Do not send it." | Asks for the missing address, then saves a formal draft, nothing sent | Pass |
| D5 | FR-08 | "Send an email to the test account…" | Approval dialog; after approval the mail is delivered | Pass (found by a follow-up inbox search) |
| D6 | FR-06 | Saved preference "formal emails", then drafted | Draft is formal | Pass |
| D7 | FR-05 | Save a memory, force-stop, relaunch | Memory still listed | Pass |
| D8 | FR-07 | Delete a memory by voice command and from the screen | Removed; screen asks for confirmation | Pass |
| D9 | FR-10, 11, 13 | "Every morning at 9 AM…", then Run now | Daily task created; Success row in History; notification | Pass |
| D10 | FR-09 | "Open news.ycombinator.com and tell me the top 3 stories" | Browser opens, top stories listed | Pass |
| D11 | FR-17 | Demo mailbox on, "find interview invitations and summarize" | Search and read run on sample data; accurate summary | Pass |
| D12 | FR-21 | Address bar with a domain; browser controls | Page loads; controls respond | Pass |
| D13 | FR-22 | Open a Wikipedia article, tap Summarize | Returns to chat with a structured summary of that page | Pass |
| D14 | FR-23 | Theme selector System, Light, Dark | Applies immediately | Pass |
| D15 | FR-24 | First launch | Splash animation then onboarding | Pass |
| D16 | FR-25 | Change the default recipient email and save | New value shown and kept | Pass |
| D17 | NFR-01 | 30 simple prompts | Median 1.6 s, on-device overhead about 0.1 s | Pass |
| D18 | FR-27, 28 | Open the mailbox viewer in demo mode | Inbox with unread in bold; tabs for Sent and Drafts | Pass |
| D19 | FR-27 | Ask Aurviz to draft an email, then to send one, then open the Drafts and Sent tabs | The Drafts and Sent tabs show the new messages | Pass |
| D20 | FR-28 | Settings, Mailbox: select Demo mailbox | Header badge DEMO; the agent uses the sample inbox | Pass |
| D21 | FR-26 | Settings, Mailbox, Test Gmail account | Email, masked password with show/copy, six numbered steps | Pass |
| D23 | FR-26 | Follow the steps: Sign in with Google, choose the test account, Continue past the unverified-app warning | Back in Aurviz showing "Connected as adityatesting404@gmail.com"; "Search my inbox" returns 15 real messages and a correct summary | Pass |
| D22 | FR-20 | History after a manual run | Plain headline, duration and formatted summary | Pass |

### Defects found during device testing, and fixed
| Defect | Fix |
|---|---|
| A background task could pop the browser screen open or wait for it | Screen-bound tools are refused in background runs; regression test added |
| Agent tab showed Settings after opening Settings from the sign-in banner (tab back stacks nested) | Links use the same tab navigation as the bottom bar |
| Search label showed `'{}'` when the model sent an empty query | Symbol-only queries mean "recent inbox" |
| Scheduler rewrote the model's instruction by keyword matching ("summarize" became "draft responses") | Instruction now stored unchanged; regression test added |
| Background email tasks told the model the wrong account address | Removed the claim; the tools use the connected account |
| A one-off network error left the Gmail status stuck on screen | Status re-checks on opening Settings; Retry button added |
| Model sometimes returned a lone "-" as its final answer | Token limit raised; one automatic retry; then a retryable error |
| Cold-start of the free-tier backend caused "could not reach" | Warm-up request at launch; timeouts allow a 45 s wake-up |
| Status-bar text invisible on light backgrounds | Icons now follow the active theme |

## 4. Requirement-to-evidence matrix

| Requirement | Automated | Device |
|---|---|---|
| FR-01 Voice input | n/a (platform API) | Earlier build |
| FR-02 Text input | `AgentOrchestratorTest` | D3 |
| FR-03 Intent detection | `AgentReliabilityTest` | D3, D4 |
| FR-04 Tool calling | `AgentOrchestratorTest`, `GeminiAgentClientTest` | D3 |
| FR-05 Persistent memory | `MemoryToolTest` | D7 |
| FR-06 Memory retrieval | `AgentReliabilityTest`, `MemoryToolTest` | D6 |
| FR-07 Memory edit/delete | `MemoryToolTest` | D8 |
| FR-08 Send after confirmation | `AgentOrchestratorTest` | D5 |
| FR-09 Web automation | `ControlledWebScraperTest`, `BrowserUrlTest` | D10 |
| FR-10 Scheduling | `SchedulerToolTest`, `TimeUtilsTest` | D9 |
| FR-11 Background execution | `TaskExecutionPolicyTest` | D9 (Run now through WorkManager) |
| FR-12 Multi-step | `AgentReliabilityTest` | D3, D11 |
| FR-13 Task history | n/a | D9 |
| FR-14 Error reporting | `AgentReliabilityTest`, `ExecutionLogFormatterTest` | D2 |
| FR-15 Search and read | `DemoMailServiceTest`, `gmail-inbox.test.js` | D3, D11 |
| FR-16 Drafts | `GmailInboxToolsTest` | D4 |
| FR-17 Demo mailbox | `DemoMailServiceTest` | D11 |
| FR-18 OAuth | `token-vault.test.js` | D1 |
| FR-19 Task management | `TaskExecutionPolicyTest` | D9 |
| FR-20 Readable results | `ExecutionLogFormatterTest` | Screen check |
| FR-21 Browser tools | `BrowserUrlTest` | D12 |
| FR-22 AI on pages | n/a | D13 |
| FR-23 Theme | n/a | D14 |
| FR-24 Onboarding, shortcuts | n/a | D15 |
| FR-25 Settings | n/a | D16 |
| FR-26 Test Gmail account | `shared-account.test.js` | D21, D23 |
| FR-27 Mailbox viewer | `MailGatewayTest` | D18, D19 |
| FR-28 Mailbox selection | `MailGatewayTest` | D20 |
| NFR-01 Performance | `MetricMathTest` | D17 |
| NFR-02 Security | `token-vault.test.js`, `validation.test.js` | D1, D5 |
| NFR-03 Reliability | `TaskExecutionPolicyTest`, `AgentReliabilityTest` | D9 |
| NFR-07 Fault tolerance | `AgentReliabilityTest` | D2 |

## 5. How to reproduce

```sh
./gradlew app:testDebugUnitTest        # 86 Android tests
./gradlew app:assembleDebug            # build the APK
cd backend && npm ci && npm test       # 18 backend tests
```
Device checks follow the [Evaluator Guide](EVALUATOR_GUIDE.md).
