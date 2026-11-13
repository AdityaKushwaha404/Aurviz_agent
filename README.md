<p align="center">
  <img src="docs/assets/logo.png" alt="Aurviz logo" width="132"/>
</p>

<h1 align="center">Aurviz</h1>
<p align="center"><b>A personal AI agent for Android that understands, remembers, plans and acts.</b></p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="AI" src="https://img.shields.io/badge/AI-Gemini%20function%20calling-4285F4?logo=google&logoColor=white">
  <img alt="Tests" src="https://img.shields.io/badge/tests-86%20Android%20%2B%2018%20backend%20passing-brightgreen">
</p>

Aurviz is more than a voice-enabled chatbot. It listens to spoken or typed instructions, remembers your preferences, plans multi-step work, **uses tools** (email, memory, scheduler, web browser) and **executes tasks**, including scheduled ones that run while the app is closed.

> **Evaluating this project?** You need no credentials. Install the APK and tap **Use the shared test inbox** (real Gmail on a throwaway account, one tap, no password), or choose the offline **demo mailbox**. Then follow the ten-minute script in the **[Evaluator Guide](docs/EVALUATOR_GUIDE.md)**.

## What it can do

| | |
|---|---|
| **Understand** | Voice and text commands. Gemini decides which tool to use and breaks complex requests into steps |
| **Email** | Search, read and summarize mail; save drafts; send only after you approve To, Subject and Body in an editable dialog. A built-in **mailbox viewer** shows Inbox, Sent and Drafts and updates live |
| **Three mailboxes** | **Shared test inbox** (real Gmail, no credentials, self-send only), **Demo mailbox** (offline sample data you can view and reset), or **your own Gmail** (Google sign-in) |
| **Remember** | Saves preferences ("I prefer formal emails") and applies them to later answers. View, edit and delete memories |
| **Schedule** | "Every morning at 9, summarize my inbox" runs in the background with retries, pause and resume, notifications and a full history |
| **Browse** | A controlled in-app browser with search, find in page, reader view, bookmarks, and AI actions that summarize or answer questions about the open page |
| **Stay safe** | OAuth (no passwords), encrypted tokens, approval before any send, background runs can never send |
| **Explain failures** | Plain-language errors with a next step and a Try again button |

## Screenshots

<table>
<tr>
<td align="center"><img src="docs/screenshots/19-onboarding-mailbox-choice.png" width="165"><br><sub>Choose a mailbox</sub></td>
<td align="center"><img src="docs/screenshots/02-chat-email-summary.png" width="165"><br><sub>Search, read, summarize</sub></td>
<td align="center"><img src="docs/screenshots/03-send-approval.png" width="165"><br><sub>Approval before sending</sub></td>
<td align="center"><img src="docs/screenshots/07-mailbox-inbox.png" width="165"><br><sub>Mailbox viewer</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/06-mailbox-drafts.png" width="165"><br><sub>Drafts update live</sub></td>
<td align="center"><img src="docs/screenshots/14-task-history.png" width="165"><br><sub>Readable task history</sub></td>
<td align="center"><img src="docs/screenshots/11-browser-ai-actions.png" width="165"><br><sub>Browser with AI actions</sub></td>
<td align="center"><img src="docs/screenshots/15-chat-dark.png" width="165"><br><sub>Dark theme</sub></td>
</tr>
</table>

## How it works

```mermaid
flowchart LR
    U([You]) -- voice / text --> A[Agent loop]
    A -- reasoning --> G[(Gemini)]
    A -- executes --> T[Tools: email, memory, scheduler, browser]
    T --> M{Mail}
    M -- shared test inbox / own Gmail --> B[Backend + Gmail API]
    M -- demo --> D[Sample mailbox]
    S[WorkManager] -- wakes later --> A
```

Three concerns, kept separate: **Gemini reasons**, **Kotlin tools execute**, **WorkManager schedules**. Read the [Architecture](docs/ARCHITECTURE.md) for diagrams and design decisions.

## Quick start

```sh
git clone https://github.com/AdityaKushwaha404/Aurviz_agent.git
cd Aurviz_agent
echo "gemini.api.key=YOUR_GEMINI_KEY" >> local.properties   # optional, bundles a key into the APK
./gradlew app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the tests:
```sh
./gradlew app:testDebugUnitTest     # 86 Android tests
cd backend && npm ci && npm test    # 18 backend tests
```

## Documentation

| Document | Purpose |
|---|---|
| [Evaluator Guide](docs/EVALUATOR_GUIDE.md) | Test every feature in ten minutes without credentials |
| [Software Requirements Specification](docs/SRS.md) | Functional and non-functional requirements with acceptance criteria |
| [Architecture](docs/ARCHITECTURE.md) | Design, diagrams, security, decisions and trade-offs |
| [Test Report](docs/TEST_REPORT.md) | Automated and on-device results, requirement-to-evidence matrix |
| [Performance](docs/PERFORMANCE.md) | Latency measured on a physical device |
| [User Guide](docs/USER_GUIDE.md) | How to use every feature |
| [Backend API](docs/API.md) | REST endpoints and delivery semantics |
| [Build and Deployment](docs/DEPLOYMENT.md) | Building the APK, running the backend, Google Cloud setup |
| [Submission summary](SUBMISSION.md) | One-page status against the assignment |

A single printable PDF of these documents is generated by `python3 docs/build_report.py` (see [docs/Aurviz_Project_Report.pdf](docs/Aurviz_Project_Report.pdf)).

## Project structure

```
app/                     Android app (Kotlin, Compose, Room, WorkManager)
  presentation/          Screens and ViewModels
  domain/                Orchestrator, tool contract, mail abstraction, models (pure Kotlin)
  data/                  Gemini client, mail services, Room, tools
  service/               Background worker, voice, browser controller
backend/                 Node.js + PostgreSQL service (OAuth, Gmail API, job queue)
docs/                    Documentation and logo
```

## Known limitations

- WorkManager is durable but not exact to the minute; Android may delay tasks.
- `gmail.readonly` is a Google restricted scope: your own Gmail must be an OAuth test user until the app is verified. The shared test inbox and the demo mailbox avoid this for evaluation.
- Memory search matches words, not meaning.
- The controlled browser is not a hardened sandbox; avoid sign-in or payment pages.
- A Gemini key bundled into an APK can be extracted; production should proxy Gemini through the backend.

## Author
Aditya Kushwaha
