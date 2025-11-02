// START
package ch.mdedic.m321.bot.commands

import ch.mdedic.m321.bot.BotCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * Bot command that returns client information
 * Returns the registered client name (if available) and the session ID
 *
 * @author Marcel Dedic
 */
@Component
class InfoCommand : BotCommand {

    override fun execute(session: WebSocketSession, userId: String, args: List<String>): String {
        val sessionId = session.id
        val clientName = if (userId.isNotBlank() && userId != "anonymous") {
            userId
        } else {
            "anonym"
        }

        return buildString {
            appendLine("Client-ID: $sessionId")
            append("[SYSTEM] Client-Name: $clientName")
        }
    }

    override fun getCommandName(): String = "info"
}
// END