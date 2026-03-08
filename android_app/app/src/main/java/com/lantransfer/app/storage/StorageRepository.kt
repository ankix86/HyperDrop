package com.lantransfer.app.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lantransfer.app.util.PathSanitizer
import java.io.File
import java.io.OutputStream

class StorageRepository(private val context: Context) {
    private val appDir = File(context.filesDir, "received").apply { mkdirs() }

    fun defaultReceivePath(): String = appDir.absolutePath

    fun openTempDestination(relativePath: String, receiveTreeUri: String?): Pair<OutputStream, TempTarget> {
        require(PathSanitizer.isSafeRelativePath(relativePath))
        return if (receiveTreeUri.isNullOrBlank()) {
            val temp = PathSanitizer.safeJoin(appDir, "$relativePath.part")
            temp.parentFile?.mkdirs()
            temp.outputStream() to TempTarget.FileTarget(temp)
        } else {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(receiveTreeUri))
                ?: error("Invalid receive tree")
            val (parent, name) = resolveParent(root, "$relativePath.part")
            val doc = parent.createFile("application/octet-stream", name) ?: error("create temp failed")
            val out = context.contentResolver.openOutputStream(doc.uri, "w") ?: error("open out failed")
            out to TempTarget.SafTarget(doc.uri)
        }
    }

    fun commitTemp(tempTarget: TempTarget, relativePath: String, receiveTreeUri: String?) {
        require(PathSanitizer.isSafeRelativePath(relativePath))
        when (tempTarget) {
            is TempTarget.FileTarget -> {
                val final = PathSanitizer.safeJoin(appDir, relativePath)
                final.parentFile?.mkdirs()
                tempTarget.file.renameTo(resolveConflict(final))
            }
            is TempTarget.SafTarget -> {
                if (receiveTreeUri.isNullOrBlank()) return
                val root = DocumentFile.fromTreeUri(context, Uri.parse(receiveTreeUri)) ?: return
                val (parent, name) = resolveParent(root, relativePath)
                parent.findFile(name)?.delete()
                val finalDoc = parent.createFile("application/octet-stream", name) ?: return
                val input = context.contentResolver.openInputStream(tempTarget.uri) ?: return
                val output = context.contentResolver.openOutputStream(finalDoc.uri, "w") ?: return
                input.use { i -> output.use { o -> i.copyTo(o) } }
                DocumentFile.fromSingleUri(context, tempTarget.uri)?.delete()
            }
        }
    }

    sealed class TempTarget {
        data class FileTarget(val file: File) : TempTarget()
        data class SafTarget(val uri: Uri) : TempTarget()
    }

    private fun resolveParent(root: DocumentFile, relative: String): Pair<DocumentFile, String> {
        val parts = relative.split("/")
        var cur = root
        for (segment in parts.dropLast(1)) {
            cur = cur.findFile(segment) ?: (cur.createDirectory(segment) ?: error("create dir failed"))
        }
        return cur to parts.last()
    }

    private fun resolveConflict(target: File): File {
        if (!target.exists()) return target
        val parent = target.parentFile ?: return target
        val stem = target.nameWithoutExtension
        val ext = target.extension
        for (i in 1..9999) {
            val candidate = if (ext.isBlank()) File(parent, "$stem ($i)") else File(parent, "$stem ($i).$ext")
            if (!candidate.exists()) return candidate
        }
        return target
    }
}
