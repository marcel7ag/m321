package ch.mdedic.m321.protocol

/*
 * Header information for protocol messages
 * @author Marcel Dedic
 */
data class Header(
    val message : String,
    val sender : String? = null,
    val recipient: String? = null,
    val timestamp: Long
)