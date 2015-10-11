package jic.agent

import org.apache.commons.lang3.SystemUtils
import java.io.File

object Os {

    public fun env(jetHome: File) = when {
        SystemUtils.IS_OS_LINUX ->
            arrayOf(
                    "PATH=${jetHome.absolutePath}/bin:\$PATH",
                    "LD_LIBRARY_PATH=${jetHome.absolutePath}/lib/x86/shared:\$LD_LIBRARY_PATH")
        else -> arrayOf<String>()
    }

    public fun jicHome(): File = File(SystemUtils.getJavaIoTmpDir(), "jic-agent")

    public fun queueName() = when {
        SystemUtils.IS_OS_LINUX -> AgentApi.linuxTaskQueue
        SystemUtils.IS_OS_WINDOWS -> AgentApi.winTaskQueue
        else -> throw RuntimeException("Unsupported OS: ${SystemUtils.OS_NAME}")
    }

    public fun platform() = when {
        SystemUtils.IS_OS_LINUX -> Platform.LINUX
        SystemUtils.IS_OS_WINDOWS -> Platform.WIN
        SystemUtils.IS_OS_MAC -> Platform.MAC
        else -> throw RuntimeException("Unsupported OS: ${SystemUtils.OS_NAME}")
    }

}