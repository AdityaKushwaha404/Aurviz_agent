# Performance

## Method
Aurviz measures itself. `PerformanceRecorder` writes one row per sample into Room (newest 1000 kept), and **Settings, Performance** shows count, median, 95th percentile and success rate per metric.

| Metric | What is timed |
|---|---|
| Agent turn (end-to-end) | Prompt submitted until the answer or error is shown. Includes Gemini and Gmail network time, and any time spent waiting on an approval dialog |
| Gemini call | One `generateContent` round trip, including model fallback |
| Memory retrieval | The Room lookup that builds the memory context |
| Tool: `<name>` | One tool execution, for example `search_emails` |
| Scheduled task run | One background WorkManager run, start to persisted result |

Percentiles use the nearest-rank method (`MetricMath`, unit-tested).

## Results (13 November 2026)

**Device:** realme RMX5116, Android 16 (API 36), Wi-Fi, debug build, model `gemini-3.5-flash-lite`.
**Workload:** 30 simple one-line prompts needing no tools, sent through the UI after resetting the samples.

| Metric | n | Median | p95 | Success |
|---|---:|---:|---:|---:|
| Agent turn (end-to-end) | 30 | 1.6 s | 6.6 s | 100% |
| Gemini call | 30 | 1.5 s | 6.4 s | 100% |
| Memory retrieval (Room) | 30 | 54 ms | 93 ms | 100% |

### Reading the numbers against the target
The target is under 3 s for simple commands, excluding external service time.

- **On-device overhead is about 0.1 s at the median** (agent turn minus Gemini call). That covers memory retrieval, prompt assembly, tool dispatch, persistence and UI state.
- **End-to-end median is 1.6 s**, below 3 s even including the network round trip.
- **The 6.6 s p95 is almost entirely Gemini's own latency** (its p95 is 6.4 s): external service time, not on-device work.

Other single observations: `search_emails` about 0.9 s for one call, `manage_memory` 43 ms, and a manually triggered background run that searched Gmail and summarized took 9.9 s end-to-end (several model and tool steps, each adding a Gemini round trip).

### Limitations of this study
One device, one network, one time of day, simple prompts. Multi-step email workflows take proportionally longer. No battery or long-run memory study was performed. WorkManager timing is not exact, so scheduled-run delay was not benchmarked.

## Task execution latency (scheduled runs)

Measured by the app's recorder on the same device. A scheduled run is a full background agent run: several model calls and tool calls through WorkManager.

| Run | Duration | Notes |
|---|---:|---|
| Email summary, real Gmail via the backend (run 1) | 9.9 s | Search, read, summarize |
| Email summary, real Gmail via the backend (run 2) | 21.3 s | More messages read, more model steps |
| Email summary, demo mailbox | 6.9 s | Local mail tools, no network hop to Gmail |
| Weekday task fired by the scheduler at 17:14 (demo mailbox) | 11.5 s | Started on the minute; shows scheduling accuracy on a charging, awake phone |

Task latency is dominated by the number of model round trips (about 1.5 s each) plus Gmail calls; it is a background job, so the user never waits on it. WorkManager's scheduling delay (how late Android starts a run) depends on battery state and was not benchmarked.

## Memory usage

`dumpsys meminfo` on the physical device, debug build, after a session that included chat, the mailbox viewer and the browser:

| Measure | Value |
|---|---:|
| Total PSS | 165 MB |
| Java heap | 18 MB |
| Native heap | 16 MB |
| Graphics | 39 MB |
| Resident set (RSS) | 289 MB |
| Swap PSS | 0.1 MB |

Most of the footprint is graphics and the WebView that backs the browser (created only while the browser screen is open and destroyed afterwards). The Kotlin heap is small because chat history is capped at 100 messages and results are bounded. This is a single point-in-time measurement on a debug build, not a peak or a leak test.

## Optimization implemented

**Bounded model context.** Each turn sends at most 12 prior messages, 8 memories, email bodies capped at 6,000 characters and 15 search results. This keeps requests small, which lowers latency and token cost, and it keeps the app's memory flat over long sessions. It is a design choice; its effect on latency was not isolated in an A/B measurement.

**Backend warm-up.** The free-tier server sleeps when idle and needs about 30 to 50 seconds to wake. The app sends a health request at launch and allows a 45-second first request, so the first Gmail action no longer fails with "could not reach".

## Optimization opportunities (not yet done)

1. **Stream the model response.** Gemini supports streaming. Showing text as it arrives would make the 1.5 s median feel instant, and it would cut perceived latency for long summaries where the 95th percentile reaches 6.6 s.
2. **Cache and parallelize Gmail reads.** `search_emails` already fetches message metadata in parallel on the server. `read_email` calls are sequential through the agent loop; letting the model request several reads at once (parallel function calls) would cut a multi-email summary from about N model round trips to about two.
3. **Memory retrieval index.** Replace the per-term `LIKE` queries with an FTS4 table (or embeddings) once memories grow into the thousands.
4. **Shrink the APK and cold start.** Enable R8 minification and a baseline profile for the release build (minification is off in the current debug and release configurations).

## Reproduce
Settings, Performance, Reset samples, send at least 30 prompts, read the table.

## Resource-efficiency choices
- No foreground or persistent background service. Deferred work uses WorkManager with a network constraint, so it never wakes the device just to fail offline.
- Bounded context: 12 prior messages, at most 8 memories, email bodies capped at 6,000 characters, search capped at 15 results, at most 8 interactive and 6 background model steps.
- Bounded tables: performance 1000 rows, task history 500 rows, chat 100 messages, browser history 60 pages.
- The hosted backend is warmed at launch to hide cold starts.
