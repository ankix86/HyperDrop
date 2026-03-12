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
import com.lantransfer.app.service.IncomingTransferDecision
import com.lantransfer.app.service.IncomingTransferItem
import com.lantransfer.app.service.IncomingTransferRequest
import com.lantransfer.app.service.TransferServiceBus
import com.lantransfer.app.settings.AppSettings
import com.lantransfer.app.storage.StorageRepository
import com.lantransfer.app.util.PathSanitizer
import com.lantransfer.app.util.asObject
import com.lantransfer.app.util.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID

class TransferEngine(private val context: Context) {
    private data class ApprovedIncomingTransferContext(
        val transferId: String,
        var receiveTreeUri: String?,
        var renameMap: Map<String, String>,
        var allowedPaths: Set<String>,
        val senderFiltersSelection: Boolean,
        var awaitingManifestDecision: Boolean,
    )

    @Volatile
    private var isCancelled = false
    @Volatile
    private var activeClientSocket: Socket? = null

    fun cancelTransfer() {
        isCancelled = true
        runCatching { activeClientSocket?.close() }
    }

    private val crypto = CryptoEngine()
    private val protocol = SessionProtocol(crypto)
    private val manifestBuilder = TransferManifestBuilder(context)
    private val storageRepo = StorageRepository(context)

    suspend fun sendUrisToPc(settings: AppSettings, uris: List<Uri>, onEvent: (String) -> Unit) {
        isCancelled = false
        sendBuiltManifest(settings, manifestBuilder.buildFromUris(uris), onEvent)
    }

    suspend fun sendTreeToPc(settings: AppSettings, treeUri: Uri, onEvent: (String) -> Unit) {
        isCancelled = false
        sendBuiltManifest(settings, manifestBuilder.buildFromTreeUri(treeUri), onEvent)
    }

    private suspend fun sendBuiltManifest(
        settings: AppSettings,
        built: TransferManifestBuilder.BuiltManifest,
        onEvent: (String) -> Unit,
    ) {
        require(settings.pcHost.isNotBlank()) { "PC host is required" }
        val transferId = UUID.randomUUID().toString()
        val transferSalt = java.security.SecureRandom().generateSeed(8)

        withContext(Dispatchers.IO) {
            val socket = Socket(settings.pcHost, settings.pcPort)
            activeClientSocket = socket
            try {
                socket.use { clientSocket ->
                    val transport = FramedTransport(clientSocket)
                    val session = protocol.handshakeAsClient(transport, settings.deviceId, settings.deviceName)
                    onEvent("Connected with remote device")

                    transport.send(
                        ProtocolMessage(
                            AppConstants.PROTOCOL_VERSION,
                            "transfer_offer",
                            JsonObject(
                                mapOf(
                                    "transfer_id" to JsonPrimitive(transferId),
                                    "entries" to JsonArray(
                                        built.entries.map { entry ->
                                            JsonObject(
                                                mapOf(
                                                    "relative_path" to JsonPrimitive(entry.relativePath),
                                                    "file_name" to JsonPrimitive(entry.fileName),
                                                    "size" to JsonPrimitive(entry.size),
                                                    "is_directory" to JsonPrimitive(entry.isDirectory)
                                                )
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    )

                    onEvent("SENDER_REQUEST:waiting")
                    val accepted = transport.receive()
                    when (accepted.type) {
                        "transfer_accept" -> onEvent("SENDER_REQUEST:accepted")
                        "transfer_decline" -> {
                            onEvent("SENDER_REQUEST:rejected")
                            val reason = accepted.payload.asObject()["reason"]?.toString()?.trim('"')
                            throw IllegalStateException(reason ?: "Transfer declined by receiver")
                        }
                        else -> {
                            onEvent("SENDER_REQUEST:rejected")
                            throw IllegalStateException("Unexpected response ${accepted.type}")
                        }
                    }

                    val acceptedPaths = (accepted.payload.asObject()["accepted_paths"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.toSet()
                        ?: emptySet()
                    val filteredEntries = if (acceptedPaths.isEmpty()) {
                        built.entries
                    } else {
                        built.entries.filter { it.relativePath in acceptedPaths }
                    }
                    val filteredSources = if (acceptedPaths.isEmpty()) {
                        built.sourceByRelativePath
                    } else {
                        built.sourceByRelativePath.filterKeys { it in acceptedPaths }
                    }

                    val entriesJson = JsonArray(filteredEntries.map { entryToJson(it) })
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

                    filteredEntries.filter { !it.isDirectory }.forEach { entry ->
                        val src = filteredSources[entry.relativePath] ?: error("missing source")
                        val totalBytes = entry.size
                        sendFile(transport, session.sessionKey, transferSalt, transferId, entry.relativePath, src) { sent ->
                            onEvent("PROGRESS:${entry.relativePath}:$sent:$totalBytes")
                        }
                        onEvent("sent ${entry.relativePath}")
                        if (isCancelled) {
                            transport.send(protocol.msg("cancel", mapOf("transfer_id" to JsonPrimitive(transferId))))
                            onEvent("Transfer cancelled")
                            throw java.util.concurrent.CancellationException("Transfer cancelled by user")
                        }
                    }

                    if (!isCancelled) {
                        transport.send(protocol.msg("transfer_complete", mapOf("transfer_id" to JsonPrimitive(transferId))))
                        onEvent("transfer complete")
                    }
                }
            } finally {
                activeClientSocket = null
            }
        }
    }

    suspend fun runServer(
        settingsProvider: () -> AppSettings,
        onEvent: (String) -> Unit,
        isRunning: () -> Boolean,
        incomingTransferHandler: suspend (IncomingTransferRequest) -> IncomingTransferDecision = { request ->
            IncomingTransferDecision(
                accepted = true,
                receiveTreeUri = request.receiveTreeUri,
                renameMap = emptyMap(),
                acceptedPaths = request.items.mapTo(linkedSetOf()) { it.relativePath },
            )
        },
    ) {
        withContext(Dispatchers.IO) {
            isCancelled = false
            val initialSettings = settingsProvider()
            val serverSocket = ServerSocket(initialSettings.localPort)
            onEvent("Android receiver listening on ${initialSettings.localPort}")
            try {
                while (isRunning() && !isCancelled) {
                    val socket = serverSocket.accept()
                    try {
                        handleIncomingSocket(socket, settingsProvider(), onEvent, incomingTransferHandler)
                    } catch (e: Exception) {
                        onEvent("PROGRESS:error:0:0")
                        onEvent("Error handling incoming connection: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                onEvent("PROGRESS:error:0:0")
                onEvent("Receiver stopped/failed: ${e.message}")
            } finally {
                serverSocket.close()
            }
        }
    }

    private suspend fun handleIncomingSocket(
        socket: Socket,
        settings: AppSettings,
        onEvent: (String) -> Unit,
        incomingTransferHandler: suspend (IncomingTransferRequest) -> IncomingTransferDecision,
    ) {
        socket.use {
            val transport = FramedTransport(it)
            val hello = transport.receive()
            require(hello.type == "hello") { "Expected hello" }
            val helloPayload = hello.payload.asObject()
            val peerDeviceId = helloPayload.str("device_id")
            val peerName = helloPayload["device_name"]?.jsonPrimitive?.contentOrNull ?: peerDeviceId

            transport.send(
                protocol.msg(
                    "pair_confirm",
                    mapOf(
                        "trusted" to JsonPrimitive(true),
                        "device_id" to JsonPrimitive(settings.deviceId),
                        "device_name" to JsonPrimitive(settings.deviceName)
                    )
                )
            )
            onEvent("Connected with device: $peerName")

            val mine = crypto.generateX25519KeyPair()
            transport.send(
                protocol.msg(
                    "key_exchange",
                    mapOf("public_key_b64" to JsonPrimitive(Base64.encodeToString(mine.publicEncoded, Base64.NO_WRAP)))
                )
            )
            val peerKx = transport.receive()
            val peerPublic = Base64.decode(peerKx.payload.asObject().str("public_key_b64"), Base64.NO_WRAP)
            val shared = crypto.deriveSharedSecret(mine.privateEncoded, peerPublic)
            val transcript = listOf(settings.deviceId, peerDeviceId).sorted().joinToString("|").encodeToByteArray()
            val sessionKey = crypto.deriveSessionKey(shared, transcript)

            transport.send(
                protocol.msg(
                    "auth",
                    mapOf(
                        "ok" to JsonPrimitive(true),
                        "device_id" to JsonPrimitive(settings.deviceId),
                        "device_name" to JsonPrimitive(settings.deviceName)
                    )
                )
            )

            var transferSalt = ByteArray(8)
            var transferId = ""
            var approvalContext: ApprovedIncomingTransferContext? = null
            var skippedPaths = emptySet<String>()
            val checksums = mutableMapOf<String, String>()
            val fileSizes = mutableMapOf<String, Long>()

            data class ReceiveState(
                val out: java.io.OutputStream,
                val tempTarget: StorageRepository.TempTarget,
                val digest: java.security.MessageDigest,
                var receivedBytes: Long = 0L,
            )

            val states = mutableMapOf<String, ReceiveState>()

            while (true) {
                val msg = transport.receive()
                when (msg.type) {
                    "transfer_offer" -> {
                        transferId = msg.payload.asObject().str("transfer_id")
                        val previewEntries = (msg.payload.asObject()["entries"] as? JsonArray)
                            ?.mapNotNull { it as? JsonObject }
                            ?: emptyList()
                        if (previewEntries.isNotEmpty()) {
                            val request = buildIncomingTransferRequest(
                                transferId = transferId,
                                senderName = peerName,
                                receiveTreeUri = settings.receiveTreeUri,
                                entries = previewEntries,
                            )
                            val decision = awaitIncomingTransferDecision(
                                socket = socket,
                                transport = transport,
                                request = request,
                                incomingTransferHandler = incomingTransferHandler,
                            )
                            if (decision.remoteCancelled) {
                                onEvent("Incoming transfer cancelled by sender $peerName")
                                return
                            }
                            if (!decision.accepted) {
                                transport.send(
                                    protocol.msg(
                                        "transfer_decline",
                                        mapOf(
                                            "transfer_id" to JsonPrimitive(transferId),
                                            "reason" to JsonPrimitive(decision.reason.ifBlank { "Transfer declined" }),
                                        )
                                    )
                                )
                                onEvent("Incoming transfer declined from $peerName")
                                return
                            }

                            approvalContext = ApprovedIncomingTransferContext(
                                transferId = transferId,
                                receiveTreeUri = decision.receiveTreeUri,
                                renameMap = decision.renameMap,
                                allowedPaths = decision.acceptedPaths,
                                senderFiltersSelection = true,
                                awaitingManifestDecision = false,
                            )
                            transport.send(
                                protocol.msg(
                                    "transfer_accept",
                                    mapOf(
                                        "transfer_id" to JsonPrimitive(transferId),
                                        "accepted_paths" to JsonArray(
                                            decision.acceptedPaths.sorted().map { JsonPrimitive(it) }
                                        ),
                                    )
                                )
                            )
                        } else {
                            approvalContext = ApprovedIncomingTransferContext(
                                transferId = transferId,
                                receiveTreeUri = settings.receiveTreeUri,
                                renameMap = emptyMap(),
                                allowedPaths = emptySet(),
                                senderFiltersSelection = false,
                                awaitingManifestDecision = true,
                            )
                            transport.send(protocol.msg("transfer_accept", mapOf("transfer_id" to JsonPrimitive(transferId))))
                        }
                    }

                    "manifest" -> {
                        val obj = msg.payload.asObject()
                        transferId = obj.str("transfer_id")
                        transferSalt = Base64.decode(obj.str("transfer_salt_b64"), Base64.NO_WRAP)
                        val entries = (obj["entries"] as? JsonArray)
                            ?.map { it.asObject() }
                            ?: emptyList()
                        checksums.clear()
                        fileSizes.clear()
                        skippedPaths = emptySet()

                        val currentApproval = approvalContext?.takeIf { it.transferId == transferId }
                        if (currentApproval != null && currentApproval.awaitingManifestDecision) {
                            val decision = incomingTransferHandler(
                                buildIncomingTransferRequest(
                                    transferId = transferId,
                                    senderName = peerName,
                                    receiveTreeUri = currentApproval.receiveTreeUri,
                                    entries = entries,
                                )
                            )
                            if (!decision.accepted) {
                                transport.send(
                                    protocol.msg(
                                        "transfer_error",
                                        mapOf(
                                            "transfer_id" to JsonPrimitive(transferId),
                                            "reason" to JsonPrimitive(decision.reason.ifBlank { "Transfer declined" }),
                                        )
                                    )
                                )
                                onEvent("Incoming transfer declined from $peerName")
                                return
                            }
                            currentApproval.receiveTreeUri = decision.receiveTreeUri
                            currentApproval.renameMap = decision.renameMap
                            currentApproval.allowedPaths = decision.acceptedPaths
                            currentApproval.awaitingManifestDecision = false
                        }

                        val manifestPaths = entries.mapTo(linkedSetOf()) { entry ->
                            val relativePath = entry["relative_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            require(PathSanitizer.isSafeRelativePath(relativePath)) { "Unsafe path in manifest: $relativePath" }
                            relativePath
                        }
                        if (currentApproval != null && currentApproval.senderFiltersSelection && currentApproval.allowedPaths.isNotEmpty()) {
                            require(manifestPaths == currentApproval.allowedPaths) { "Incoming manifest does not match the approved file list" }
                        }
                        if (currentApproval != null && !currentApproval.senderFiltersSelection && currentApproval.allowedPaths.isNotEmpty()) {
                            val unexpectedPaths = currentApproval.allowedPaths - manifestPaths
                            require(unexpectedPaths.isEmpty()) { "Approved file selection does not match the incoming manifest" }
                            skippedPaths = manifestPaths - currentApproval.allowedPaths
                        }

                        entries.forEach { entry ->
                            val rel = entry.str("relative_path")
                            if (rel in skippedPaths) {
                                return@forEach
                            }
                            val isDirectory = entry["is_directory"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                            if (!isDirectory) {
                                checksums[rel] = entry.str("checksum")
                                fileSizes[rel] = entry.str("size").toLongOrNull() ?: 0L
                            }
                        }
                        emitReceiveSessionEvent(
                            onEvent = onEvent,
                            payload = buildJsonObject {
                                put("type", JsonPrimitive("start"))
                                put("transfer_id", JsonPrimitive(transferId))
                                put(
                                    "target_label",
                                    JsonPrimitive(
                                        storageRepo.describeReceiveTarget(
                                            currentApproval?.receiveTreeUri ?: settings.receiveTreeUri
                                        )
                                    )
                                )
                                put(
                                    "files",
                                    buildJsonArray {
                                        entries.forEach { entry ->
                                            val rel = entry["relative_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                            if (rel.isBlank() || rel in skippedPaths) {
                                                return@forEach
                                            }
                                            val isDirectory = entry["is_directory"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                                            if (isDirectory) {
                                                return@forEach
                                            }
                                            add(
                                                buildJsonObject {
                                                    put("relative_path", JsonPrimitive(rel))
                                                    put("file_name", JsonPrimitive(entry["file_name"]?.jsonPrimitive?.contentOrNull ?: rel.substringAfterLast('/')))
                                                    put("size", JsonPrimitive(entry["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }

                    "file_chunk" -> {
                        val obj = msg.payload.asObject()
                        val rel = obj.str("relative_path")
                        val idx = obj.str("chunk_index").toInt()
                        val eof = obj.str("eof").toBoolean()
                        require(PathSanitizer.isSafeRelativePath(rel)) { "Unsafe file path: $rel" }

                        if (rel in skippedPaths) {
                            if (!eof) {
                                transport.send(
                                    protocol.msg(
                                        "chunk_ack",
                                        mapOf(
                                            "transfer_id" to JsonPrimitive(transferId),
                                            "relative_path" to JsonPrimitive(rel),
                                            "chunk_index" to JsonPrimitive(idx)
                                        )
                                    )
                                )
                            }
                            continue
                        }

                        if (eof) {
                            val state = states.remove(rel) ?: error("Missing state for $rel")
                            state.out.close()
                            val digest = state.digest.digest().joinToString("") { "%02x".format(it) }
                            val expected = checksums[rel] ?: ""
                            require(expected.isBlank() || digest == expected) { "Checksum mismatch for $rel" }
                            val targetRel = approvalContext?.renameMap?.get(rel) ?: rel
                            val receiveTreeUri = approvalContext?.receiveTreeUri ?: settings.receiveTreeUri
                            val committed = storageRepo.commitTemp(state.tempTarget, targetRel, receiveTreeUri)
                            emitReceiveSessionEvent(
                                onEvent = onEvent,
                                payload = buildJsonObject {
                                    put("type", JsonPrimitive("file_done"))
                                    put("transfer_id", JsonPrimitive(transferId))
                                    put("relative_path", JsonPrimitive(rel))
                                    put("file_name", JsonPrimitive(targetRel.substringAfterLast('/')))
                                    put("size", JsonPrimitive(fileSizes[rel] ?: state.receivedBytes))
                                    put("open_uri", JsonPrimitive(committed?.openUri?.toString().orEmpty()))
                                }
                            )
                            continue
                        }

                        val state = states.getOrPut(rel) {
                            val receiveTreeUri = approvalContext?.receiveTreeUri ?: settings.receiveTreeUri
                            val (out, tempTarget) = storageRepo.openTempDestination(rel, receiveTreeUri)
                            ReceiveState(out, tempTarget, java.security.MessageDigest.getInstance("SHA-256"))
                        }
                        val ciphertext = Base64.decode(obj.str("ciphertext_b64"), Base64.NO_WRAP)
                        val aad = "$transferId:$rel:$idx".encodeToByteArray()
                        val plain = crypto.decryptChunk(sessionKey, transferSalt, idx, aad, ciphertext)
                        state.out.write(plain)
                        state.digest.update(plain)
                        state.receivedBytes += plain.size
                        val total = fileSizes[rel] ?: 0L
                        onEvent("PROGRESS:$rel:${state.receivedBytes}:$total")

                        transport.send(
                            protocol.msg(
                                "chunk_ack",
                                mapOf(
                                    "transfer_id" to JsonPrimitive(transferId),
                                    "relative_path" to JsonPrimitive(rel),
                                    "chunk_index" to JsonPrimitive(idx)
                                )
                            )
                        )
                    }

                    "transfer_complete" -> {
                        emitReceiveSessionEvent(
                            onEvent = onEvent,
                            payload = buildJsonObject {
                                put("type", JsonPrimitive("complete"))
                                put("transfer_id", JsonPrimitive(transferId))
                            }
                        )
                        onEvent("Incoming transfer complete")
                        approvalContext = null
                        skippedPaths = emptySet()
                        break
                    }

                    "cancel" -> {
                        emitReceiveSessionEvent(
                            onEvent = onEvent,
                            payload = buildJsonObject {
                                put("type", JsonPrimitive("cancelled"))
                                put("transfer_id", JsonPrimitive(transferId))
                            }
                        )
                        onEvent("PROGRESS:cancelled:0:0")
                        approvalContext = null
                        skippedPaths = emptySet()
                        break
                    }

                    else -> transport.send(protocol.msg("transfer_error", mapOf("reason" to JsonPrimitive("Unhandled ${msg.type}"))))
                }
            }
        }
    }

    private suspend fun awaitIncomingTransferDecision(
        socket: Socket,
        transport: FramedTransport,
        request: IncomingTransferRequest,
        incomingTransferHandler: suspend (IncomingTransferRequest) -> IncomingTransferDecision,
    ): IncomingTransferDecision = coroutineScope {
        val previousTimeout = socket.soTimeout
        socket.soTimeout = 250
        val decisionJob = async { incomingTransferHandler(request) }
        try {
            while (true) {
                if (decisionJob.isCompleted) {
                    return@coroutineScope decisionJob.await()
                }
                try {
                    val msg = transport.receive()
                    if (msg.type == "cancel") {
                        val cancelledId = msg.payload.asObject()["transfer_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (cancelledId.isBlank() || cancelledId == request.transferId) {
                            TransferServiceBus.senderClosedPendingIncoming(request.transferId)
                            return@coroutineScope decisionJob.await()
                        }
                    } else {
                        throw IllegalStateException("Unexpected response ${msg.type} while awaiting approval")
                    }
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: EOFException) {
                    TransferServiceBus.senderClosedPendingIncoming(request.transferId)
                    return@coroutineScope decisionJob.await()
                } catch (_: SocketException) {
                    TransferServiceBus.senderClosedPendingIncoming(request.transferId)
                    return@coroutineScope decisionJob.await()
                }
            }
        } finally {
            socket.soTimeout = previousTimeout
        }
        error("Incoming transfer decision loop exited unexpectedly")
    }

    private fun buildIncomingTransferRequest(
        transferId: String,
        senderName: String,
        receiveTreeUri: String?,
        entries: List<JsonObject>,
    ): IncomingTransferRequest {
        val items = entries.mapNotNull { entry ->
            val relativePath = entry["relative_path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (relativePath.isBlank() || !PathSanitizer.isSafeRelativePath(relativePath)) {
                return@mapNotNull null
            }
            IncomingTransferItem(
                relativePath = relativePath,
                fileName = entry["file_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: relativePath.substringAfterLast('/'),
                parentPath = relativePath.substringBeforeLast('/', ""),
                sizeBytes = entry["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                isDirectory = entry["is_directory"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false,
            )
        }
        return IncomingTransferRequest(
            transferId = transferId,
            senderName = senderName,
            receiveTreeUri = receiveTreeUri,
            receiveTargetLabel = storageRepo.describeReceiveTarget(receiveTreeUri),
            items = items,
        )
    }

    private suspend fun sendFile(
        transport: FramedTransport,
        sessionKey: ByteArray,
        transferSalt: ByteArray,
        transferId: String,
        relativePath: String,
        uri: Uri,
        onProgress: ((Long) -> Unit)? = null
    ) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open source")
        input.use { stream ->
            val buf = ByteArray(AppConstants.CHUNK_SIZE)
            var idx = 0
            var sent = 0L
            while (true) {
                if (isCancelled) {
                    throw java.util.concurrent.CancellationException("Transfer cancelled by user")
                }
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
                if (ack.type == "cancel") {
                    throw java.util.concurrent.CancellationException("Transfer cancelled by PC")
                }
                require(ack.type == "chunk_ack") { "Expected chunk_ack" }
                sent += read
                idx += 1
                onProgress?.invoke(sent)
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

    private fun emitReceiveSessionEvent(onEvent: (String) -> Unit, payload: JsonObject) {
        onEvent("RECEIVE_SESSION:${payload}")
    }
}
