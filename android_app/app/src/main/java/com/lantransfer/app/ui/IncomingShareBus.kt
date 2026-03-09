package com.lantransfer.app.ui

import android.net.Uri
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class IncomingShareEvent(
    val id: Long,
    val uris: List<Uri>,
)

object IncomingShareBus {
    private val nextEventId = AtomicLong(0L)
    private val _events = MutableSharedFlow<IncomingShareEvent>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<IncomingShareEvent> = _events

    fun publish(value: List<Uri>) {
        if (value.isNotEmpty()) {
            _events.tryEmit(
                IncomingShareEvent(
                    id = nextEventId.incrementAndGet(),
                    uris = value,
                )
            )
        }
    }
}
