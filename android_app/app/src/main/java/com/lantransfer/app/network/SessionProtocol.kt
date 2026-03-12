package com.lantransfer.app.network

import android.util.Base64
import com.lantransfer.app.core.AppConstants
import com.lantransfer.app.crypto.CryptoEngine
import com.lantransfer.app.data.ProtocolMessage
import com.lantransfer.app.util.asObject
import com.lantransfer.app.util.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SessionProtocol(private val crypto: CryptoEngine) {
    data class SecureSession(
        val transport: FramedTransport,
        val sessionKey: ByteArray,
        val peerDeviceId: String
    )

    fun msg(type: String, payload: Map<String, JsonElement>): ProtocolMessage {
        return ProtocolMessage(AppConstants.PROTOCOL_VERSION, type, JsonObject(payload))
    }

    suspend fun handshakeAsClient(
        transport: FramedTransport,
        localDeviceId: String,
        localName: String
    ): SecureSession {
        try {
            transport.send(msg("hello", mapOf("device_id" to JsonPrimitive(localDeviceId), "device_name" to JsonPrimitive(localName))))

            val pair = transport.receive()
            var peerDeviceId = "unknown"
            if (pair.type == "pair_confirm") {
                val pairObj = pair.payload.asObject()
                if (pairObj.containsKey("device_id")) peerDeviceId = pairObj.str("device_id")
            } else {
                require(false) { "unexpected handshake message ${pair.type}" }
            }

            val serverKx = transport.receive()
            require(serverKx.type == "key_exchange") { "expected key_exchange but got ${serverKx.type}" }
            val peerPublic = Base64.decode(serverKx.payload.asObject().str("public_key_b64"), Base64.NO_WRAP)

            val mine = crypto.generateX25519KeyPair()
            val shared = crypto.deriveSharedSecret(mine.privateEncoded, peerPublic)

            transport.send(msg("key_exchange", mapOf("public_key_b64" to JsonPrimitive(Base64.encodeToString(mine.publicEncoded, Base64.NO_WRAP)))))
            val auth = transport.receive()
            require(auth.type == "auth") { "expected auth but got ${auth.type}" }
            val authObj = auth.payload.asObject()
            require(authObj["ok"]?.toString()?.toBoolean() == true) { "auth rejected: ${authObj["reason"]}" }
            if (peerDeviceId == "unknown" && authObj.containsKey("device_id")) {
                peerDeviceId = authObj.str("device_id")
            }

            val transcript = listOf(localDeviceId, peerDeviceId).sorted().joinToString("|").encodeToByteArray()
            val key = crypto.deriveSessionKey(shared, transcript)

            return SecureSession(transport, key, peerDeviceId)
        } catch (t: Throwable) {
            throw IllegalStateException("handshake failed: ${t.message}", t)
        }
    }
}
