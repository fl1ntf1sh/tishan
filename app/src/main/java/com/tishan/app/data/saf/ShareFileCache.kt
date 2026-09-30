package com.tishan.app.data.saf

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareFileCache(private val context: Context) {
    suspend fun createJsonFile(displayName: String, content: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared-resources").apply { mkdirs() }
        cleanupExpired(directory)
        val safeName = displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "题库资源" }
        val file = File(directory, "$safeName-${UUID.randomUUID()}.json")
        file.writeText(content, Charsets.UTF_8)
        file
    }

    fun uriFor(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    fun scheduleCleanup(file: File, delayMillis: Long = 10 * 60 * 1000L) {
        Handler(Looper.getMainLooper()).postDelayed(
            { if (file.exists()) file.delete() },
            delayMillis,
        )
    }

    private fun cleanupExpired(directory: File) {
        val threshold = System.currentTimeMillis() - 30 * 60 * 1000L
        directory.listFiles()?.filter { it.lastModified() < threshold }?.forEach(File::delete)
    }
}
