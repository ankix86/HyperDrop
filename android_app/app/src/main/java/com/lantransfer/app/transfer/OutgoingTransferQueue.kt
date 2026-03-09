package com.lantransfer.app.transfer

import android.net.Uri
import com.lantransfer.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class OutgoingTransferQueue(
    private val scope: CoroutineScope,
    private val worker: suspend (Task) -> Unit,
    private val onEvent: (String) -> Unit
) {
    sealed class Task(val fingerprint: String) {
        class UriBatch(val settings: AppSettings, val uris: List<Uri>) : Task(
            "uri:${settings.pcHost}:${settings.pcPort}:${uris.joinToString("|")}"
        )

        class FolderTree(val settings: AppSettings, val treeUri: Uri) : Task(
            "tree:${settings.pcHost}:${settings.pcPort}:$treeUri"
        )
    }

    private val queue = Channel<Pair<Task, Int>>(200)
    private val known = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            for ((task, attempt) in queue) {
                try {
                    worker(task)
                    known.remove(task.fingerprint)
                } catch (t: Throwable) {
                    val reason = "${t::class.java.simpleName}: ${t.message ?: "no details"}"
                    onEvent("PROGRESS:error:0:0")
                    if (t is java.net.ConnectException) {
                        onEvent("The receiver is not accepting files at the moment. Please try again later.")
                    } else {
                        onEvent("transfer failed/cancelled: $reason. Clearing remaining queue.")
                    }
                    clear()
                }
            }
        }
    }

    fun enqueue(task: Task) {
        if (!known.add(task.fingerprint)) {
            onEvent("duplicate transfer skipped")
            return
        }
        scope.launch { queue.send(task to 0) }
    }

    fun clear() {
        while (queue.tryReceive().isSuccess) {
            // Drain the channel
        }
        known.clear()
        onEvent("Queue cleared.")
    }
}
