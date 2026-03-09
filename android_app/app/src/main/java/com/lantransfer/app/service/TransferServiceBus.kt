package com.lantransfer.app.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingTransferItem(
    val relativePath: String,
    val fileName: String,
    val parentPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val selected: Boolean = !isDirectory,
    val proposedName: String = fileName,
)

data class IncomingTransferRequest(
    val transferId: String,
    val senderName: String,
    val receiveTreeUri: String?,
    val receiveTargetLabel: String,
    val items: List<IncomingTransferItem>,
)

data class IncomingTransferDecision(
    val accepted: Boolean,
    val receiveTreeUri: String?,
    val renameMap: Map<String, String>,
    val acceptedPaths: Set<String>,
    val reason: String = "",
    val remoteCancelled: Boolean = false,
)

object TransferServiceBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val events = _events.asSharedFlow()
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()
    private val _incomingTransfer = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingTransfer = _incomingTransfer.asStateFlow()
    private var pendingIncomingDecision: CompletableDeferred<IncomingTransferDecision>? = null

    fun emit(text: String) {
        _events.tryEmit(text)
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    suspend fun requestIncomingTransfer(request: IncomingTransferRequest): IncomingTransferDecision {
        val deferred = synchronized(this) {
            val active = pendingIncomingDecision
            if (active != null && !active.isCompleted) {
                return IncomingTransferDecision(
                    accepted = false,
                    receiveTreeUri = request.receiveTreeUri,
                    renameMap = emptyMap(),
                    acceptedPaths = emptySet(),
                    reason = "Another incoming transfer is awaiting approval",
                    remoteCancelled = false,
                )
            }

            CompletableDeferred<IncomingTransferDecision>().also {
                pendingIncomingDecision = it
                _incomingTransfer.value = request
            }
        }

        return try {
            deferred.await()
        } finally {
            synchronized(this) {
                if (pendingIncomingDecision === deferred) {
                    pendingIncomingDecision = null
                }
                if (_incomingTransfer.value?.transferId == request.transferId) {
                    _incomingTransfer.value = null
                }
            }
        }
    }

    @Synchronized
    fun respondIncomingTransfer(decision: IncomingTransferDecision) {
        val deferred = pendingIncomingDecision ?: return
        pendingIncomingDecision = null
        _incomingTransfer.value = null
        if (!deferred.isCompleted) {
            deferred.complete(decision)
        }
    }

    fun declinePendingIncoming(reason: String = "Receiver unavailable") {
        respondIncomingTransfer(
            IncomingTransferDecision(
                accepted = false,
                receiveTreeUri = null,
                renameMap = emptyMap(),
                acceptedPaths = emptySet(),
                reason = reason,
                remoteCancelled = false,
            )
        )
    }

    fun senderClosedPendingIncoming(transferId: String, reason: String = "Sender closed the session") {
        val current = synchronized(this) {
            val deferred = pendingIncomingDecision ?: return
            val active = _incomingTransfer.value
            if (active != null && active.transferId != transferId) {
                return
            }
            pendingIncomingDecision = null
            if (!deferred.isCompleted) {
                deferred.complete(
                    IncomingTransferDecision(
                        accepted = false,
                        receiveTreeUri = active?.receiveTreeUri,
                        renameMap = emptyMap(),
                        acceptedPaths = emptySet(),
                        reason = reason,
                        remoteCancelled = true,
                    )
                )
            }
            active
        }
        if (current != null) {
            _events.tryEmit("INCOMING_TRANSFER:sender_closed:$transferId")
            _incomingTransfer.value = null
        }
    }
}
