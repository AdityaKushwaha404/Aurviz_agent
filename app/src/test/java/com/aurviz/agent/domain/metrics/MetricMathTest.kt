package com.aurviz.agent.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricMathTest {
    @Test
    fun percentileUsesNearestRank() {
        val values = (1L..100L).shuffled()
        assertEquals(50L, MetricMath.percentile(values, 50.0))
        assertEquals(95L, MetricMath.percentile(values, 95.0))
        assertEquals(0L, MetricMath.percentile(emptyList(), 50.0))
    }

    @Test
    fun summaryReportsSuccessRateAndMax() {
        val summary = MetricMath.summarize("x", listOf(100L to true, 300L to false, 200L to true, 400L to true))
        assertEquals(4, summary.count)
        assertEquals(0.75, summary.successRate, 0.0001)
        assertEquals(200L, summary.medianMs)
        assertEquals(400L, summary.maxMs)
    }
}
