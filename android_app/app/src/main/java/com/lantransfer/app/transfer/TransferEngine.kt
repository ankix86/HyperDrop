package com.lantransfer.app.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.lantransfer.app.core.AppConstants
import com.lantransfer.app.crypto.CryptoEngine
import com.lantransfer.app.data.ManifestEntry
import com.lantransfer.app.data.ProtocolMessage
import com.lantransfer.app.network.FramedTransport
import com.lantransfer.app.network.SessionProtocol
import com.lantransfer.app.settings.AppSettings
import com.lantransfer.app.storage.StorageRepository
import com.lantransfer.app.util.asObject
import com.lantransfer.app.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class TransferEngine(private val context: Context) {
    private val crypto = CryptoEngine()
    private val protocol = SessionProtocol(crypto)
    private val manifestBuilder = TransferManifestBuilder(context)
    private val storageRepo = StorageRepository(context)

    suspend fun sendUrisToPc(settings: AppSettings, uris: List<Uri>, onEvent: (String) -> Unit) {
        sendBuiltManifest(settings, manifestBuilder.buildFromUris(uris), onEvent)
    }

    suspend fun sendTreeToPc(settings: AppSettings, treeUri: Uri, onEvent: (String) -> Unit) {
        sendBuiltManifest(settings, manifestBuilder.buildFromTreeUri(treeUri), onEvent)
    }

    private suspend fun sendBuiltManifest(settings: AppSettings, built: TransferManifestBuilder.BuiltManifest, onEvent: (String) -> Unit) {
        require(settings.pcHost.isNotBlank()) { "PC host is required" }
        val transferId = UUID.randomUUID().toString()
        val transferSalt = java.security.SecureRandom().generateSeed(8)

        withContext(Dispatchers.IO) {
            Socket(settings.pcHost, settings.pcPort).use { socket ->
                val transport = FramedTransport(socket)
                val session = protocol.handshakeAsClient(transport, settings.deviceId, settings.deviceName, settings.pairingCode)
                onEvent("Pairing confirmed with remote device")

                transport.send(protocol.msg("transfer_offer", mapOf("transfer_id" to JsonPrimitive(transferId))))
                val accepted = transport.receive()
                require(accepted.type == "transfer_accept") { "Transfer declined" }

                val entriesJson = JsonArray(built.entries.map { entryToJson(it) })
                transport.send(
                    ProtocolMessage(
                        AppConstants.PROTOCOL_VERSION,
                        "manifest",
                        JsonObject(
                            mapOf(
                                "transfer_id" to JsonPrimitive(transferId),
                                "sender_device_id" to JsonPrimitive(settings.deviceId),
                                "receiver_device_id" to JsonPrimitive("pc"),
                                "transfer_salt_b64" to JsonPrimitive(Base64.encodeToString(transferSalt, Base64.NO_WRAP)),
                                "entries" to entriesJson
                            )
                        )
                    )
                )

                built.entries.filter { !it.isDirectory }.forEach { entry ->
                    val src = built.sourceByRelativePath[entry.relativePath] ?: error("missing source")
                    sendFile(transport, session.sessionKey, transferSalt, transferId, entry.relativePath, src)
                    onEvent("sent ${entry.relativePath}")
                }

                transport.send(protocol.msg("transfer_complete", mapOf("transfer_id" to JsonPrimitive(transferId))))
                onEvent("transfer complete")
            }
        }
    }

    suspend fun runServer(settings: AppSettings, onEvent: (String) -> Unit, isRunning: () -> Boolean) {
        withContext(Dispatchers.IO) {
            val ss = ServerSocket(settings.pcPort)
            onEvent("Android receiver listening on ${settings.pcPort}")
            try {
                while (isRunning()) {
                    val socket = ss.accept()
                    handleIncomingSocket(socket, settings, onEvent)
                }
            } finally {
                ss.close()
            }
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket, settings: AppSettings, onEvent: (String) -> Unit) {
        socket.use {
            val transport = FramedTransport(it)
            val hello = transport.receive()
            require(hello.type == "hello") { "Expected hello" }
            val peerDeviceId = hello.payload.asObject().str("device_id")

            transport.send(protocol.msg("pair_request", mapOf(
                "device_id" to JsonPrimitive(settings.deviceId),
                "device_name" to JsonPrimitive(settings.deviceName),
                "reason" to JsonPrimitive("pairing required")
            )))
            val pair = transport.receive()
            require(pair.type == "pair_confirm") { "Expected pair_confirm" }
            if (pair.payload.asObject().str("pairing_code") != settings.pairingCode) {
                transport.send(protocol.msg("auth", mapOf("ok" to JsonPrimitive(false), "reason" to JsonPrimitive("Invalid pairing code"))))
                return
            }
            transport.send(protocol.msg("pair_confirm", mapOf("trusted" to JsonPrimitive(true))))
            onEvent("Pairing confirmed with device: $peerDeviceId")

            val mine = crypto.generateX25519KeyPair()
            transport.send(protocol.msg("key_exchange", mapOf("public_key_b64" to JsonPrimitive(Base64.encodeToString(mine.publicEncoded, Base64.NO_WRAP)))))
            val peerKx = transport.receive()
            val peerPublic = Base64.decode(peerKx.payload.asObject().str("public_key_b64"), Base64.NO_WRAP)
            val shared = crypto.deriveSharedSecret(mine.privateEncoded, peerPublic)
            val transcript = listOf(settings.deviceId, peerDeviceId).sorted().joinToString("|").encodeToByteArray()
            val sessionKey = crypto.deriveSessionKey(shared, transcript)

            transport.send(protocol.msg("auth", mapOf("ok" to JsonPrimitive(true), "device_id" to JsonPrimitive(settings.deviceId), "device_name" to JsonPrimitive(settings.deviceName))))

            var transferSalt = ByteArray(8)
            var transferId = ""
            val checksums = mutableMapOf<String, String>()

            data class ReceiveState(val out: java.io.OutputStream, val tempTarget: StorageRepository.TempTarget, val digest: java.security.MessageDigest)
            val states = mutableMapOf<String, ReceiveState>()

            while (true) {
                val msg = transport.receive()
                when (msg.type) {
                    "transfer_offer" -> {
                        transferId = msg.payload.asObject().str("transfer_id")
                        transport.send(protocol.msg("transfer_accept", mapOf("transfer_id" to JsonPrimitive(transferId))))
                    }
                    "manifest" -> {
                        val obj = msg.payload.asObject()
                        transferSalt = Base64.decode(obj.str("transfer_salt_b64"), Base64.NO_WRAP)
                        val arr = obj["entries"] as JsonArray
                        arr.forEach { e ->
                            val eo = e.asObject()
                            val rel = eo.str("relative_path")
                            val isDir = eo["is_directory"].toString().toBoolean()
                            if (!isDir) checksums[rel] = eo.str("checksum")
                        }
                    }
                    "file_chunk" -> {
                        val obj = msg.payload.asObject()
                        val rel = obj.str("relative_path")
                        val idx = obj.str("chunk_index").toInt()
                        val eof = obj.str("eof").toBoolean()

                        if (eof) {
                            val st = states.remove(rel) ?: error("Missing state for $rel")
                            st.out.close()
                            val digest = st.digest.digest().joinToString("") { "%02x".format(it) }
                            val expected = checksums[rel] ?: ""
                            require(expected.isBlank() || digest == expected) { "Checksum mismatch for $rel" }
                            storageRepo.commitTemp(st.tempTarget, rel, settings.receiveTreeUri)
                            continue
                        }

                        val st = states.getOrPut(rel) {
                            val (out, tempTarget) = storageRepo.openTempDestination(rel, settings.receiveTreeUri)
                            ReceiveState(out, tempTarget, java.security.MessageDigest.getInstance("SHA-256"))
                        }
                        val ciphertext = Base64.decode(obj.str("ciphertext_b64"), Base64.NO_WRAP)
                        val aad = "$transferId:$rel:$idx".encodeToByteArray()
                        val plain = crypto.decryptChunk(sessionKey, transferSalt, idx, aad, ciphertext)
                        st.out.write(plain)
                        st.digest.update(plain)

                        transport.send(protocol.msg("chunk_ack", mapOf(
                            "transfer_id" to JsonPrimitive(transferId),
                            "relative_path" to JsonPrimitive(rel),
                            "chunk_index" to JsonPrimitive(idx)
                        )))
                    }
                    "transfer_complete" -> {
                        onEvent("Incoming transfer complete")
                        break
                    }
                    "cancel" -> break
                    "ping" -> transport.send(protocol.msg("pong", mapOf("ts" to JsonPrimitive(System.currentTimeMillis()))))
                    else -> transport.send(protocol.msg("transfer_error", mapOf("reason" to JsonPrimitive("Unhandled ${msg.type}"))))
                }
            }
        }
    }

    private suspend fun sendFile(transport: FramedTransport, sessionKey: ByteArray, transferSalt: ByteArray, transferId: String, relativePath: String, uri: Uri) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open source")
        input.use { stream ->
            val buf = ByteArray(AppConstants.CHUNK_SIZE)
            var idx = 0
            var sent = 0L
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                val plain = if (read == buf.size) buf else buf.copyOf(read)
                val aad = "$transferId:$relativePath:$idx".encodeToByteArray()
                val encrypted = crypto.encryptChunk(sessionKey, transferSalt, idx, aad, plain)
                transport.send(
                    ProtocolMessage(
                        AppConstants.PROTOCOL_VERSION,
                        "file_chunk",
                        JsonObject(
                            mapOf(
                                "transfer_id" to JsonPrimitive(transferId),
                                "relative_path" to JsonPrimitive(relativePath),
                                "chunk_index" to JsonPrimitive(idx),
                                "offset" to JsonPrimitive(sent),
                                "ciphertext_b64" to JsonPrimitive(Base64.encodeToString(encrypted, Base64.NO_WRAP)),
                                "eof" to JsonPrimitive(false)
                            )
                        )
                    )
                )
                val ack = transport.receive()
                require(ack.type == "chunk_ack") { "Expected chunk_ack" }
                sent += read
                idx += 1
            }

            transport.send(
                ProtocolMessage(
                    AppConstants.PROTOCOL_VERSION,
                    "file_chunk",
                    JsonObject(
                        mapOf(
                            "transfer_id" to JsonPrimitive(transferId),
                            "relative_path" to JsonPrimitive(relativePath),
                            "chunk_index" to JsonPrimitive(idx),
                            "offset" to JsonPrimitive(sent),
                            "ciphertext_b64" to JsonPrimitive(""),
                            "eof" to JsonPrimitive(true)
                        )
                    )
                )
            )
        }
    }

    private fun entryToJson(e: ManifestEntry): JsonObject {
        return JsonObject(
            mapOf(
                "relative_path" to JsonPrimitive(e.relativePath),
                "file_name" to JsonPrimitive(e.fileName),
                "mime_type" to JsonPrimitive(e.mimeType),
                "size" to JsonPrimitive(e.size),
                "modified_time" to JsonPrimitive(e.modifiedTime),
                "checksum" to JsonPrimitive(e.checksum),
                "is_directory" to JsonPrimitive(e.isDirectory)
            )
        )
    }
}
