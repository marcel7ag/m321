package ch.mdedic.m321.config

import ch.mdedic.m321.bot.ServerData
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
    private val botCommandHandler: ch.mdedic.m321.bot.BotCommandHandler,
    private val adminManager: AdminManager,
    private val objectMapper: ObjectMapper
) : org.springframework.web.socket.WebSocketHandler {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val ADMIN_DM_PREFIX = "@serveradmin"
    }

    // Store all active sessions
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // Store user information (sessionId -> userId)
    private val userSessions = ConcurrentHashMap<String, String>()

    // Track server start time
    private val serverStartTime: Long = System.currentTimeMillis()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("WebSocket connection established: ${session.id}")
        sessions[session.id] = session
        adminManager.registerConnection(session.id)
// END
        // Send welcome message
        val welcomeMessage = MessageBuilder.welcome(session.id)
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
                MessageType.JOIN.toString() -> handleJoinMessage(session, messageData)
                MessageType.CHAT.toString() -> handleChatMessage(session, messageData)
                MessageType.LEAVE.toString() -> handleLeaveMessage(session, messageData)
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

        // Try to set as first admin
        adminManager.trySetFirstAdmin(session, userId)

        // Broadcast join to all other users
        val joinNotification = MessageBuilder.joinNotification(
            userId = userId,
            isAdmin = adminManager.isAdmin(session.id)
        )
        broadcastMessage(joinNotification, excludeSession = session.id)

        // Send confirmation to the joining user
        val confirmation = MessageBuilder.joinAck(
            activeUsers = userSessions.values,
            isAdmin = adminManager.isAdmin(session.id)
        )
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(confirmation)))
    }

    private fun handleChatMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"
        val content = messageData["content"] as? String ?: ""
        val recipientId = messageData["recipientId"] as? String
// START
        // Check if this is a DM to admin
        if (content.trim().lowercase().startsWith(ADMIN_DM_PREFIX)) {
            handleAdminDM(session, userId, content)
            return
        }
// END

        // Check if this is a bot command
        if (botCommandHandler.isBotCommand(content)) {
            val serverData = ServerData(
                adminUserId = adminManager.getAdminUserId(userSessions),
                adminSessionId = adminManager.getAdminSessionId(),
                serverStartTime = serverStartTime,
                connectedUsers = userSessions.values.toList()
            )
            val botResponse = botCommandHandler.processCommand(session, userId, content, serverData)
            sendToSession(session, MessageBuilder.system(botResponse))
            return
        }

        val chatMessage = MessageBuilder.chat(
            senderId = userId,
            content = content,
            recipientId = recipientId ?: "all",
            isAdmin = adminManager.isAdmin(session.id)
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

// START
    private fun handleAdminDM(senderSession: WebSocketSession, senderId: String, content: String) {
        // Remove @ServerAdmin prefix
        val actualMessage = content.trim().replaceFirst(Regex("@serveradmin\\s*", RegexOption.IGNORE_CASE), "").trim()

        if (actualMessage.isEmpty()) {
            sendToSession(senderSession, MessageBuilder.system("Error: Message to admin cannot be empty"))
            return
        }

        val adminSessionId = adminManager.getAdminSessionId()
        if (adminSessionId == null) {
            sendToSession(senderSession, MessageBuilder.system("Error: No admin is currently online"))
            return
        }

        // Check if sender is the admin (admin DMing themselves)
        if (adminManager.isAdmin(senderSession.id)) {
            sendToSession(senderSession, MessageBuilder.system("Error: You cannot DM yourself (you are the admin)"))
            return
        }

        val adminUserId = userSessions[adminSessionId] ?: "Admin"

        log.info("Admin DM from $senderId to $adminUserId: $actualMessage")

        // Create DM message for admin
        val dmMessage = MessageBuilder.privateMessage(
            senderId = senderId,
            recipientId = adminUserId,
            content = actualMessage,
            isAdminDM = true
        )

        // Send to admin
        val adminSession = sessions[adminSessionId]
        if (adminSession != null && adminSession.isOpen) {
            sendToSession(adminSession, dmMessage)
        }

        // Send confirmation to sender
        val senderConfirmation = MessageBuilder.privateMessage(
            senderId = senderId,
            recipientId = adminUserId,
            content = actualMessage,
            isAdminDM = true,
            isSent = true
        )
        sendToSession(senderSession, senderConfirmation)
    }
// END

    private fun handleLeaveMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"

        val leaveNotification = MessageBuilder.leaveNotification(userId)
        broadcastMessage(leaveNotification, excludeSession = session.id)
// START
        // Handle admin succession
        adminManager.handleAdminSuccession(
            session.id,
            sessions,
            userSessions,
            ::broadcastMessage
        )

        userSessions.remove(session.id)
        adminManager.unregisterConnection(session.id)
    }
// END
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

    private fun sendErrorToSession(session: WebSocketSession, errorMessage: String) {
        val error = MessageBuilder.error(errorMessage)
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
            val leaveNotification = MessageBuilder.disconnectNotification(userId)
            broadcastMessage(leaveNotification, excludeSession = session.id)
        }
// START
        // Handle admin succession before cleanup
        adminManager.handleAdminSuccession(
            session.id,
            sessions,
            userSessions,
            ::broadcastMessage
        )

        sessions.remove(session.id)
        userSessions.remove(session.id)
        adminManager.unregisterConnection(session.id)
    }
// END

    override fun supportsPartialMessages(): Boolean {
        return false
    }
}