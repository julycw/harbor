package julyww.harbor.core.notification.channel

interface Message

interface MessageChannel {
    fun send(event: Message)
}