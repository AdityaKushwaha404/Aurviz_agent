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

## Reproduce
Settings, Performance, Reset samples, send at least 30 prompts, read the table.

## Resource-efficiency choices
- No foreground or persistent background service. Deferred work uses WorkManager with a network constraint, so it never wakes the device just to fail offline.
- Bounded context: 12 prior messages, at most 8 memories, email bodies capped at 6,000 characters, search capped at 15 results, at most 8 interactive and 6 background model steps.
- Bounded tables: performance 1000 rows, task history 500 rows, chat 100 messages, browser history 60 pages.
- The hosted backend is warmed at launch to hide cold starts.
