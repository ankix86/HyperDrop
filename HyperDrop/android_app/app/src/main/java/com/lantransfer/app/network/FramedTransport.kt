package com.lantransfer.app.network

import com.lantransfer.app.data.ProtocolMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class FramedTransport(private val socket: Socket) {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(message: ProtocolMessage) {
        withContext(Dispatchers.IO) {
            val bytes = json.encodeToString(ProtocolMessage.serializer(), message).encodeToByteArray()
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }

    suspend fun receive(): ProtocolMessage {
        return withContext(Dispatchers.IO) {
            val len = input.readInt()
            require(len in 1..(16 * 1024 * 1024)) { "Invalid frame length" }
            val buf = ByteArray(len)
            input.readFully(buf)
            json.decodeFromString(ProtocolMessage.serializer(), buf.decodeToString())
        }
    }

    fun close() = socket.close()
}
