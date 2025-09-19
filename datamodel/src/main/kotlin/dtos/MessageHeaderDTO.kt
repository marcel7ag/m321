package dtos

data class MessageHeaderDTO (
    val id: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long
)