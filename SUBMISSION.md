# Submission Summary

**Aurviz: a personal AI agent for Android.** One-page status against the assignment. Details live in the linked documents.

| Document | Link |
|---|---|
| Requirements (FR/NFR, acceptance criteria) | [docs/SRS.md](docs/SRS.md) |
| Architecture and design decisions | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Test report and requirement-to-evidence matrix | [docs/TEST_REPORT.md](docs/TEST_REPORT.md) |
| Performance study | [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| Evaluator guide (test account steps or demo mailbox) | [docs/EVALUATOR_GUIDE.md](docs/EVALUATOR_GUIDE.md) |
| Email flow with proof screenshots | [docs/EMAIL_FLOW.md](docs/EMAIL_FLOW.md) |
| Browser feature guide | [docs/BROWSER.md](docs/BROWSER.md) |
| User guide, API, deployment | [docs/USER_GUIDE.md](docs/USER_GUIDE.md), [docs/API.md](docs/API.md), [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |

## Status at a glance

| Area | Status |
|---|---|
| Functional requirements FR-01 to FR-28 | Implemented. Device-verified, except speech input in the latest build (see the Test Report) |
| Non-functional requirements NFR-01 to NFR-11 | Implemented and evidenced |
| Android unit tests | 86 pass |
| Backend tests | 18 pass |
| On-device performance | 30 samples: median 1.6 s end-to-end, about 0.1 s on-device overhead |
| Evaluation without any credentials | Test Gmail account with credentials and steps shown in the app, plus an offline demo mailbox with a live viewer |

## The three engineering concerns, kept separate

| Concern | Owner |
|---|---|
| AI reasoning | Gemini decides what needs to happen (function calling) |
| Tool execution | Kotlin tools and the backend perform the operation |
| Scheduling | Android WorkManager triggers execution later |

Gemini cannot wake the app, so scheduled tasks are persisted in Room and registered with WorkManager. WorkManager timing is durable but not exact to the minute.

## Priority areas from the brief

| Priority | Area | Where it is shown |
|---|---|---|
| P0 | AI reasoning and tool calling | Tool timeline in chat; [Architecture section 4](docs/ARCHITECTURE.md#4-the-agent-loop) |
| P0 | Modular architecture | `domain`, `data`, `presentation`, `service` packages; [Architecture section 3](docs/ARCHITECTURE.md#3-layers-of-the-android-app) |
| P0 | Persistent memory | Memory tab; survives restart; used automatically in prompts |
| P0 | Background scheduling | Tasks tab, History; [Architecture section 6](docs/ARCHITECTURE.md#6-scheduled-tasks) |
| P1 | OAuth and secure email | Backend OAuth, encrypted tokens, approval before send |
| P1 | Error handling and reliability | Plain-language errors, retries, history; [Test Report](docs/TEST_REPORT.md) |
| P1 | Performance measurement | Settings, Performance panel; [Performance](docs/PERFORMANCE.md) |
| P1 | UI/UX and task history | Dark mode, onboarding, animations, task history |

## Honest limits

- WorkManager is not an exact scheduler; Android may delay tasks.
- `gmail.readonly` is a Google restricted scope. Real accounts must be test users until Google verifies the app; the demo mailbox removes this barrier for evaluation.
- Not verified on the device: speech input in the latest build, offline Retry (unit-tested), the 09:00 trigger firing unattended.
- Memory search is lexical, recurrence is daily or one-time, and the browser is a controlled WebView rather than a hardened sandbox.
- A Gemini key bundled into an APK can be extracted; production should proxy Gemini through the backend.
