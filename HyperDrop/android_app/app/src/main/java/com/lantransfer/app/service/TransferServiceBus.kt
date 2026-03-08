package com.lantransfer.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TransferServiceBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val events = _events.asSharedFlow()

    fun emit(text: String) {
        _events.tryEmit(text)
    }
}
