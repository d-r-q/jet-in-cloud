package jic.front.tasks

import java.util.*

object Tasks {

    private val tasks = hashMapOf<UUID, Task>()

    fun createTask(): UUID {
        val id = UUID.randomUUID()
        tasks.put(id, Task(id, null))
        return id
    }

    fun setResult(taskId: UUID?, resultId: UUID) {
        tasks[taskId]!!.resultId = resultId
    }

    operator fun get(taskUid: UUID): Task? = tasks[taskUid]

}