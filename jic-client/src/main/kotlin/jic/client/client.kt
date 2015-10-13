package jic.client

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class JicClient(private val baseUrl: String = "http://localhost:4567") {

    private val httpClient = HttpClients.createDefault()

    public fun download(resultId: String?, jar: File) {
        jar.deleteRecursively()
        jar.parentFile.mkdirs()
        URL(base("download/$resultId")).openConnection().inputStream.use { input ->
            ObservableOutputStream(FileOutputStream(jar), OsObserver()).use {
                input.copyTo(it)
            }
        }
    }

    public fun waitForResult(taskId: String, platform: String): String? {
        val statues = sequence {
            Thread.sleep(1000)
            val status = HttpGet(base("result/$taskId/$platform"))
            val res = httpClient.execute(status).entity.content.bufferedReader().readText()
            print("                             ${System.currentTimeMillis()}: $res\r")
            res
        }
        val resultId = statues.find { it != "null" }
        return resultId
    }

    public fun compile(fileUid: String): String {
        val compile = HttpPost(base("compile/$fileUid"))
        val r = httpClient.execute(compile)
        val re = r.entity
        val taskId = re.content.bufferedReader().readText()
        println(taskId)
        return taskId
    }

    public fun upload(file: File): String {
        val uploadFile = HttpPost(base("upload"))

        val builder = MultipartEntityBuilder.create()
        builder.addBinaryBody("file", file, ContentType.APPLICATION_OCTET_STREAM, "java")
        val multipart = builder.build()

        uploadFile.entity = multipart

        val response = httpClient.execute(uploadFile)
        val responseEntity = response.entity
        val fileUid = responseEntity.content.bufferedReader().readText()
        return fileUid
    }

    public fun close() {
        httpClient.close()
    }

    private fun base(tail: String) = baseUrl + "/" + tail
}
