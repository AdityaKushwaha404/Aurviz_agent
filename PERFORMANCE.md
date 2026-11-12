# Performance Analysis

## Measurement status

This repository does not currently contain measurements collected on a named device. Earlier latency, memory, token-reduction, and battery figures were not backed by reproducible traces and have been removed rather than presented as measurements.

| Metric | Status | Collection method |
|---|---|---|
| Agent response latency | Not measured | Record prompt submission and first/final response timestamps; report median and p95 over at least 30 runs. |
| Tool execution latency | Not measured | Time each tool separately, including network time for web fetches. |
| Memory use | Not measured | Capture Android Studio Profiler Java/native process memory at idle and during a representative task. |
| Scheduled execution delay | Not measured | Compare requested due time with WorkManager start time across idle and Doze conditions. |

Record device model, Android version, app build, model ID, network type, sample count, and raw results before claiming a performance result. WorkManager periodic intervals are not exact alarms.

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

## Next profiling opportunity

Use an Android Studio system trace and memory profiler on a representative low-end device. Separate network wait from UI rendering and tool execution, then optimize the measured bottleneck. No performance target is claimed until those measurements exist.
