package jic.gradle

import jic.client.JicClient
import org.gradle.api.Plugin
import org.gradle.api.Project

class ExcelsiorJetPlugin implements Plugin<Project> {

    private final jetCompile = 'jetCompile'
    private File jar = null

    @Override
    void apply(Project project) {
        jar = project.tasks['jar'].archivePath
        project.task(jetCompile, dependsOn: 'jar') {
            inputs.file jar
            outputs.dir 'build/native'

        }
        project.tasks[jetCompile] << {
            def client = new JicClient("http://jic-front:4567")
            try {
                println(jar.absolutePath)
                println(jar.size())
                println(jar.exists())
                println("Uploading jar")
                def fileId = client.upload(jar)
                println("Compiling jar")
                def taskId = client.compile(fileId)
                println("Waiting for result")
                def resultId = client.waitForResult(taskId)
                println("Download exe")
                client.download(resultId, new File('build/jet', jar.name))
                println("exe downloaded")
            } finally {
                client.close()
            }
        }
    }
}
