package dtos

/**
 * Enum representing different types of WebSocket messages
 * Used to differentiate between various message purposes in the chat application
 */
enum class MessageType {
    CHAT,
    JOIN,
    LEAVE,
    ERROR,
    SYSTEM,
    JOIN_ACK,
    PRIVATE_MESSAGE,
    STATUS_UPDATE,
    PING,
    PONG
}