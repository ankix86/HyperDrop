package com.lantransfer.app.util

import java.io.File

object PathSanitizer {
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/")) return false
        return path.split("/").none { it.isBlank() || it == "." || it == ".." }
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim('.')
            .trim()
        return when {
            cleaned.isBlank() -> "received_file"
            cleaned == "." || cleaned == ".." -> "received_file"
            else -> cleaned
        }
    }

    fun safeJoin(base: File, relative: String): File {
        require(isSafeRelativePath(relative)) { "Unsafe relative path: $relative" }
        val target = File(base, relative)
        val baseCanonical = base.canonicalFile
        val targetCanonical = target.canonicalFile
        require(targetCanonical.path.startsWith(baseCanonical.path)) { "Path traversal detected" }
        return targetCanonical
    }
}
