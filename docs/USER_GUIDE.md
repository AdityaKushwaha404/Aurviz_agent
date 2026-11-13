# User Guide

## Talking to Aurviz
Type in the message box or tap the microphone. While Aurviz works, the header shows what it is doing ("Searching your inbox…"). Every tool it uses appears as a card, so you can see exactly what happened.

Examples:
- "Summarize my unread emails from today"
- "Draft a reply to the interview invitation, but don't send it"
- "Remember that my manager is Alex and her email is alex@company.com"
- "Every weekday morning at 8, summarize my inbox" (daily recurrence; weekday-only rules are not supported)
- "Open the BBC News website and tell me the top story"

## Mailboxes
Choose where Aurviz reads and writes mail in **Settings, Mailbox**:
- **Test Gmail account** (default): a real Gmail account made for testing. The email, password and sign-in steps are shown in Settings. It can only send to itself or example.com addresses.
- **My own Gmail**: sign in with Google.
- **Demo mailbox**: sample emails on your phone. Works offline.

For real accounts, open Gmail itself to see the mail. For the demo mailbox, open the **inbox icon** in the chat header (or Settings, View demo mailbox) to see **Inbox, Sent and Drafts**. Tap a message to read it or have Aurviz summarize it. The lists update as soon as Aurviz drafts or sends, and the menu has **Reset demo mailbox**.

## Email
- **Search and read:** Aurviz uses Gmail search syntax internally (`from:`, `subject:`, `is:unread`, `newer_than:3d`).
- **Drafts:** "draft…" or "don't send" always saves a draft.
- **Sending:** Aurviz shows **To, Subject and Body** in an editable dialog. Nothing is sent until you tap **Send email**. Scheduled tasks can never send.
- **Your own account:** Settings, Mailbox, My own Gmail, Connect Gmail. Sign-in happens on Google's page.

## Memory
Aurviz remembers facts and preferences and uses them in later answers. The **Memory** tab lets you search, add, edit and delete. Deleting asks for confirmation. You can also say "forget my…".

## Tasks
The **Tasks** tab lists routines. For each task you can **Run now**, **pause or resume** (daily tasks), **edit** or **delete**. The **History** tab keeps every run with the time taken. Tap a task's "View last result" to read what happened. If something failed you get a plain explanation and what to do next, with technical details one tap away.

Android may start a scheduled task a few minutes late to save battery.

## Browser
Tap the globe in the chat header.
- Type a website or just words to search.
- Menu (three dots): Find in page, Reader view, Desktop site, Larger or smaller text, Share link, Copy link, Open in Chrome.
- Bottom bar: back, forward, home, bookmark star, bookmarks and history.
- **AI chips:** Summarize, Key facts, Explain simply, Ask about this page. The page content is sent to Aurviz only when you tap one of these.

## Settings
- **Appearance:** System, Light or Dark.
- **Mailbox:** shared test inbox, demo mailbox or your own Gmail, plus View mailbox.
- **AI model:** your own Gemini key (optional if the build includes one).
- **Preferences:** default recipient and spoken replies.
- **Permissions:** shortcut to Android's permission page.
- **Performance:** measured response times.

## Shortcuts
Long-press the Aurviz icon for Voice command, Summarize inbox and Tasks. Share text from another app and choose Aurviz to get a summary.

## Troubleshooting
| Problem | What to do |
|---|---|
| "Could not reach Aurviz" | The hosted server may be waking up. Wait a few seconds and tap Retry |
| "Gmail inbox access was not granted" | Settings, Mailbox, My own Gmail, Disconnect, then Connect Gmail again and approve all permissions |
| "Could not reach Aurviz" when signing in | The server may be waking up. Wait a few seconds and Retry |
| "The test account can only send to itself…" | Expected. Use the demo mailbox or your own Gmail to email other people |
| Google asks to verify it's you | Use the Demo mailbox |
| "Gemini API key is not configured" | Add a key in Settings, AI model |
| Task ran late | Normal Android battery behaviour; use Run now for immediate execution |
