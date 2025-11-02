package ch.mdedic.m321.bot

import org.springframework.web.socket.WebSocketSession

/**
 * Interface for bot commands that can be executed via @server
 * Each command must implement this interface to be registered and executable
 *
 * @author Marcel Dedic
 */
interface BotCommand {
    /**
     * Executes the command with the given session and arguments
     *
     * @param session The WebSocket session of the user who invoked the command
     * @param userId The ID/name of the user who invoked the command
     * @param args Additional arguments passed to the command
     * @return The response message to be sent back to the user
     */
    fun execute(session: WebSocketSession, userId: String, args: List<String>): String

    /**
     * Returns the unique name of the command
     * This name is used to invoke the command (e.g., "info" for @server info)
     *
     * @return The command name
     */
    fun getCommandName(): String
}