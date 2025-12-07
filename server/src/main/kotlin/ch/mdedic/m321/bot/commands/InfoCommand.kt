package ch.mdedic.m321.bot.commands

import ch.mdedic.m321.bot.BotCommand
import ch.mdedic.m321.bot.ServerData
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.TimeUnit

/**
 * Bot command that returns client information, admin information and server uptime
 * Returns the registered client name (if available), session ID, current admin, and server uptime
 *
 * @author Marcel Dedic
 */
@Component
class InfoCommand : BotCommand {

    override fun execute(
        session: WebSocketSession,
        userId: String,
        args: List<String>,
// START
        serverData: ServerData
// END
    ): String {
        val sessionId = session.id
        val clientName = if (userId.isNotBlank() && userId != "anonymous") {
            userId
        } else {
            "anonym"
        }

// START
        val adminUserId = serverData.adminUserId ?: "No admin online"
        val adminSessionId = serverData.adminSessionId ?: "N/A"

        val uptimeMillis = System.currentTimeMillis() - serverData.serverStartTime
        val uptimeFormatted = formatUptime(uptimeMillis)

        // Get all connected users from serverData
        val connectedUsers = serverData.connectedUsers
        val userCount = connectedUsers.size

        return buildString {
            appendLine("=== Server Info ===")
            appendLine("Connected Users: $userCount")
            if (connectedUsers.isNotEmpty()) {
                connectedUsers.forEachIndexed { index, user ->
                    val isAdmin = user == adminUserId
                    val badge = if (isAdmin) " [ADMIN]" else ""
                    appendLine("  ${index + 1}. $user$badge")
                    appendLine("     Session-ID: $sessionId")
                }
            }
            appendLine("---")
            appendLine("Admin: $adminUserId")
            appendLine("Admin Session-ID: $adminSessionId")
            appendLine("---")
            append("Server Uptime: $uptimeFormatted")
        }
    }

    private fun formatUptime(uptimeMillis: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(uptimeMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMillis) % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m ${seconds}s"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
// END

    override fun getCommandName(): String = "info"
}