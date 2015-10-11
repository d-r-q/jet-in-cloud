package jic.agent

import com.fasterxml.jackson.annotation.JsonCreator

class CompilationTask @JsonCreator constructor(
        val name: String,
        val downloadUrl: String,
        val uploadUrl: String,
        val taskId: String
)
