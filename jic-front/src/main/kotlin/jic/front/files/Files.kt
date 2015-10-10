package jic.front.files

import java.io.*
import java.util.*

object Files {

    private val filesDir = File("/tmp/jic/files")
    private val fileNames = hashMapOf<UUID, String>()

    init {
        filesDir.mkdirs()
    }

    fun store(input: InputStream, fileName: String): UUID? {
        val uid = UUID.randomUUID()
        val file = File(filesDir, uid.toString())
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.use {
            input.copyTo(it)
        }
        fileNames.put(uid, fileName)
        return uid
    }

    fun open(uid: String): InputStream = FileInputStream(File(filesDir, uid))

    fun getName(fileId: UUID): String? = fileNames[fileId]

}
