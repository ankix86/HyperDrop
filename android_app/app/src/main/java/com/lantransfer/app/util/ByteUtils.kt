package com.lantransfer.app.util

fun Int.toByteArray(): ByteArray {
    return byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(),
        ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte()
    )
}
