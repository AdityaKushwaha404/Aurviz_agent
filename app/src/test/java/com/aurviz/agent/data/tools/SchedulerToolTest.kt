package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.model.TaskExecutionRecord
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerToolTest {

    private class FakeRepo : TaskSchedulerRepository {
        val scheduled = mutableListOf<ScheduledTask>()
        override fun getAllTasks(): Flow<List<ScheduledTask>> = flowOf(scheduled)
        override suspend fun getTaskById(id: String) = scheduled.firstOrNull { it.id == id }
        override suspend fun scheduleTask(task: ScheduledTask) { scheduled += task }
        override suspend fun updateTask(task: ScheduledTask) {}
        override suspend fun cancelTask(id: String) {}
        override suspend fun triggerTaskNow(id: String) = "queued"
        override suspend fun updateTaskStatus(id: String, lastExecutionEpoch: Long, status: String, completed: Boolean) {}
        override suspend fun setTaskEnabled(id: String, enabled: Boolean) {}
        override fun getExecutionHistory(limit: Int): Flow<List<TaskExecutionRecord>> = flowOf(emptyList())
        override suspend fun recordExecution(
            taskId: String, taskTitle: String, startedAt: Long, finishedAt: Long,
            status: String, message: String, attempt: Int
        ) {}
    }

    @Test
    fun modelWrittenInstructionIsStoredUnchanged() = runBlocking {
        val repo = FakeRepo()
        val instruction = "Search Gmail for important unread emails and summarize them."

        val result = SchedulerTool(repo).execute(
            mapOf(
                "title" to "Morning Inbox Summary",
                "promptAction" to instruction,
                "timeExpression" to "9:00 AM",
                "isRecurring" to true
            )
        )

        assertTrue(result.isSuccess)
        val task = repo.scheduled.single()
        assertEquals(instruction, task.promptAction)
        assertEquals("Morning Inbox Summary", task.title)
        assertEquals(9, task.hourOfDay)
        assertTrue(task.isRecurring)
    }

    @Test
    fun invalidTimeIsRejectedWithoutScheduling() = runBlocking {
        val repo = FakeRepo()
        val result = SchedulerTool(repo).execute(
            mapOf("promptAction" to "x", "timeExpression" to "whenever", "isRecurring" to true)
        )
        assertFalse(result.isSuccess)
        assertTrue(repo.scheduled.isEmpty())
    }
}
