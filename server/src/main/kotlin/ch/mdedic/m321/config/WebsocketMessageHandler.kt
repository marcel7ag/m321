package ch.mdedic.m321.config

import com.fasterxml.jackson.databind.ObjectMapper
import dtos.MessageType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Component
class WebsocketMessageHandler(
    // START
    private val botCommandHandler: ch.mdedic.m321.bot.BotCommandHandler
    // END
) : org.springframework.web.socket.WebSocketHandler {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper()

    // Store all active sessions
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // Store user information (sessionId -> userId)
    private val userSessions = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("WebSocket connection established: ${session.id}")
        sessions[session.id] = session

        // Send welcome message
        val welcomeMessage = mapOf(
            "type" to MessageType.SYSTEM.toString(),
            "message" to "Connected to WebSocket server",
            "sessionId" to session.id
        )
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(welcomeMessage)))
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        try {
            val payload = message.payload.toString()
            log.info("Received message from ${session.id}: $payload")

            // Parse the incoming message
            val messageData = objectMapper.readValue(payload, Map::class.java)
            val messageType = messageData["type"] as? String ?: "CHAT"

            when (messageType) {
                "JOIN" -> handleJoinMessage(session, messageData)
                "CHAT" -> handleChatMessage(session, messageData)
                "LEAVE" -> handleLeaveMessage(session, messageData)
                else -> {
                    log.warn("Unknown message type: $messageType")
                    sendErrorToSession(session, "Unknown message type: $messageType")
                }
            }
        } catch (e: Exception) {
            log.error("Error handling message", e)
            sendErrorToSession(session, "Error processing message: ${e.message}")
        }
    }

    private fun handleJoinMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = messageData["userId"] as? String ?: "anonymous"
        userSessions[session.id] = userId

        log.info("User $userId joined with session ${session.id}")

        // Broadcast join to all other users
        val joinNotification = mapOf(
            "type" to MessageType.JOIN.toString(),
            "userId" to userId,
            "message" to "$userId has joined the chat",
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(joinNotification, excludeSession = session.id)

        // Send confirmation of the joining user
        val confirmation = mapOf(
            "type" to MessageType.JOIN_ACK.toString(),
            "message" to "Successfully joined the chat",
            "activeUsers" to userSessions.values.toList()
        )
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(confirmation)))
    }

    private fun handleChatMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"
        val content = messageData["content"] as? String ?: ""
        val recipientId = messageData["recipientId"] as? String

        // START
        // Check if this is a bot command
        if (botCommandHandler.isBotCommand(content)) {
            val botResponse = botCommandHandler.processCommand(session, userId, content)
            sendToSession(session, mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to botResponse,
                "timestamp" to System.currentTimeMillis()
            ))
            return
        }
        // END

        val chatMessage = mapOf(
            "type" to MessageType.CHAT.toString(),
            "id" to java.util.UUID.randomUUID().toString(),
            "senderId" to userId,
            "recipientId" to (recipientId ?: "all"),
            "content" to content,
            "timestamp" to System.currentTimeMillis()
        )

        log.info("Chat message from $userId: $content")

        // If recipientId is specified, send only to that user
        if (recipientId != null) {
            sendToUser(recipientId, chatMessage)
        } else {
            // Broadcast to all users EXCEPT the sender
            broadcastMessage(chatMessage, excludeSession = session.id)
        }
    }

    private fun handleLeaveMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"

        val leaveNotification = mapOf(
            "type" to MessageType.LEAVE.toString(),
            "userId" to userId,
            "message" to "$userId has left the chat",
            "timestamp" to System.currentTimeMillis()
        )

        broadcastMessage(leaveNotification, excludeSession = session.id)
        userSessions.remove(session.id)
    }

    private fun broadcastMessage(message: Any, excludeSession: String? = null) {
        val jsonMessage = objectMapper.writeValueAsString(message)
        sessions.values.forEach { session ->
            if (session.id != excludeSession && session.isOpen) {
                try {
                    session.sendMessage(TextMessage(jsonMessage))
                } catch (e: Exception) {
                    log.error("Error sending message to session ${session.id}", e)
                }
            }
        }
    }

    private fun sendToUser(userId: String, message: Any) {
        val targetSessionId = userSessions.entries.find { it.value == userId }?.key
        val targetSession = targetSessionId?.let { sessions[it] }

        if (targetSession != null && targetSession.isOpen) {
            val jsonMessage = objectMapper.writeValueAsString(message)
            targetSession.sendMessage(TextMessage(jsonMessage))
        } else {
            log.warn("User $userId not found or session is closed")
        }
    }

    // START
    private fun sendToSession(session: WebSocketSession, message: Any) {
        if (session.isOpen) {
            val jsonMessage = objectMapper.writeValueAsString(message)
            try {
                session.sendMessage(TextMessage(jsonMessage))
            } catch (e: Exception) {
                log.error("Error sending message to session ${session.id}", e)
            }
        }
    }
    // END

    private fun sendErrorToSession(session: WebSocketSession, errorMessage: String) {
        val error = mapOf(
            "type" to MessageType.ERROR.toString(),
            "message" to errorMessage,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(error)))
        } catch (e: Exception) {
            log.error("Error sending error message", e)
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.error("WebSocket transport error for session ${session.id}", exception)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        log.info("WebSocket connection closed: ${session.id}, reason: ${closeStatus.reason}")

        val userId = userSessions[session.id]
        if (userId != null) {
            val leaveNotification = mapOf(
                "type" to MessageType.LEAVE.toString(),
                "userId" to userId,
                "message" to "$userId has disconnected",
                "timestamp" to System.currentTimeMillis()
            )
            broadcastMessage(leaveNotification, excludeSession = session.id)
        }

        sessions.remove(session.id)
        userSessions.remove(session.id)
    }

    override fun supportsPartialMessages(): Boolean {
        return false
    }
}