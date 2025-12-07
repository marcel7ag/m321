package ch.mdedic.m321.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * Handler that manages and executes bot commands
 * Registers all available bot commands and routes incoming @server messages to the appropriate command
 *
 * @author Marcel Dedic
 */
@Component
class BotCommandHandler(
    // Spring automatically injects all beans that implement BotCommand
    private val commands: List<BotCommand>
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val commandMap: Map<String, BotCommand>

// START
    companion object {
        const val BOT_COMMAND_PREFIX = "@server"
    }
// END

    init {
        // Build a map of command names to command instances for fast lookup
        commandMap = commands.associateBy { it.getCommandName() }
        log.info("Registered bot commands: ${commandMap.keys.joinToString(", ")}")
    }

    /**
     * Checks if a message is a bot command (starts with @server)
     *
     * @param message The message content to check
     * @return true if the message is a bot command, false otherwise
     */
    fun isBotCommand(message: String): Boolean {
        return message.trim().startsWith(BOT_COMMAND_PREFIX)
    }

    /**
     * Processes a bot command and returns the response
     *
     * @param session The WebSocket session of the user
     * @param userId The ID/name of the user
     * @param message The full message content
     * @param serverData Server data to pass to commands (optional)
     * @return The response to send back to the user
     */
    fun processCommand(
        session: WebSocketSession,
        userId: String,
        message: String,
        serverData: ServerData = ServerData()
    ): String {
        return try {
            // Remove @server prefix and split into command and args
            val content = message.trim().removePrefix(BOT_COMMAND_PREFIX).trim()
            val parts = content.split(Regex("\\s+"))

            if (parts.isEmpty() || parts[0].isBlank()) {
                return "Error: No command specified. Available commands: ${commandMap.keys.joinToString(", ")}"
            }

            val commandName = parts[0].lowercase()
            val args = parts.drop(1)

            // Find and execute the command
            val command = commandMap[commandName]
            if (command != null) {
                log.info("Executing bot command '$commandName' for user $userId (session: ${session.id})")
                command.execute(session, userId, args, serverData)
            } else {
                "Error: Unknown command '$commandName'. Available commands: ${commandMap.keys.joinToString(", ")}"
            }
        } catch (e: Exception) {
            log.error("Error processing bot command", e)
            "Error: Failed to process command - ${e.message}"
        }
    }

    /**
     * Returns a list of all available command names
     *
     * @return List of command names
     */
    fun getAvailableCommands(): List<String> {
        return commandMap.keys.toList()
    }
}