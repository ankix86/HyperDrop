package com.lantransfer.app.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.lantransfer.app.data.ManifestEntry
import java.security.MessageDigest

class TransferManifestBuilder(private val context: Context) {
    data class BuiltManifest(
        val entries: List<ManifestEntry>,
        val sourceByRelativePath: Map<String, Uri>
    )

    suspend fun buildFromUris(uris: List<Uri>): BuiltManifest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val entries = mutableListOf<ManifestEntry>()
        val sourceMap = mutableMapOf<String, Uri>()
        uris.forEach { uri ->
            val meta = query(uri)
            val rel = meta.name
            entries += ManifestEntry(rel, meta.name, meta.mime ?: "application/octet-stream", meta.size, System.currentTimeMillis(), sha256Suspend(uri), false)
            sourceMap[rel] = uri
        }
        BuiltManifest(entries, sourceMap)
    }

    suspend fun buildFromTreeUri(treeUri: Uri): BuiltManifest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Invalid tree URI")
        val entries = mutableListOf<ManifestEntry>()
        val sourceMap = mutableMapOf<String, Uri>()

        suspend fun walk(node: DocumentFile, base: String) {
            val name = node.name ?: "unnamed"
            val rel = if (base.isBlank()) name else "$base/$name"
            if (node.isDirectory) {
                entries += ManifestEntry(rel, name, "inode/directory", 0, node.lastModified(), "", true)
                node.listFiles().forEach { walk(it, rel) }
            } else if (node.isFile) {
                entries += ManifestEntry(rel, name, node.type ?: "application/octet-stream", node.length(), node.lastModified(), sha256Suspend(node.uri), false)
                sourceMap[rel] = node.uri
            }
        }

        root.listFiles().forEach { walk(it, "") }
        BuiltManifest(entries, sourceMap)
    }

    private data class Meta(val name: String, val size: Long, val mime: String?)

    private fun query(uri: Uri): Meta {
        val resolver = context.contentResolver
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: "file.bin"
                val size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                return Meta(name, size, resolver.getType(uri))
            }
        }
        return Meta("file.bin", 0L, resolver.getType(uri))
    }

    private suspend fun sha256Suspend(uri: Uri): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
