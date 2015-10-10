package jic.client

import java.io.File

object Main {

    @JvmStatic fun main(args: Array<String>) {

        val file = File("/home/azhidkov/tmp/hw/HelloWorld.jar")

        with(JicClient()) {
            val fileUid = upload(file)
            val taskId = compile(fileUid)
            val resultId = waitForResult(taskId)
            download(resultId, File("/tmp/jic-client/out.zip"))
            close()
        }
    }
}