package julyww.harbor.core.notification.channel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


abstract class BaseMessage(
    @field:JsonProperty("msgtype")
    val msgType: String = "markdown",
    var at: At? = null
)

class At(
    @field:JsonProperty("atMobiles")
    var atMobiles: List<String>? = listOf(),
    @field:JsonProperty("isAtAll")
    var isAtAll: Boolean = false
)

class Text(
    var content: String? = null
)

class Markdown(
    var title: String? = null,
    var text: String? = null
)

class TextMessage(
    var text: Text? = null,
    at: At? = null
): BaseMessage("text", at)

class MarkdownMessage(
    var markdown: Markdown? = null,
    at: At? = null
): BaseMessage("markdown", at)

interface DingDingMessage : Message {

    fun toBaseMessage(): BaseMessage

}

class DingDingGroupChannel(
    private val accessKey: String,
    private val secret: String,
) : MessageChannel {

    private val log = LoggerFactory.getLogger(DingDingGroupChannel::class.java)
    private val restTemplate = RestTemplate()

    override fun send(event: Message) {
        if (event is DingDingMessage) {
            val message = event.toBaseMessage()
            val response = restTemplate.postForEntity(url, message, JsonNode::class.java)
            log.debug("send message response: ${response.body.toString()}")
        }
    }

    private val url: String get() {
        val ts = System.currentTimeMillis()
        return "$sendUrl?access_token=$accessKey&timestamp=$ts&sign=${sign(secret, ts)}"
    }

    private val sendUrl = "https://oapi.dingtalk.com/robot/send"

    private fun sign(secret: String, timestamp: Long): String {
        val stringToSign = """
            $timestamp
            $secret
            """.trimIndent()
        val mac: Mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(charset("UTF-8")), "HmacSHA256"))
        val signData: ByteArray = mac.doFinal(stringToSign.toByteArray(charset("UTF-8")))
        return URLEncoder.encode(String(Base64.getEncoder().encode(signData)), "UTF-8")
    }
}