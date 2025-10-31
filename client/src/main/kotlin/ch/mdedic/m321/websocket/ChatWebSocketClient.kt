package ch.mdedic.m321.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class ChatWebSocketClient(
    serverUri: URI,
    private val listener: ConnectionListener
) : WebSocketClient(serverUri) {

    private val objectMapper = jacksonObjectMapper()

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onMessageReceived(message: String)
        fun onError(error: String)
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        listener.onConnected()
    }

    override fun onMessage(message: String?) {
        message?.let { listener.onMessageReceived(it) }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        listener.onDisconnected(reason)
    }

    override fun onError(ex: Exception?) {
        listener.onError(ex?.message ?: "Unknown error")
    }

    fun sendJoin(username: String) {
        val message = mapOf(
            "type" to "JOIN",
            "userId" to username
        )
        send(objectMapper.writeValueAsString(message))
    }

    fun sendLeave(username: String) {
        val message = mapOf(
            "type" to "LEAVE",
            "userId" to username
        )
        send(objectMapper.writeValueAsString(message))
    }

    fun sendChatMessage(username: String, content: String) {
        val message = mapOf(
            "type" to "CHAT",
            "userId" to username,
            "content" to content,
            "timestamp" to System.currentTimeMillis()
        )
        send(objectMapper.writeValueAsString(message))
    }
}