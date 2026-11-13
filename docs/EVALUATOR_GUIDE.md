# Evaluator Guide

This guide lets you test every feature of Aurviz in about ten minutes **without anyone's password or Google account**.

## 1. Install

1. Install the APK on an Android 8.0+ phone (allow "install unknown apps" for your file manager or browser).
2. Open **Aurviz**. You will see the animated logo, then a short walkthrough.
3. On the last page choose **Start with the demo mailbox**. (You can switch later in Settings, Gmail, Demo mailbox.)
4. Allow the microphone and notification permissions when asked.

The AI key is bundled in the build, so there is nothing to configure. If your build has no bundled key, open Settings, AI model and paste a free Gemini key from <https://aistudio.google.com/>.

## 2. What the demo mailbox contains

Ten realistic sample emails: an interview invitation, a campus placement drive, a reply from a professor about a deadline, an application acknowledgement, a GitHub notification, a bank security alert, a friend asking about the weekend, job alerts, a newsletter and an invoice. Nothing you do in demo mode is ever sent to a real address.

## 3. Ten-minute test script

Type each prompt (or tap the mic and say it). The badge **DEMO** in the header confirms demo mode.

| # | Say or type | What you should see | Requirement |
|---|---|---|---|
| 1 | "Check my emails, find interview invitations and summarize them" | Cards "Search Gmail" then "Read email", then a structured summary with the Monday 10:30 interview | FR-03, 04, 12, 15 |
| 2 | "Which of my emails are important and unread?" | Placement drive and interview invitation | FR-15 |
| 3 | "Remember that I prefer formal emails" | Memory saved card; the Memory tab lists it | FR-05 |
| 4 | "Draft an email to Prof. Rao asking about my grade, but don't send it" | It asks for the address if unknown, then saves a **formal** draft; nothing is sent | FR-06, 16 |
| 5 | "Find my drafts" | The draft you just saved (`in:drafts`) | FR-16 |
| 6 | "Send an email to rahul@example.com saying I will be late" | An **approval dialog** with editable To, Subject, Body. Cancel sends nothing; approve records it in the demo outbox | FR-08 |
| 7 | "Every morning at 9 AM, check my important emails and summarize them" | Task created. Open **Tasks**, tap **Run now**; a notification arrives and **History** shows the run | FR-10, 11, 13 |
| 8 | "Forget my email tone preference" | Memory deleted | FR-07 |
| 9 | "Open news.ycombinator.com and tell me the top 3 stories" | The in-app browser opens and the stories are listed | FR-09 |
| 10 | In the browser open any Wikipedia article, tap **Summarize** | Back in chat with a summary of that page | FR-22 |

## 4. Other things to look at

- **Settings, Appearance:** System, Light and Dark themes.
- **Browser menu (three dots):** Find in page, Reader view, Desktop site, text size, Share, Copy link, Open in Chrome. Star button for bookmarks; library button for bookmarks and history.
- **Tasks:** pause and resume a daily task; the History tab explains failures in plain words with a next step.
- **Settings, Performance:** latency measured on your device (median and p95).
- **Long-press the app icon:** shortcuts for Voice command, Summarize inbox and Tasks.
- **Share text** from any app to Aurviz to get a summary.
- **Failure handling:** switch on airplane mode and send a prompt. You get a clear message and a **Try again** button.

## 5. Testing with a real Gmail account (optional)

Google only allows listed accounts to grant the Gmail read scope while an app is in testing mode. Two ways to run the live path:

1. **Add the evaluator's account as a test user** (project owner, one minute): Google Cloud Console, APIs and Services, OAuth consent screen, Audience, Test users, Add users. The evaluator then taps Settings, Connect Gmail, picks their own account, and approves. They never share a password.
2. **Publish the consent screen "In production"** (unverified). Any Google user can then continue through the "Google hasn't verified this app" warning (Advanced, Go to Aurviz), limited to 100 users until Google verifies the app.

In both cases the evaluator signs in on Google's own page. Aurviz never sees the password.

## 6. Mapping to common evaluation criteria

| Criterion | Where to look |
|---|---|
| AI reasoning and tool calling | Script 1, 4, 6; the tool cards in chat; [Architecture](ARCHITECTURE.md#4-the-agent-loop) |
| Modular architecture | [Architecture](ARCHITECTURE.md), the `domain`, `data`, `presentation`, `service` packages |
| Persistent memory | Script 3, 4, 8; restart the app and check the Memory tab |
| Background scheduling | Script 7; [Architecture, scheduled tasks](ARCHITECTURE.md#6-scheduled-tasks) |
| OAuth and secure email | Section 5 above; [Security](ARCHITECTURE.md#10-security) |
| Error handling and reliability | Airplane-mode test; Tasks, History; [Test Report](TEST_REPORT.md) |
| Performance measurement | Settings, Performance; [Performance](PERFORMANCE.md) |
| UI and UX | Whole app, dark mode, onboarding, animations |
