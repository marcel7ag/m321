package ch.mdedic.m321.protocol

/*
 * Generic data container for protocol messages
 * @author Marcel Dedic
 */
data class Data<T> (
    val payload : T
)