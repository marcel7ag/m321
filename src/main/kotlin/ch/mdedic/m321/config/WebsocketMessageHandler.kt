package ch.mdedic.m321.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession

@Component
class WebsocketMessageHandler : org.springframework.web.socket.WebSocketHandler {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("WebSocket connection established: ${session.id}")
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        log.info("Received message: ${message.payload}")
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.error("WebSocket transport error", exception)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        log.error("WebSocket connection closed: ${closeStatus.reason}", session.id)
    }

    override fun supportsPartialMessages(): Boolean {
        return false
    }

}