package dtos

data class MessageDTO (
    val id: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long
)