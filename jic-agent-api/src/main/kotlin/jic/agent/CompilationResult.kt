package jic.agent

import com.fasterxml.jackson.annotation.JsonCreator

class CompilationResult @JsonCreator constructor(
        public val taskId: String,
        public val resultId: String)