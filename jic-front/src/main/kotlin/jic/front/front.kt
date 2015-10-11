package jic.front

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import com.rabbitmq.client.*
import jic.agent.AgentApi
import jic.agent.CompilationResult
import jic.agent.CompilationTask
import jic.agent.Platform
import jic.front.files.Files
import jic.front.tasks.Tasks
import spark.Spark.get
import spark.Spark.post
import java.util.*
import javax.servlet.MultipartConfigElement
import kotlin.properties.Delegates

private val mapper = ObjectMapper()

object Dispatcher {


    var c: Channel by Delegates.notNull<Channel>()

    fun init(args: Array<String>) {
        val cf = ConnectionFactory()
        cf.host = args[0]
        cf.username = "user"
        cf.password = args[1]
        val conn = cf.newConnection()
        c = conn.createChannel()
        val ch = c.queueDeclare(AgentApi.linuxTaskQueue, false, false, false, null)
        val winTasksQueue = c.queueDeclare(AgentApi.winTaskQueue, false, false, false, null)
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
                Tasks.setResult(UUID.fromString(result.taskId), result.platform, UUID.fromString(result.resultId))
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
        Dispatcher.init(args)

        post("/compile/:fileUid") { req, res ->
            val uid = req.params("fileUid")
            println("Compile $uid")
            val task = Tasks.createTask()
            val name = Files.getName(UUID.fromString(uid))!!
            val cmpTask = CompilationTask(name = name, taskId = task.toString(),
                    downloadUrl =  "http://${req.host()}/download/$uid", uploadUrl = "http://${req.host()}/upload")
            Dispatcher.post(mapper.writeValueAsString(cmpTask), AgentApi.linuxTaskQueue)
            Dispatcher.post(mapper.writeValueAsString(cmpTask), AgentApi.winTaskQueue)
            task
        }

        post("/upload", "multipart/form-data") { req, res ->
            val multipartConfigElement = MultipartConfigElement("/tmp")
            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement)
            val file = req.raw().getPart("file") //file is name of the upload form
            val fileName = file.submittedFileName
            println("Uploading of $fileName")
            val uid = Files.store(file.inputStream, fileName)
            println("$fileName uploaded to $uid")
            uid
        }

        get("/result/:taskUid/:platform") { req, res ->
            val taskUid = req.params("taskUid")
            val platform = Platform.valueOf(req.params("platform"))
            val task = Tasks.get(UUID.fromString(taskUid))
            task?.result(platform) ?: "null"
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
