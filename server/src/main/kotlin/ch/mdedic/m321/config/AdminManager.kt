// START
package ch.mdedic.m321.config

import com.fasterxml.jackson.databind.ObjectMapper
import dtos.MessageType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages admin functionality including admin assignment and succession
 *
 * @author Marcel Dedic
 */
@Component
class AdminManager(
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // Store connection order (sessionId -> connection timestamp)
    private val connectionOrder = ConcurrentHashMap<String, Long>()

    private var adminSessionId: String? = null

    fun registerConnection(sessionId: String) {
        connectionOrder[sessionId] = System.currentTimeMillis()
    }

    fun unregisterConnection(sessionId: String) {
        connectionOrder.remove(sessionId)
    }

    fun isAdmin(sessionId: String): Boolean {
        return adminSessionId == sessionId
    }

    fun getAdminSessionId(): String? = adminSessionId

    fun getAdminUserId(userSessions: Map<String, String>): String? {
        return adminSessionId?.let { userSessions[it] }
    }

    /**
     * Attempts to set the first admin if none exists
     * Returns true if this session became admin
     */
    fun trySetFirstAdmin(
        session: WebSocketSession,
        userId: String
    ): Boolean {
        if (adminSessionId == null) {
            adminSessionId = session.id
            log.info("User $userId (session ${session.id}) is now the admin (first user)")

            // Notify the user they are admin
            val adminNotification = mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "You are now the admin",
                "isAdmin" to true,
                "timestamp" to System.currentTimeMillis()
            )

            try {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(adminNotification)))
            } catch (e: Exception) {
                log.error("Failed to send admin notification", e)
            }

            return true
        }
        return false
    }

    /**
     * Handles admin succession when the current admin disconnects
     * Returns the new admin session ID if succession occurred, null otherwise
     */
    fun handleAdminSuccession(
        disconnectingSessionId: String,
        sessions: Map<String, WebSocketSession>,
        userSessions: Map<String, String>,
        broadcastMessage: (Map<String, Any>, String?) -> Unit
    ): String? {
        if (adminSessionId != disconnectingSessionId) {
            return null
        }

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

                try {
                    newAdminSession.sendMessage(TextMessage(objectMapper.writeValueAsString(adminNotification)))
                } catch (e: Exception) {
                    log.error("Failed to send admin notification to new admin", e)
                }
            }

            // Broadcast admin change to all users
            val adminChangeNotification = mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "$newAdminUserId is now the admin",
                "timestamp" to System.currentTimeMillis()
            )
            broadcastMessage(adminChangeNotification, nextAdmin.key)

            return nextAdmin.key
        } else {
            // No more users, reset admin
            adminSessionId = null
            log.info("No users left, admin reset")
            return null
        }
    }
}
// END