package jic.front.tasks

import jic.agent.Platform
import java.util.*

object Tasks {

    private val tasks = hashMapOf<UUID, Task>()

    fun createTask(): UUID {
        val id = UUID.randomUUID()
        tasks.put(id, Task(id, null, null))
        return id
    }

    fun setResult(taskId: UUID, platform: Platform, resultId: UUID) {
        tasks[taskId]!!.result(platform, resultId)
    }

    operator fun get(taskUid: UUID): Task? = tasks[taskUid]

}