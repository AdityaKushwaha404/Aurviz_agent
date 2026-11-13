# Performance Analysis

## Built-in measurement

The app now measures itself. `PerformanceRecorder` writes one Room row per sample (newest 1000 kept), and **Settings → Performance** shows count, median, p95 and success rate per metric:

| Metric | What is timed |
|---|---|
| Agent turn (end-to-end) | Prompt submitted → final answer or error shown. Includes Gemini and Gmail network time and any time spent waiting on a confirmation dialog. |
| Gemini call | One `generateContent` round trip, including model fallback. |
| Memory retrieval | Room lookup that builds the memory context for the prompt. |
| Tool: `<name>` | One tool execution, e.g. `search_emails`, `read_email`, `open_url`. |
| Scheduled task run | One background WorkManager run, start to persisted result. |

Percentiles use the nearest-rank method (`MetricMath`, unit-tested). The NFR target is under 3 s for simple commands, excluding external service delays. Compare "Agent turn" against "Gemini call": the difference is on-device overhead (memory retrieval, tool dispatch, persistence, UI).

### How to produce a report

1. Install a debug build on a named device, add the Gemini key, and connect Gmail.
2. Settings → Performance → Reset.
3. Run at least 30 simple prompts ("hi", "what do you remember about me?"), 10 email prompts ("summarize my important emails from today") and 2 or more task triggers.
4. Record device model, Android version, network type, model ID and the table from Settings below.

No result table is claimed here until that run is done on a device.

## Physical device capture — 2026-11-12

Device: realme RMX5116, Android 16 (API 36), `arm64-v8a`; debug package `com.aurviz.agent.debug`. The debug build was installed and launched on the connected phone.

| Metric | Capture | Notes |
|---|---:|---|
| Battery | 94% | Screenshot during final app inspection; device was not charging in the earlier battery service capture. |
| Uptime | 9 days, 15:19 | `adb shell uptime` capture; point-in-time value. |
| System CPU | ~7.1% busy | One `top` sample before the app run: 7% user + 40% system + 743% idle across 8 logical CPUs. This is a short snapshot, not a benchmark. |
| App CPU | 0% | One later `top` snapshot for the app process. |
| Process memory | 127,159 KB PSS; 217,704 KB RSS; 30,053 KB SwapPss | `dumpsys meminfo com.aurviz.agent.debug`; point-in-time process values, not a peak or a repeatable benchmark. |
| System memory | ~7.4 GB total; `top` free-memory samples varied from ~633 MB to ~122 MB, with ~2.42 GB cached in the later sample | Android reclaims cache; “free” is not equivalent to application-available memory. |
| Agent latency / task latency | Not measured | The email request failed in Gemini tool-call parsing before Gmail confirmation; no valid latency sample. |

These are single diagnostic captures and should not be presented as a performance study. Repeat under a controlled workload and report sample count/median/p95 before making latency or battery claims. WorkManager periodic intervals are not exact alarms.

## Existing optimization

The orchestrator limits conversational context to the last 12 user/assistant messages and requests up to six memory matches before falling back to recent memories. This is a bounded-context design choice, not a measured latency improvement. Chat history is file-backed, limited to 100 messages, and should be migrated to a versioned Room schema.

## Resource efficiency choices

- No foreground service or long-running background service. All deferred work uses WorkManager with a network constraint, so it never wakes the device just to fail offline.
- Context is bounded: 12 prior messages, at most 8 memories, email bodies capped at 6,000 characters, search capped at 15 results, at most 8 interactive and 6 background model steps.
- Room tables are bounded (performance 1000 rows, task history 500 rows, chat 100 messages).

## Next profiling opportunity

Use an Android Studio system trace and memory profiler on a representative low-end device. Separate network wait from UI rendering and tool execution, then optimize the measured bottleneck. No performance target is claimed until those measurements exist.
