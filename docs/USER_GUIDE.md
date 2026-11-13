# User Guide

## Talking to Aurviz
Type in the message box or tap the microphone. While Aurviz works, the header shows what it is doing ("Searching your inbox…"). Every tool it uses appears as a card, so you can see exactly what happened.

Examples:
- "Summarize my unread emails from today"
- "Draft a reply to the interview invitation, but don't send it"
- "Remember that my manager is Alex and her email is alex@company.com"
- "Every weekday morning at 8, summarize my inbox" (daily recurrence; weekday-only rules are not supported)
- "Open the BBC News website and tell me the top story"

## Email
- **Search and read:** Aurviz uses Gmail search syntax internally (`from:`, `subject:`, `is:unread`, `newer_than:3d`).
- **Drafts:** "draft…" or "don't send" always saves a draft.
- **Sending:** Aurviz shows **To, Subject and Body** in an editable dialog. Nothing is sent until you tap **Send email**. Scheduled tasks can never send.
- **Connect your account:** Settings, Gmail, Connect Gmail. Sign-in happens on Google's page.
- **Try without an account:** Settings, Gmail, Demo mailbox.

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
- **Gmail:** connect, disconnect or use the demo mailbox.
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
| "Gmail inbox access was not granted" | Settings, Disconnect, then Connect Gmail again and approve all permissions |
| "Gemini API key is not configured" | Add a key in Settings, AI model |
| Task ran late | Normal Android battery behaviour; use Run now for immediate execution |
