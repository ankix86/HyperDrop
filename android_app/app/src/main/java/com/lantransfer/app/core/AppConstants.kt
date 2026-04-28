package com.lantransfer.app.core

object AppConstants {
    const val DEFAULT_PORT = 54545
    const val DISCOVERY_PORT = 54546
    const val DISCOVERY_MAGIC = "hyperdrop_discovery_v1"
    const val CHUNK_SIZE = 1 * 1024 * 1024  // 1 MB (was 256 KB)
    const val PIPELINE_WINDOW = 16           // max in-flight unacknowledged chunks
    const val PROTOCOL_VERSION = 1
    const val NOTIFICATION_CHANNEL_ID = "hyperdrop_channel"
    const val NOTIFICATION_ID = 1010
}
