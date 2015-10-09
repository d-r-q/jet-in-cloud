package jic.front.files

import java.io.*
import java.util.*

object Files {

    private val filesDir = File("/tmp/jic/files")

    init {
        filesDir.mkdirs()
    }

    fun store(input: InputStream): UUID? {
        val uid = UUID.randomUUID()
        val file = File(filesDir, uid.toString())
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.use {
            input.copyTo(it)
        }
        return uid
    }

    fun open(uid: String): InputStream = FileInputStream(File(filesDir, uid))

}
