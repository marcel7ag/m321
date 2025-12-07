// START
package ch.mdedic.m321.bot

/**
 * Data class containing server information that can be passed to bot commands
 *
 * @property adminUserId The user ID of the current admin (null if no admin)
 * @property adminSessionId The session ID of the current admin (null if no admin)
 * @property serverStartTime The timestamp when the server started
 * @property connectedUsers List of all currently connected user IDs
 *
 * @author Marcel Dedic
 */
data class ServerData(
    val adminUserId: String? = null,
    val adminSessionId: String? = null,
    val serverStartTime: Long = 0L,
    val connectedUsers: List<String> = emptyList()
)
// END