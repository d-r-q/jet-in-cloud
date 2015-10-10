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
import sun.tools.jar.resources.jar
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLConnection
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
            val entryName = it.absolutePath.substring(target.absolutePath.length()) +
                    if (it.isDirectory) "/" else ""
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
        val env = arrayOf(
                "PATH=${jetHome.absolutePath}/bin:\$PATH",
                "LD_LIBRARY_PATH=${jetHome.absolutePath}/lib/x86/shared:\$LD_LIBRARY_PATH"
        )
        val proc = Runtime.getRuntime().exec(this, env, workDir)
        proc.inputStream.reader().forEachLine { println(it.toString()) }
        proc.errorStream.reader().forEachLine { System.err.println(it.toString()) }
        proc.waitFor()
        return proc.exitValue()
    }
}

object Main {

    private val mapper = ObjectMapper()

    private val jic = File("/tmp/jic-agent")

    init {
        mapper.registerModule(ParanamerModule())
    }

    private fun uploadFile(result: File): String {

        val httpClient = HttpClients.createDefault()
        val uploadFile = HttpPost("http://localhost:4567/upload")

        val builder = MultipartEntityBuilder.create()
        builder.addBinaryBody("file", result, ContentType.APPLICATION_OCTET_STREAM, result.name)
        val multipart = builder.build()

        uploadFile.entity = multipart

        val response = httpClient.execute(uploadFile)
        val responseEntity = response.entity
        return responseEntity.content.bufferedReader().readText()
    }

    @JvmStatic fun main(args: Array<String>) {

        val cf = ConnectionFactory()
        cf.host = "localhost"
        cf.username = "user"
        cf.password = "password"
        val conn = cf.newConnection()

        val channel = conn.createChannel()

        val cons = object : DefaultConsumer(channel) {
            override fun handleDelivery(consumerTag: String?, envelope: Envelope?, properties: AMQP.BasicProperties?, body: ByteArray) {
                jic.deleteRecursively()
                jic.mkdirs()
                val msg = String(body, "UTF-8")
                println(msg)
                val task = mapper.readValue(msg, CompilationTask::class.java)
                val jar = File(jic, task.name + ".jar")
                URL(task.downloadUrl).openConnection().inputStream.use { input ->
                    FileOutputStream(jar).use {
                        input.copyTo(it)
                    }
                }
                val out = File(jic, "out.zip")
                val agent = Agent(File("/home/azhidkov/jet8040/"), jar, out)
                agent.compile()
                agent.pack()
                val id = uploadFile(out)
                val result = CompilationResult(taskId = task.taskId, resultId = id)
                channel.basicPublish("", AgentApi.resultsQueueName, null, mapper.writeValueAsString(result).toByteArray())
            }
        }
        channel.basicConsume(AgentApi.linuxTaskQueue, true, cons)
    }
}
