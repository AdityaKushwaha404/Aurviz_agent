package com.aurviz.agent.domain.metrics

/**
 * Records on-device latency samples so performance claims are backed by measurements.
 * Implementations must be cheap and must never throw into the agent loop.
 */
interface PerformanceRecorder {
    suspend fun record(metric: String, durationMs: Long, success: Boolean)

    companion object {
        /** Prompt submission until the final answer/error is shown (includes network). */
        const val AGENT_TURN = "agent_turn_total"
        /** One Gemini generateContent round trip, including model fallback. */
        const val LLM_CALL = "llm_call"
        /** Local Room memory lookup used to build the prompt context. */
        const val MEMORY_RETRIEVAL = "memory_retrieval"
        /** One autonomous scheduled-task execution. */
        const val SCHEDULED_RUN = "scheduled_task_run"
        fun tool(name: String) = "tool:$name"
    }
}

object NoOpPerformanceRecorder : PerformanceRecorder {
    override suspend fun record(metric: String, durationMs: Long, success: Boolean) = Unit
}

/** Summary statistics for one metric over the retained sample window. */
data class MetricSummary(
    val metric: String,
    val count: Int,
    val successRate: Double,
    val medianMs: Long,
    val p95Ms: Long,
    val maxMs: Long
)

object MetricMath {
    /** Nearest-rank percentile; [values] need not be sorted. */
    fun percentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val rank = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun summarize(metric: String, samples: List<Pair<Long, Boolean>>): MetricSummary {
        val durations = samples.map { it.first }
        return MetricSummary(
            metric = metric,
            count = samples.size,
            successRate = if (samples.isEmpty()) 0.0 else samples.count { it.second }.toDouble() / samples.size,
            medianMs = percentile(durations, 50.0),
            p95Ms = percentile(durations, 95.0),
            maxMs = durations.maxOrNull() ?: 0
        )
    }
}
