package com.tishan.app.data.saf

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuestionResourceReader(private val context: Context) {
    @Throws(IOException::class)
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
            reader?.readText() ?: throw IOException("无法读取所选文件")
        }
    }

    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return@withContext cursor.getString(0)
        }
        uri.lastPathSegment?.substringAfterLast('/')
    }
}
