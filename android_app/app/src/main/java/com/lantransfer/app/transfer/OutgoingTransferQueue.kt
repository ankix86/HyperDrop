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
                    if (attempt < 5) {
                        val delaySec = (1 shl attempt).coerceAtMost(30)
                        onEvent("retrying in ${delaySec}s: $reason")
                        delay(delaySec * 1000L)
                        queue.send(task to (attempt + 1))
                    } else {
                        onEvent("transfer failed: $reason")
                        known.remove(task.fingerprint)
                    }
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
}
