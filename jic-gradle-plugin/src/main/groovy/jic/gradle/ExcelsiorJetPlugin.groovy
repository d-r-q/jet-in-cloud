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
            def client = new JicClient()
            try {
                println(jar.absolutePath)
                println(jar.size())
                println(jar.exists())
                def fileId = client.upload(jar)
                def taskId = client.compile(fileId)
                def resultId = client.waitForResult(taskId)
                client.download(resultId, new File('build/jet', jar.name))
            } finally {
                client.close()
            }
        }
    }
}
