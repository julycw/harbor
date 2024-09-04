package julyww.harbor.utils

import julyww.harbor.core.container.DockerService
import julyww.harbor.props.HarborProps
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader


@Component
class Environments(
    private val dockerService: DockerService,
    private val harborProps: HarborProps
) {

    val dockerContainerId: String? by lazy {
        val cgroupFilePath = "/proc/self/cgroup"
        var containerId: String? = null
        try {
            BufferedReader(InputStreamReader(FileInputStream(cgroupFilePath))).use { reader ->
                var line: String
                while ((reader.readLine().also { line = it }) != null) {
                    if (line.contains("docker")) {
                        // 0::/docker/{container-id}
                        val parts = line.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        containerId = parts[2]
                        break
                    }
                }
            }
        } catch (_: IOException) {
        }
        containerId
    }

    val dockerDaemonId: String? by lazy {
        try {
            dockerService.sys().id
        } catch (e: Exception) {
            null
        }
    }

    val endpoint: String? by lazy {
        harborProps.endpoint ?: dockerDaemonId
    }
}