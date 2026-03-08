package com.lantransfer.app.ui

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object IncomingShareBus {
    private val _uris = MutableSharedFlow<List<Uri>>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val uris: SharedFlow<List<Uri>> = _uris

    fun publish(value: List<Uri>) {
        if (value.isNotEmpty()) {
            _uris.tryEmit(value)
        }
    }
}
