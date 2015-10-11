package jic.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Agent(private val jetHome: File,
            private val jar: File,
            private val out: File) {

    private val jetBin = File(jetHome, "bin")
    private val jcPath = File(jetBin, "jc").absolutePath
    private val xpackPath = File(jetBin, "xpack").absolutePath

    private val workDir = jar.parentFile.absolutePath
    private val native = File(workDir, "native")
    private val target = File(workDir, "target")

    init {
        native.mkdirs()
        target.mkdirs()
    }

    fun compile() {
        "$jcPath ${jar.absolutePath}".e(native)
        "$xpackPath -source $native -target ${target.absolutePath}".e(target)
    }

    fun pack() {
        val out = ZipOutputStream(FileOutputStream(out))
        target.walkTopDown().forEach {
            val entryName = it.absolutePath.
                    substring(target.absolutePath.length()).
                    replace("\\", "/") +
                    if (it.isDirectory) "/" else ""

            println("Adding entry $entryName")
            if (entryName.isEmpty()) {
                return@forEach
            }
            val zipEntry = ZipEntry(entryName)
            out.putNextEntry(zipEntry)
            if (it.isFile) {
                it.inputStream().copyTo(out)
            }
            out.closeEntry()
        }
        out.close()
    }

    private fun String.e(workDir: File): Int {
        val proc = Runtime.getRuntime().exec(this, Os.env(jetHome), workDir)
        proc.inputStream.reader().forEachLine { println(it.toString()) }
        proc.errorStream.reader().forEachLine { System.err.println(it.toString()) }
        proc.waitFor()
        return proc.exitValue()
    }
}

object Main {

    private val mapper = ObjectMapper()

    private val jic = Os.jicHome()

    init {
        mapper.registerModule(ParanamerModule())
    }

    private fun uploadFile(result: File, url: String): String {

        val httpClient = HttpClients.createDefault()
        val uploadFile = HttpPost(url)

        val builder = MultipartEntityBuilder.create()
        println("Uploading file size: ${result.length()}")
        builder.addBinaryBody("file", result, ContentType.APPLICATION_OCTET_STREAM, result.name)
        val multipart = builder.build()

        uploadFile.entity = multipart

        val response = httpClient.execute(uploadFile)
        val responseEntity = response.entity
        return responseEntity.content.bufferedReader().readText()
    }

    @JvmStatic fun main(args: Array<String>) {

        val jetHome = args[2]

        val cf = ConnectionFactory()
        cf.host = args[0]
        cf.username = "user"
        cf.password = args[1]
        val conn = cf.newConnection()
        println("Connection created")

        val channel = conn.createChannel()
        println("channel created")

        val cons = object : DefaultConsumer(channel) {
            override fun handleDelivery(consumerTag: String?, envelope: Envelope?, properties: AMQP.BasicProperties?, body: ByteArray) {
                jic.deleteRecursively()
                jic.mkdirs()
                val msg = String(body, "UTF-8")
                println(msg)
                val task = mapper.readValue(msg, CompilationTask::class.java)
                val jar = File(jic, task.name + ".jar")
                println("Downloading jar")
                val openConnection = URL(task.downloadUrl).openConnection()
                println("Connection opened")
                openConnection.doOutput = false
                openConnection.doInput = true

                val inputStream = openConnection.inputStream
                println("input stream created")
                inputStream.use { input ->
                    FileOutputStream(jar).use {
                        println("Copying ")
                        input.copyTo(it)
                    }
                }
                println("Downloaded")
                val out = File(jic, "out.zip")
                val agent = Agent(File(jetHome), jar, out)
                agent.compile()
                println("Packing result")
                agent.pack()
                println("Result packed")
                val id = uploadFile(out, task.uploadUrl)
                println("Result uploaded")
                val result = CompilationResult(taskId = task.taskId, resultId = id, platform = Os.platform())
                channel.basicPublish("", AgentApi.resultsQueueName, null, mapper.writeValueAsString(result).toByteArray())
                println("Result published")
            }
        }
        channel.basicConsume(Os.queueName(), true, cons)
        println("Consuming started")
    }
}
