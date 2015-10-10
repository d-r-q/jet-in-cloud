package jic.front

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import com.rabbitmq.client.*
import jic.agent.AgentApi
import jic.agent.CompilationResult
import jic.agent.CompilationTask
import jic.front.files.Files
import jic.front.tasks.Tasks
import spark.Spark.get
import spark.Spark.post
import sun.management.Agent
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*
import javax.servlet.MultipartConfigElement
import kotlin.properties.Delegates

private val mapper = ObjectMapper()

object Dispatcher {


    var c: Channel by Delegates.notNull<Channel>()

    fun init() {
        val cf = ConnectionFactory()
        cf.host = "localhost"
        cf.username = "user"
        cf.password = "password"
        val conn = cf.newConnection()
        c = conn.createChannel()
        val ch = c.queueDeclare(AgentApi.linuxTaskQueue, false, false, false, null)
        val resultsQueue = c.queueDeclare(AgentApi.resultsQueueName, false, false, false, null)

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                c.close()
                conn.close()
            }
        })

        val cons = object : DefaultConsumer(c) {
            override fun handleDelivery(consumerTag: String?, envelope: Envelope?, properties: AMQP.BasicProperties?, body: ByteArray) {
                val msg = String(body, "UTF-8")
                println(msg)
                val result = mapper.readValue(msg, CompilationResult::class.java)
                Tasks.setResult(UUID.fromString(result.taskId), UUID.fromString(result.resultId))
            }
        }
        c.basicConsume(AgentApi.resultsQueueName, true, cons)
    }

    fun post(msg: String, queue: String) {
        println("Publish $msg to $queue")
        c.basicPublish("", queue, null, msg.toByteArray())
    }

}

object Front {


    @JvmStatic fun main(args: Array<String>) {

        mapper.registerModule(ParanamerModule())
        Dispatcher.init()

        post("/compile/:fileUid") { req, res ->
            val uid = req.params("fileUid")
            println("Compile $uid")
            val task = Tasks.createTask()
            val name = Files.getName(UUID.fromString(uid))!!
            val cmpTask = CompilationTask(name = name, taskId = task.toString(), downloadUrl =  "http://localhost:4567/download/$uid")
            Dispatcher.post(mapper.writeValueAsString(cmpTask), AgentApi.linuxTaskQueue)
            task
        }

        post("/upload", "multipart/form-data") { req, res ->
            val multipartConfigElement = MultipartConfigElement("/tmp")
            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement)
            val file = req.raw().getPart("file") //file is name of the upload form
            val fileName = file.submittedFileName
            println("Uploading of $fileName")
            val uid = Files.store(file.inputStream, fileName)
            uid
        }

        get("/result/:taskUid") { req, res ->
            val taskUid = req.params("taskUid")
            val task = Tasks.get(UUID.fromString(taskUid))
            task?.resultId ?: "null"
        }

        get("/download/:uid") { req, res ->
            val uid = req.params("uid")
            res.raw().outputStream.use {
                Files.open(uid).copyTo(it)
            }
            res.raw()
        }

    }
}
