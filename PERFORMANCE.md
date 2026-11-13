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

### Measured results (2026-11-13)

Collected by the app itself (Settings → Performance) on a **realme RMX5116, Android 16 (API 36)**, Wi-Fi, debug build, model `gemini-3.5-flash-lite` (the configured primary), after resetting the samples. Workload: **30 simple one-line prompts** that need no tools (arithmetic, trivia, greetings), sent by script through the UI. Nearest-rank percentiles.

| Metric | n | Median | p95 | Success |
|---|---:|---:|---:|---:|
| Agent turn (end-to-end) | 30 | 1.6 s | 6.6 s | 100% |
| Gemini call | 30 | 1.5 s | 6.4 s | 100% |
| Memory retrieval (Room) | 30 | 54 ms | 93 ms | 100% |

How to read this against the NFR target ("under 3 s for simple commands, excluding external service delays"):

- **On-device overhead is about 0.1 s at the median** (agent turn minus Gemini call). That covers memory retrieval, prompt assembly, tool dispatch, persistence and UI state, and is far below 3 s.
- **End-to-end median is 1.6 s**, also under 3 s including the network round trip.
- **The p95 of 6.6 s is almost entirely Gemini latency** (its own p95 is 6.4 s). The slow tail is external service time; it is not on-device work.

Other single observations from the same session (not a benchmark): `search_emails` took about 0.9 s for one call, `manage_memory` 43 ms, and a manually triggered background run that searched Gmail and summarized took 9.9 s end-to-end (several model and tool steps).

Limitations: one device, one network, one time of day, simple prompts only. Email workflows with several model steps take proportionally longer, since each step adds a Gemini round trip. No battery or long-run memory study was performed.

To reproduce: Settings → Performance → Reset samples, send at least 30 prompts, and read the table.

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
