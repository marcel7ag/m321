package ch.mdedic.m321.handlers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dtos.MessageType

class MessageHandler(
    private val username: String,
    private val onDisplayMessage: (String) -> Unit
) {
    private val objectMapper = jacksonObjectMapper()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun handleMessage(jsonMessage: String) {
        try {
            val data: Map<String, Any> = objectMapper.readValue(jsonMessage)
            val type = data["type"] as? String ?: return

            when (type) {
                MessageType.SYSTEM.toString() -> handleSystemMessage(data)
                MessageType.JOIN.toString() -> handleJoinMessage(data)
                MessageType.JOIN_ACK.toString() -> handleJoinAck(data)
                MessageType.LEAVE.toString() -> handleLeaveMessage(data)
                MessageType.CHAT.toString() -> handleChatMessage(data)
                MessageType.ERROR.toString() -> handleErrorMessage(data)
            }
        } catch (e: Exception) {
            onDisplayMessage("[ERROR] Failed to parse message: ${e.message}")
        }
    }

    private fun handleSystemMessage(data: Map<String, Any>) {
        val content = data["message"] as? String ?: return
        onDisplayMessage("[SYSTEM] $content")
    }

    private fun handleJoinMessage(data: Map<String, Any>) {
        val userId = data["userId"] as? String ?: "Unknown"
        val message = data["message"] as? String ?: "$userId joined"
        onDisplayMessage("[SYSTEM] $message")
    }

    private fun handleJoinAck(data: Map<String, Any>) {
        val message = data["message"] as? String ?: "Successfully joined"
        onDisplayMessage("[SYSTEM] $message")

        val activeUsers = data["activeUsers"] as? List<*>
        if (activeUsers != null) {
            onDisplayMessage("[SYSTEM] Active users: ${activeUsers.joinToString(", ")}")
        }
    }

    private fun handleLeaveMessage(data: Map<String, Any>) {
        val userId = data["userId"] as? String ?: "Unknown"
        val message = data["message"] as? String ?: "$userId left"
        onDisplayMessage("[SYSTEM] $message")
    }

    private fun handleChatMessage(data: Map<String, Any>) {
        val senderId = data["senderId"] as? String ?: "Unknown"
        val content = data["content"] as? String ?: ""
        val timestamp = (data["timestamp"] as? Number)?.toLong()

        // Don't display our own messages (already shown when sent)
        if (senderId == username) return

        val time = formatTimestamp(timestamp)
        onDisplayMessage("[$time] $senderId: $content")
    }

    private fun handleErrorMessage(data: Map<String, Any>) {
        val content = data["message"] as? String ?: "Unknown error"
        onDisplayMessage("[ERROR] $content")
    }

    private fun formatTimestamp(timestamp: Long?): String {
        return if (timestamp != null) {
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
        } else {
            Instant.now()
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
        }
    }
}