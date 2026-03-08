package com.lantransfer.app.observer

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ScreenshotObserver(
    private val resolver: ContentResolver,
    private val onNewScreenshot: (Uri) -> Unit
) {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (uri == null || !uri.toString().contains("images", ignoreCase = true)) return

            val screenshotUri = if (isLikelyScreenshot(uri)) {
                uri
            } else {
                findLatestRecentScreenshotUri()
            } ?: return

            if (!seen.add(screenshotUri.toString())) return
            onNewScreenshot(screenshotUri)
        }
    }

    private fun isLikelyScreenshot(uri: Uri): Boolean {
        return runCatching {
            val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val name = cursor.getString(0)?.lowercase(Locale.US).orEmpty()
                val relPath = cursor.getString(1)?.lowercase(Locale.US).orEmpty()

                // Covers common OEM/default screenshot naming and folders.
                name.contains("screenshot") || relPath.contains("screenshot") || relPath.contains("screen")
            } ?: false
        }.getOrDefault(false)
    }

    private fun findLatestRecentScreenshotUri(): Uri? {
        return runCatching {
            val nowSec = System.currentTimeMillis() / 1000
            val cutoffSec = nowSec - 20 // new screenshot should appear immediately.

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED,
            )
            val selection = "${MediaStore.Images.Media.DATE_ADDED}>=?"
            val selectionArgs = arrayOf(cutoffSec.toString())

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)?.lowercase(Locale.US).orEmpty()
                    val relPath = cursor.getString(pathIndex)?.lowercase(Locale.US).orEmpty()
                    val isScreenshot = name.contains("screenshot") || relPath.contains("screenshot") || relPath.contains("screen")
                    if (!isScreenshot) continue

                    return@use Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
                null
            }
        }.getOrNull()
    }

    fun start() {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    fun stop() {
        resolver.unregisterContentObserver(observer)
    }
}
