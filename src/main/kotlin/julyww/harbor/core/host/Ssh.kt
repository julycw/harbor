package julyww.harbor.core.host

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


@Service
class SshSessionManager {

    private val log: Logger = LoggerFactory.getLogger(SshSessionManager::class.java)
    private val sessions: ConcurrentHashMap<String, SshSession> = ConcurrentHashMap()

    fun createSession(username: String, password: String, host: String, port: Int = 22): SshSession {
        SshSession.of(username, password, host, port).let {
            it.connect()
            sessions[it.sessionId] = it
            log.info("create ssh session $username@${host}:${port}, session id is ${it.sessionId}")
            return it
        }
    }

    fun getSession(sessionId: String): SshSession? = sessions[sessionId]

    /**
     * 关闭session
     */
    fun closeSession(sessionId: String) {
        log.info("close ssh session $sessionId")
        getSession(sessionId)?.let {
            try {
                it.close()
            } finally {
                sessions.remove(sessionId)
            }
        }
    }
}

class SshSession(
    username: String,
    password: String,
    host: String,
    port: Int = 22
) {
    val sessionId = UUID.randomUUID().toString()

    private val connTimeout: Duration = Duration.ofSeconds(30)
    private val connTimeoutMilli get() = connTimeout.toMillis().toInt()
    private val session: Session
    private var channelShell: ChannelShell? = null
    private var channelSftp: ChannelSftp? = null

    companion object {
        private val jsch = JSch()
        fun of(username: String, password: String, host: String, port: Int = 22) = SshSession(
            username, password, host, port
        )
    }

    init {
        session = jsch.getSession(username, host, port)
        session.setPassword(password)
        session.setConfig("StrictHostKeyChecking", "no")
    }

    fun connect(): SshSession {
        if (!session.isConnected) {
            session.connect(connTimeoutMilli)
        }
        return this
    }

    fun openSftpChannel(): ChannelSftp {
        val thisChannel = this.channelSftp
        val channel: ChannelSftp
        if (thisChannel == null || thisChannel.isClosed) {
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            this.channelSftp = channel as ChannelSftp
        } else {
            channel = thisChannel
        }
        return channel
    }

    fun openShellChannel(consumer: (String) -> Unit): SshSession {
        this.channelShell?.disconnect()
        this.channelShell = session.openChannel("shell").let {
            val channel: ChannelShell = it as ChannelShell
            channel.connect()
            Executors.newSingleThreadExecutor().execute {
                val inputStream = channel.inputStream
                val br = (InputStreamReader(inputStream, Charset.defaultCharset()))
                val len = 1024
                val tmp = CharArray(len)

                while (true) {
                    while (true) {
                        val i = br.read(tmp, 0, len)
                        if (i >= 0) {
                            consumer(String(tmp, 0, i))
                        } else {
                            break
                        }
                    }
                    if (channel.isClosed) {
                        break
                    }
                    Thread.sleep(100)
                }
            }
            channel
        }
        return this
    }

    fun close() {
        channelShell?.let {
            if (it.isConnected) {
                try {
                    it.disconnect()
                } catch (ignore: Exception) {
                }
            }
        }
        channelSftp?.let {
            if (it.isConnected) {
                try {
                    it.disconnect()
                } catch (ignore: Exception) {
                }
            }
        }

        if (session.isConnected) {
            try {
                session.disconnect()
            } catch (ignore: Exception) {
            }
        }
    }

    fun exec(command: String) {
        channelShell?.let {
            if (it.isConnected) {
                val ow = OutputStreamWriter(it.outputStream)
                ow.write(command)
                ow.flush()
            }
        }
    }

    fun sendSignal(signal: String) {
        channelShell?.let {
            if (it.isConnected) {
                val ow = OutputStreamWriter(it.outputStream)
                ow.write(signal.toInt())
                ow.flush()
            }
        }
    }


}
