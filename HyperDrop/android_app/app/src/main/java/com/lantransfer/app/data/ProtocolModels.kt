package com.lantransfer.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProtocolMessage(
    val version: Int,
    val type: String,
    val payload: JsonElement
)

@Serializable
data class ManifestEntry(
    val relativePath: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: Long,
    val checksum: String,
    val isDirectory: Boolean
)
