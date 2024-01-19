package julyww.harbor.core.container

import cn.trustway.nb.common.auth.exception.app.AppException
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback.Adapter
import com.github.dockerjava.api.command.ExecCreateCmdResponse
import com.github.dockerjava.api.model.Frame
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.Closeable
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import java.util.*


class DockerContainerExecSession(
    containerId: String,
    cmd: String = "sh",
    private val dockerClient: DockerClient,
) {

    val sessionId = UUID.randomUUID().toString()

    private val log = LoggerFactory.getLogger(DockerContainerExecSession::class.java)
    private val exec: ExecCreateCmdResponse = dockerClient
        .execCreateCmd(containerId)
        .withAttachStdin(true)
        .withAttachStderr(true)
        .withAttachStdout(true)
        .withCmd(cmd)
        .withTty(true)
        .exec()


    val pipedInputStream = PipedInputStream()
    val pipedOutputStream = PipedOutputStream()

    fun attach(consumer: (String) -> Unit) {

        pipedInputStream.connect(pipedOutputStream)

        dockerClient
            .execStartCmd(exec.id)
            .withDetach(false)
            .withTty(true)
            .withStdIn(pipedInputStream)
            .exec(object : Adapter<Frame>() {

                override fun onNext(frame: Frame) {
                    consumer(String(frame.payload))
                }

                override fun onError(throwable: Throwable) {
                    log.info("error!", throwable)
                }

                override fun onComplete() {
                    log.info("complete!")
                }
            })
    }

    fun close() {
        try {
            pipedOutputStream.write(3) // ^C
            exec("\nexit\n")
        } finally {
            try {
                pipedOutputStream.close()
            } catch (_: Exception) {
            }
            try {
                pipedInputStream.close()
            } catch (_: Exception) {
            }
        }
    }

    fun exec(command: String) {
        pipedOutputStream.let {
            val ow = OutputStreamWriter(it)
            ow.write(command)
            ow.flush()
        }
    }

}


@Service
class DockerContainerSessionManager(
    private val dockerClient: DockerClient
) {

    private val log: Logger = LoggerFactory.getLogger(DockerContainerSessionManager::class.java)

    private val sessions: Cache<String, DockerContainerExecSession> = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .removalListener<String, DockerContainerExecSession> {
            it.value?.close()
        }
        .build()

    fun createSession(containerId: String, cmd: String): DockerContainerExecSession {
        val session = DockerContainerExecSession(containerId, cmd, dockerClient)
        sessions.put(session.sessionId, session)
        return session
    }

    fun getSession(sessionId: String): DockerContainerExecSession {
        return sessions.getIfPresent(sessionId) ?: throw AppException(404, "session 不存在")
    }

    fun close(sessionId: String) {
        sessions.invalidate(sessionId)
    }
}