package jic.client

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import java.io.File

object Client {
    @JvmStatic fun main(args: Array<String>) {

        val httpClient = HttpClients.createDefault()
        val uploadFile = HttpPost("http://localhost:4567/upload")

        val builder = MultipartEntityBuilder.create()
        builder.addBinaryBody("file", File("/home/azhidkov/tmp/hw/HelloWorld.jar"), ContentType.APPLICATION_OCTET_STREAM, "java")
        val multipart = builder.build()

        uploadFile.entity = multipart

        val response = httpClient.execute(uploadFile)
        val responseEntity = response.entity
        responseEntity.content.bufferedReader().forEachLine { println(it) }
    }
}
