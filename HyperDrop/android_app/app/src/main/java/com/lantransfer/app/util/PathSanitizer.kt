package com.lantransfer.app.util

import java.io.File

object PathSanitizer {
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/")) return false
        return path.split("/").none { it.isBlank() || it == "." || it == ".." }
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
