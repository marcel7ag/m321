package ch.mdedic.m321.config

import dtos.MessageType
import java.util.UUID

/**
 * Builder class for constructing WebSocket messages
 * Provides a clean API for creating different types of messages
 *
 * @author Marcel Dedic
 */
class MessageBuilder {

    companion object {
        /**
         * Creates a system message
         */
        fun system(message: String): Map<String, Any> {
            return mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a system message with admin flag
         */
        fun systemWithAdmin(message: String, isAdmin: Boolean): Map<String, Any> {
            return mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to message,
                "isAdmin" to isAdmin,
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a welcome message
         */
        fun welcome(sessionId: String): Map<String, Any> {
            return mapOf(
                "type" to MessageType.SYSTEM.toString(),
                "message" to "Connected to WebSocket server",
                "sessionId" to sessionId
            )
        }

        /**
         * Creates a join notification
         */
        fun joinNotification(userId: String, isAdmin: Boolean): Map<String, Any> {
            return mapOf(
                "type" to MessageType.JOIN.toString(),
                "userId" to userId,
                "message" to "$userId has joined the chat",
                "isAdmin" to isAdmin,
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a join acknowledgment
         */
        fun joinAck(activeUsers: Collection<String>, isAdmin: Boolean): Map<String, Any> {
            return mapOf(
                "type" to MessageType.JOIN_ACK.toString(),
                "message" to "Successfully joined the chat",
                "activeUsers" to activeUsers.toList(),
                "isAdmin" to isAdmin
            )
        }

        /**
         * Creates a leave notification
         */
        fun leaveNotification(userId: String): Map<String, Any> {
            return mapOf(
                "type" to MessageType.LEAVE.toString(),
                "userId" to userId,
                "message" to "$userId has left the chat",
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a disconnect notification
         */
        fun disconnectNotification(userId: String): Map<String, Any> {
            return mapOf(
                "type" to MessageType.LEAVE.toString(),
                "userId" to userId,
                "message" to "$userId has disconnected",
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a chat message
         */
        fun chat(
            senderId: String,
            content: String,
            recipientId: String = "all",
            isAdmin: Boolean = false
        ): Map<String, Any> {
            return mapOf(
                "type" to MessageType.CHAT.toString(),
                "id" to UUID.randomUUID().toString(),
                "senderId" to senderId,
                "recipientId" to recipientId,
                "content" to content,
                "isAdmin" to isAdmin,
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates a private message (DM)
         */
        fun privateMessage(
            senderId: String,
            recipientId: String,
            content: String,
            isAdminDM: Boolean = false,
            isSent: Boolean = false
        ): Map<String, Any> {
            return mapOf(
                "type" to MessageType.PRIVATE_MESSAGE.toString(),
                "id" to UUID.randomUUID().toString(),
                "senderId" to senderId,
                "recipientId" to recipientId,
                "content" to content,
                "isAdminDM" to isAdminDM,
                "isSent" to isSent,
                "timestamp" to System.currentTimeMillis()
            )
        }

        /**
         * Creates an error message
         */
        fun error(message: String): Map<String, Any> {
            return mapOf(
                "type" to MessageType.ERROR.toString(),
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
        }
    }
}