package jic.front.tasks

import jic.agent.Platform
import java.util.*

class Task(public val id: UUID,
           public var linuxResultId: UUID?,
           public var winResultId: UUID?) {

    fun result(platform: Platform): UUID? = when (platform) {
        Platform.LINUX -> linuxResultId
        Platform.WIN -> winResultId
        Platform.MAC -> null
    }

    fun result(platform: Platform, id: UUID) {
        when (platform) {
            Platform.LINUX -> linuxResultId = id
            Platform.WIN -> winResultId = id
            else -> throw RuntimeException("Unsupported platform: $platform")
        }
    }

}