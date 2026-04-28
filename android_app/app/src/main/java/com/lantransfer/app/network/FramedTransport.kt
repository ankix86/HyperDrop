package com.lantransfer.app.network

import com.lantransfer.app.data.ProtocolMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class FramedTransport(private val socket: Socket) {
    private val input = DataInputStream(socket.getInputStream())
    // BufferedOutputStream batches small writes so the OS sees larger TCP
    // segments.  We size it at 8 MB so that an entire pipeline window of 1 MB
    // chunks (≈ 1.4 MB each after base-64 encoding) fits without flushing.
    private val buffered = BufferedOutputStream(socket.getOutputStream(), 8 * 1024 * 1024)
    private val output = DataOutputStream(buffered)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(message: ProtocolMessage) {
        withContext(Dispatchers.IO) {
            val bytes = json.encodeToString(ProtocolMessage.serializer(), message).encodeToByteArray()
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }

    /**
     * Write a message to the internal buffer *without* flushing to the OS.
     * Call [flush] after queuing all desired messages to push them at once.
     * This keeps the TCP pipeline full during pipelined chunk transfers.
     */
    suspend fun sendBuffered(message: ProtocolMessage) {
        withContext(Dispatchers.IO) {
            val bytes = json.encodeToString(ProtocolMessage.serializer(), message).encodeToByteArray()
            output.writeInt(bytes.size)
            output.write(bytes)
            // No flush – data stays in BufferedOutputStream until flush() is called.
        }
    }

    /** Flush all buffered messages to the OS socket. */
    suspend fun flush() {
        withContext(Dispatchers.IO) {
            output.flush()
        }
    }

    suspend fun receive(): ProtocolMessage {
        return withContext(Dispatchers.IO) {
            val len = input.readInt()
            require(len in 1..(32 * 1024 * 1024)) { "Invalid frame length" }
            val buf = ByteArray(len)
            input.readFully(buf)
            json.decodeFromString(ProtocolMessage.serializer(), buf.decodeToString())
        }
    }

    fun close() = socket.close()
}
