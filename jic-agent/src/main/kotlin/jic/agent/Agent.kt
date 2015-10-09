package jic.agent

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
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

    private val workDir = jar.parentFile.parentFile.absolutePath
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
    @JvmStatic fun main(args: Array<String>) {

        val cf = ConnectionFactory()
        cf.host = "localhost"
        cf.username = "user"
        cf.password = "password"
        val conn = cf.newConnection()
        val c = conn.createChannel()
        val name = "compileLinuxTask"
        val jic = File("/tmp/jic-agent")

        val cons = object : DefaultConsumer(c) {
            override fun handleDelivery(consumerTag: String?, envelope: Envelope?, properties: AMQP.BasicProperties?, body: ByteArray) {
                jic.deleteRecursively()
                jic.mkdirs()
                val uid = String(body, "UTF-8")
                val jar = File(jic, uid + ".jar")
                URL("http://localhost:4567/download/$uid").openConnection().inputStream.use { input ->
                    FileOutputStream(jar).use {
                       input.copyTo(it)
                    }
                }
                println(uid)
                val agent = Agent(File("/home/azhidkov/jet8040/"), jar, File(jic, "out.zip"))
                agent.compile()
                agent.pack()
            }
        }
        c.basicConsume(name, true, cons)
    }
}
