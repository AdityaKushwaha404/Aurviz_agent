package com.aurviz.agent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurviz.agent.data.local.history.TaskExecutionDao
import com.aurviz.agent.data.local.history.TaskExecutionEntity
import com.aurviz.agent.data.local.memory.MemoryDao
import com.aurviz.agent.data.local.memory.MemoryEntity
import com.aurviz.agent.data.local.metrics.PerformanceDao
import com.aurviz.agent.data.local.metrics.PerformanceSampleEntity
import com.aurviz.agent.data.local.task.ScheduledTaskDao
import com.aurviz.agent.data.local.task.ScheduledTaskEntity

@Database(
    entities = [
        MemoryEntity::class,
        ScheduledTaskEntity::class,
        TaskExecutionEntity::class,
        PerformanceSampleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun taskExecutionDao(): TaskExecutionDao
    abstract fun performanceDao(): PerformanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 adds task execution history and performance samples. Existing memories
         * and scheduled tasks are preserved; persistent memory must survive upgrades.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }

        val MIGRATION_1_2_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `task_executions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` TEXT NOT NULL, `taskTitle` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`finishedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `message` TEXT NOT NULL, `attempt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_task_executions_taskId_startedAt` ON `task_executions` (`taskId`, `startedAt`)",
            "CREATE TABLE IF NOT EXISTS `performance_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`metric` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `success` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_performance_samples_metric_createdAt` ON `performance_samples` (`metric`, `createdAt`)"
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aurviz_agent.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Only a downgrade (installing an older build) may reset local data.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
