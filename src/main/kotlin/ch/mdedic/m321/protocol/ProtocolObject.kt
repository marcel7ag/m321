package ch.mdedic.m321.protocol

/*
 * Main protocol object that wraps all communication
 * @author Marcel Dedic
 */
data class ProtocolObject<T>(
    val header: Header,
    val data : Data<T>
)
