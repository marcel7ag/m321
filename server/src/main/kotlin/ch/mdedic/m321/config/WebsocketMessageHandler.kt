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
    private val botCommandHandler: ch.mdedic.m321.bot.BotCommandHandler,
) : org.springframework.web.socket.WebSocketHandler {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper()
// START
    companion object {
        private const val ADMIN_DM_PREFIX = "@serveradmin"
    }
// END
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // Store user information (sessionId -> userId)
    private val userSessions = ConcurrentHashMap<String, String>()

// START
    // Store connection order (sessionId -> connection timestamp)
    private val connectionOrder = ConcurrentHashMap<String, Long>()

    private var adminSessionId: String? = null

    private val serverStartTime: Long = System.currentTimeMillis()

    fun getServerStartTime(): Long = serverStartTime

    fun getAdminUserId(): String? {
        return adminSessionId?.let { userSessions[it] }
    }

    fun getAdminSessionId(): String? = adminSessionId

// END

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("WebSocket connection established: ${session.id}")
        sessions[session.id] = session
// START
        connectionOrder[session.id] = System.currentTimeMillis()
// END

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

// START
        // Set first user as admin
        if (adminSessionId == null) {
            adminSessionId = session.id
            log.info("User $userId (session ${session.id}) is now the admin.")

            // Notify the user they are admin
            val adminNotification = mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "You are now the admin",
                "isAdmin" to true,
                "timestamp" to System.currentTimeMillis()
            )
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(adminNotification)))
        }
// END

        // Broadcast join to all other users
        val joinNotification = mapOf(
            "type" to MessageType.JOIN.toString(),
            "userId" to userId,
            "message" to "$userId has joined the chat",
            "isAdmin" to (session.id == adminSessionId),
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(joinNotification, excludeSession = session.id)

        // Send confirmation of the joining user
// START
        val confirmation = mapOf(
            "type" to MessageType.JOIN_ACK.toString(),
            "message" to "Successfully joined the chat",
            "activeUsers" to userSessions.values.toList(),
            "isAdmin" to (session.id == adminSessionId)
        )
// END

        session.sendMessage(TextMessage(objectMapper.writeValueAsString(confirmation)))
    }

    private fun handleChatMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"
        val content = messageData["content"] as? String ?: ""
        val recipientId = messageData["recipientId"] as? String
// START
        // Check if this is a DM to admin
        if (content.trim().lowercase().startsWith((ADMIN_DM_PREFIX))) {
            handleAdminDM(session, userId, content)
            return
        }
// END

        // Check if this is a bot command
        if (botCommandHandler.isBotCommand(content)) {
            val serverData = ServerData(
                adminUserId = adminSessionId?.let { userSessions[it] },
                adminSessionId = adminSessionId,
                serverStartTime = serverStartTime
            )
            val botResponse = botCommandHandler.processCommand(session, userId, content, serverData)
            sendToSession(session, mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to botResponse,
                "timestamp" to System.currentTimeMillis()
            ))
            return
        }

// START
        val chatMessage = mapOf(
            "type" to MessageType.CHAT.toString(),
            "id" to java.util.UUID.randomUUID().toString(),
            "senderId" to userId,
            "recipientId" to (recipientId ?: "all"),
            "content" to content,
            "isAdmin" to (session.id == adminSessionId),
            "timestamp" to System.currentTimeMillis()
        )
// END

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
            sendToSession(senderSession, mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "Error: Message to admin cannot be empty",
                "timestamp" to System.currentTimeMillis()
            ))
            return
        }

        if (adminSessionId == null) {
            sendToSession(senderSession, mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "Error: No admin is currently online",
                "timestamp" to System.currentTimeMillis()
            ))
            return
        }

        // Check if sender is the admin (admin DMing themselves)
        if (senderSession.id == adminSessionId) {
            sendToSession(senderSession, mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "Error: You cannot DM yourself (you are the admin)",
                "timestamp" to System.currentTimeMillis()
            ))
            return
        }

        val adminUserId = userSessions[adminSessionId] ?: "Admin"

        log.info("Admin DM from $senderId to $adminUserId: $actualMessage")

        // Create DM message
        val dmMessage = mapOf(
            "type" to MessageType.PRIVATE_MESSAGE.toString(),
            "id" to java.util.UUID.randomUUID().toString(),
            "senderId" to senderId,
            "recipientId" to adminUserId,
            "content" to actualMessage,
            "isAdminDM" to true,
            "timestamp" to System.currentTimeMillis()
        )

        // Send to admin
        val adminSession = sessions[adminSessionId]
        if (adminSession != null && adminSession.isOpen) {
            sendToSession(adminSession, dmMessage)
        }

        // Send confirmation to sender with their message
        val senderConfirmation = mapOf(
            "type" to MessageType.PRIVATE_MESSAGE.toString(),
            "id" to java.util.UUID.randomUUID().toString(),
            "senderId" to senderId,
            "recipientId" to adminUserId,
            "content" to actualMessage,
            "isAdminDM" to true,
            "isSent" to true,
            "timestamp" to System.currentTimeMillis()
        )
        sendToSession(senderSession, senderConfirmation)
    }
// END

    private fun handleLeaveMessage(session: WebSocketSession, messageData: Map<*, *>) {
        val userId = userSessions[session.id] ?: "anonymous"

        val leaveNotification = mapOf(
            "type" to MessageType.LEAVE.toString(),
            "userId" to userId,
            "message" to "$userId has left the chat",
            "timestamp" to System.currentTimeMillis()
        )

        broadcastMessage(leaveNotification, excludeSession = session.id)

// START
        handleAdminSuccession(session.id)
// END

        userSessions.remove(session.id)

// START
        connectionOrder.remove(session.id)
// END
    }
// START
    private fun handleAdminSuccession(disconnectingSessionId: String) {
        // Check if the disconnecting user is the admin
        if (adminSessionId == disconnectingSessionId) {
            log.info("Admin is disconnecting, finding next admin...")

            // Find the next oldest connected user
            val nextAdmin = connectionOrder
                .filter { it.key != disconnectingSessionId && sessions[it.key]?.isOpen == true }
                .minByOrNull { it.value }

            if (nextAdmin != null) {
                adminSessionId = nextAdmin.key
                val newAdminUserId = userSessions[nextAdmin.key] ?: "anonymous"
                log.info("User $newAdminUserId (session ${nextAdmin.key}) is now the admin")

                // Notify the new admin
                val newAdminSession = sessions[nextAdmin.key]
                if (newAdminSession != null && newAdminSession.isOpen) {
                    val adminNotification = mapOf(
                        "type" to MessageType.SYSTEM.toString(),
                        "message" to "You are now the admin",
                        "isAdmin" to true,
                        "timestamp" to System.currentTimeMillis()
                    )
                    newAdminSession.sendMessage(TextMessage(objectMapper.writeValueAsString(adminNotification)))
                }

                // Broadcast admin change to all users
                val adminChangeNotification = mapOf(
                    "type" to MessageType.SYSTEM.toString(),
                    "message" to "$newAdminUserId is now the admin",
                    "timestamp" to System.currentTimeMillis()
                )
                broadcastMessage(adminChangeNotification, excludeSession = nextAdmin.key)
            } else {
                // No more users, reset admin
                adminSessionId = null
                log.info("No users left, admin reset")
            }
        }
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