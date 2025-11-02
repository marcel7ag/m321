package ch.mdedic.m321.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/*
 * WebSocket configuration class that registers WebSocket handlers.
 * @author Marcel Dedic
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val websocketMessageHandler: WebsocketMessageHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(websocketMessageHandler, "/ws").setAllowedOrigins("*")
    }
}