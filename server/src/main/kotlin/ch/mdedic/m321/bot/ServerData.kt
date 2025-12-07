package ch.mdedic.m321.bot

/**
 * Data class containing server information that can be passed to bot commands
 *
 * @property adminUserId The user ID of the current admin (null if no admin)
 * @property adminSessionId The session ID of the current admin (null if no admin)
 * @property serverStartTime The timestamp when the server started
 *
 * @author Marcel Dedic
 */
data class ServerData(
    val adminUserId: String? = null,
    val adminSessionId: String? = null,
    val serverStartTime: Long = 0L
)