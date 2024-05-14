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

enum class SessionState {
    Init,
    Connecting,
    Started,
    Completed,
    Error
}

class DockerContainerExecSession(
    containerId: String,
    cmd: String = "sh",
    private val dockerClient: DockerClient,
) {

    val sessionId = UUID.randomUUID().toString()

    @Volatile
    private var state = SessionState.Init

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
        this.state = SessionState.Connecting

        dockerClient
            .execStartCmd(exec.id)
            .withDetach(false)
            .withTty(true)
            .withStdIn(pipedInputStream)
            .exec(object : Adapter<Frame>() {

                override fun onStart(stream: Closeable?) {
                    log.info("session $sessionId start!")
                    this@DockerContainerExecSession.state = SessionState.Started
                }

                override fun onNext(frame: Frame) {
                    consumer(String(frame.payload))
                }

                override fun onError(throwable: Throwable) {
                    log.info("session $sessionId error! {}", throwable.message)
                    this@DockerContainerExecSession.state = SessionState.Error
                }

                override fun onComplete() {
                    log.info("session $sessionId complete!")
                    this@DockerContainerExecSession.state = SessionState.Completed
                }
            })
    }

    fun close() {
        log.info("try close session $sessionId ...")
        pipedOutputStream.write(3) // ^C
        exec("\nexit\n")
    }

    fun exec(command: String) {
        var wait = 0
        while (state != SessionState.Started) {
            Thread.sleep(100)
            wait += 100
            if (wait > 10000) {
                throw AppException(500, "timeout")
            }
        }
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