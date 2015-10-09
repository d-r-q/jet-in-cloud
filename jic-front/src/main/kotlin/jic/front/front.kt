package jic.front

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import jic.front.files.Files
import spark.Spark.get
import spark.Spark.post
import java.io.File
import javax.servlet.MultipartConfigElement
import kotlin.properties.Delegates

object Dispatcher {

    val linuxTaskQueue = "compileLinuxTask"
    var c: Channel by Delegates.notNull<Channel>()

    fun init() {
        val cf = ConnectionFactory()
        cf.host = "localhost"
        cf.username = "user"
        cf.password = "password"
        val conn = cf.newConnection()
        c = conn.createChannel()
        val ch = c.queueDeclare(linuxTaskQueue, false, false, false, null)

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                c.close()
                conn.close()
            }
        })
    }

    fun post(msg: String, queue: String) {
        println("Publish $msg to $queue")
        c.basicPublish("", queue, null, msg.toByteArray())
    }

}

object Front {
    @JvmStatic fun main(args: Array<String>) {

        Dispatcher.init()
        get("/hello") { req, res ->
            "Hello World"
        }

        post("/upload", "multipart/form-data") { req, res ->
            val multipartConfigElement = MultipartConfigElement("/tmp")
            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement)
            val file = req.raw().getPart("file") //file is name of the upload form
            val uid = Files.store(file.inputStream)

            Dispatcher.post(uid.toString(), Dispatcher.linuxTaskQueue)
            uid
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
