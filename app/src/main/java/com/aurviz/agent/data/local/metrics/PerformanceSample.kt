package com.aurviz.agent.data.local.metrics

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.aurviz.agent.domain.metrics.MetricMath
import com.aurviz.agent.domain.metrics.MetricSummary
import com.aurviz.agent.domain.metrics.PerformanceRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "performance_samples", indices = [Index(value = ["metric", "createdAt"])])
data class PerformanceSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long
)

@Dao
interface PerformanceDao {
    @Insert
    suspend fun insert(sample: PerformanceSampleEntity)

    @Query("SELECT * FROM performance_samples ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PerformanceSampleEntity>>

    /** Keeps the table bounded so measurement never grows storage without limit. */
    @Query("DELETE FROM performance_samples WHERE id NOT IN (SELECT id FROM performance_samples ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM performance_samples")
    suspend fun clear()
}

class RoomPerformanceRecorder(private val dao: PerformanceDao) : PerformanceRecorder {
    override suspend fun record(metric: String, durationMs: Long, success: Boolean) {
        runCatching {
            dao.insert(
                PerformanceSampleEntity(
                    metric = metric,
                    durationMs = durationMs.coerceAtLeast(0),
                    success = success,
                    createdAt = System.currentTimeMillis()
                )
            )
            dao.trim(MAX_SAMPLES)
        }
    }

    fun summaries(): Flow<List<MetricSummary>> = dao.recent(MAX_SAMPLES).map { samples ->
        samples.groupBy { it.metric }
            .map { (metric, rows) -> MetricMath.summarize(metric, rows.map { it.durationMs to it.success }) }
            .sortedWith(compareBy({ ORDER.indexOf(it.metric).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.metric }))
    }

    suspend fun clear() = dao.clear()

    private companion object {
        const val MAX_SAMPLES = 1000
        val ORDER = listOf(
            PerformanceRecorder.AGENT_TURN,
            PerformanceRecorder.LLM_CALL,
            PerformanceRecorder.MEMORY_RETRIEVAL,
            PerformanceRecorder.SCHEDULED_RUN
        )
    }
}
