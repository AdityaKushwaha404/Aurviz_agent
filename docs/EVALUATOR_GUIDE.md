# Evaluator Guide

Test every feature of Aurviz in about ten minutes. **You do not need a password, a Google account or any setup.** Aurviz offers three mailboxes, and the credentials of a dedicated test Gmail account are shown inside the app.

## 1. Install and first launch

1. Install the APK on an Android 8.0 or newer phone (allow "install unknown apps" for your file manager or browser).
2. Open **Aurviz**. You see the animated logo, then a short walkthrough.
3. On the last page tap **Show me the sign-in steps**. Settings opens with the test account's **email and password** (tap the copy icons) and six numbered steps. Follow them (about one minute).
4. Allow the microphone and notification permissions when asked.

<table>
<tr>
<td align="center"><img src="screenshots/16-splash.png" width="170"><br><sub>Animated splash</sub></td>
<td align="center"><img src="screenshots/17-onboarding-1.png" width="170"><br><sub>Welcome</sub></td>
<td align="center"><img src="screenshots/18-onboarding-2.png" width="170"><br><sub>What it can do</sub></td>
<td align="center"><img src="screenshots/19-onboarding-mailbox-choice.png" width="170"><br><sub>Choose a mailbox</sub></td>
</tr>
</table>

The AI key is built into the APK. If your build has none, open **Settings, AI model** and paste a free Gemini key from <https://aistudio.google.com/>.

## 2. The three mailboxes

| Mailbox | What it is | Credentials needed | Best for |
|---|---|---|---|
| **Test Gmail account** (default) | Real Gmail on a dedicated throwaway account (`adityatesting404@gmail.com`) | Email and password are shown in Settings, Mailbox, with copy buttons | Seeing the real Gmail integration |
| **My own Gmail** | Your own account through Google's sign-in page | Your own Google account (must be an OAuth test user, see section 6) | Using your own mail |
| **Demo mailbox** | Ten sample emails stored on the phone. Fully offline | None | Trying everything with zero risk, including sending |

Switch at any time in **Settings, Mailbox**. The header badge shows which one is active (**TEST ACCOUNT** or **DEMO**).

<table>
<tr>
<td align="center"><img src="screenshots/09-settings-appearance-mailbox.png" width="200"><br><sub>Test account: email and password</sub></td>
<td align="center"><img src="screenshots/10-settings-test-account-steps.png" width="200"><br><sub>Six numbered sign-in steps</sub></td>
</tr>
</table>

### Signing in to the test account (step by step)
1. **Settings, Mailbox, Test Gmail account.** The email and password are shown; use the copy icons.
2. Tap **Sign in with Google**. Your browser opens.
3. Choose **Use another account**, paste the email, **Next**, paste the password, **Next**.
4. Google shows **"Google hasn't verified this app"**. This is normal for a test app: tap **Continue** (or Advanced, then Go to Aurviz).
5. Tick the Gmail permissions and tap **Continue**. You return to Aurviz, and the card shows **Connected as adityatesting404@gmail.com**.
6. Ask Aurviz anything. To see the mail itself, open Gmail with the same login.

If Google asks to "verify it's you" or blocks the sign-in (it can do this for a shared account), use the **Demo mailbox**.

### Why the test account is safe to share
It is a throwaway account with no private data. On the server it **can only send mail to itself or to example.com / example.org / example.net addresses** (which reach no real person), and it **cannot be disconnected** from the app, so one reviewer cannot break it for another.

## 3. See the demo mailbox

The in-app mailbox viewer exists **only for the demo mailbox**. Real accounts are viewed in Gmail itself. With the demo mailbox selected, tap the **inbox icon** in the chat header (or **Settings, View demo mailbox**). The viewer shows **Inbox, Sent and Drafts**, with unread messages in bold. Tap a message to read it, then **Summarize with Aurviz**. In demo mode the viewer **updates live** as Aurviz drafts or sends, and the menu has **Reset demo mailbox**.

<table>
<tr>
<td align="center"><img src="screenshots/07-mailbox-inbox.png" width="170"><br><sub>Inbox</sub></td>
<td align="center"><img src="screenshots/08-mailbox-message.png" width="170"><br><sub>Open a message</sub></td>
<td align="center"><img src="screenshots/06-mailbox-drafts.png" width="170"><br><sub>Drafts update live</sub></td>
<td align="center"><img src="screenshots/05-mailbox-sent.png" width="170"><br><sub>Sent updates live</sub></td>
</tr>
</table>

## 4. Ten-minute test script

Type each prompt, or tap the microphone and say it.

| # | Say or type | What you should see | Requirement |
|---|---|---|---|
| 1 | "Check my emails, find interview invitations and summarize them" | Cards "Search Gmail" then "Read email", then a structured summary (in demo: the Northwind Labs interview) | FR-03, 04, 12, 15 |
| 2 | "Which of my emails are important and unread?" | The important, unread messages | FR-15 |
| 3 | "Remember that I prefer formal emails" | A memory card; the Memory tab lists it | FR-05 |
| 4 | "Draft an email to meera.rao@university.example asking about my grade, but don't send it" | A formal draft is saved; nothing is sent. Open the **Drafts** tab to see it | FR-06, 16 |
| 5 | "Send an email to rahul@example.com saying I will be late" | An **approval dialog** with editable To, Subject and Body. Cancel sends nothing. Approve and it appears in **Sent** (the test account accepts only its own address and example.com addresses; the demo mailbox accepts any) | FR-08 |
| 6 | "Every morning at 9 AM, check my important emails and summarize them" | Task created. Open **Tasks**, tap **Run now**; **History** shows the run | FR-10, 11, 13 |
| 7 | "Forget my email tone preference" | Memory deleted | FR-07 |
| 8 | "Open news.ycombinator.com and tell me the top 3 stories" | The in-app browser opens and the stories are listed | FR-09 |
| 9 | In the browser open a Wikipedia article and tap **Summarize** | Back in chat with a summary of that page | FR-22 |

<table>
<tr>
<td align="center"><img src="screenshots/01-agent-empty.png" width="170"><br><sub>Start screen</sub></td>
<td align="center"><img src="screenshots/02-chat-email-summary.png" width="170"><br><sub>Search, read, summarize</sub></td>
<td align="center"><img src="screenshots/03-send-approval.png" width="170"><br><sub>Approval before sending</sub></td>
<td align="center"><img src="screenshots/12-chat-task-created.png" width="170"><br><sub>Scheduling by voice or text</sub></td>
</tr>
</table>

## 5. More to explore

- **Tasks:** pause and resume a daily task. **History** shows each run with its duration, and failures are explained in plain words with a next step.
- **Browser (globe icon):** address bar that also searches, find in page, reader view, desktop site, text size, bookmarks, history, share. The AI buttons Summarize, Key facts, Explain simply and Ask about this page use the page you are viewing.
- **Settings, Appearance:** System, Light or Dark.
- **Settings, Performance:** latency measured on your device (median and p95).
- **Long-press the app icon:** shortcuts for Voice command, Summarize inbox and Tasks.
- **Share text** from any app to Aurviz to get a summary.
- **Failure handling:** switch on airplane mode and send a prompt. You get a clear message and a **Try again** button.

<table>
<tr>
<td align="center"><img src="screenshots/13-tasks-list.png" width="170"><br><sub>Tasks</sub></td>
<td align="center"><img src="screenshots/14-task-history.png" width="170"><br><sub>Readable history</sub></td>
<td align="center"><img src="screenshots/11-browser-ai-actions.png" width="170"><br><sub>Browser with AI actions</sub></td>
<td align="center"><img src="screenshots/15-chat-dark.png" width="170"><br><sub>Dark theme</sub></td>
</tr>
</table>

## 6. Using your own Gmail (optional)

Google allows only listed accounts to grant the Gmail read permission while an app is in testing mode. The project owner can either:

1. **Add your account as a test user**: Google Cloud Console, APIs and Services, OAuth consent screen, Audience, Test users. Then choose **My own Gmail** in Settings and sign in on Google's own page. You never share a password with Aurviz or anyone.
2. **Publish the consent screen "In production"** (unverified). Any Google user can then continue through the "Google hasn't verified this app" warning, limited to 100 users until Google verifies the app.

## 7. Deeper reading

- [Email Flow](EMAIL_FLOW.md): the real-Gmail path step by step, with the Aurviz screens next to the matching Gmail screens as proof.
- [Browser](BROWSER.md): every browser feature, with screenshots.

## 8. Mapping to common evaluation criteria

| Criterion | Where to look |
|---|---|
| AI reasoning and tool calling | Scripts 1, 4, 5; the tool cards in chat; [Architecture, the agent loop](ARCHITECTURE.md#4-the-agent-loop) |
| Modular architecture | [Architecture](ARCHITECTURE.md); the `domain`, `data`, `presentation`, `service` packages |
| Persistent memory | Scripts 3, 4, 7; restart the app and open the Memory tab |
| Background scheduling | Script 6; [Architecture, scheduled tasks](ARCHITECTURE.md#6-scheduled-tasks) |
| OAuth and secure email | Section 2 and 6; [Security](ARCHITECTURE.md#10-security) |
| Error handling and reliability | Airplane-mode test; Tasks, History; [Test Report](TEST_REPORT.md) |
| Performance measurement | Settings, Performance; [Performance](PERFORMANCE.md) |
| UI and UX | The whole app: dark mode, onboarding, animations, mailbox viewer |
