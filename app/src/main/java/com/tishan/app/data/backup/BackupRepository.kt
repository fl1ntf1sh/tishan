package com.tishan.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.tishan.app.data.local.ExamResultEntity
import com.tishan.app.data.local.ExamSessionEntity
import com.tishan.app.data.local.ExamSettingsEntity
import com.tishan.app.data.local.FavoriteEntity
import com.tishan.app.data.local.LibraryEntity
import com.tishan.app.data.local.PracticeAttemptEntity
import com.tishan.app.data.local.PracticeSessionEntity
import com.tishan.app.data.local.QuestionEntity
import com.tishan.app.data.local.QuestionOptionEntity
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.data.local.WrongQuestionEntity
import com.tishan.app.data.settings.SettingsRepository
import com.tishan.app.data.settings.SettingsSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BackupCategory {
    const val QUESTIONS = "questions"
    const val LEARNING = "learning"
    const val EXAMS = "exams"
    const val SETTINGS = "settings"

    val all: Set<String> = setOf(QUESTIONS, LEARNING, EXAMS, SETTINGS)
}

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val applicationId: String,
    val schemaVersion: Int,
    val categories: List<String>,
    val counts: Map<String, Int>,
    val createdAtEpochMillis: Long,
    val settings: SettingsSnapshot? = null,
)

data class BackupPreview(
    val manifest: BackupManifest,
) {
    val categories: List<String> get() = manifest.categories
}

class BackupRejectedException(message: String) : IllegalArgumentException(message)

private data class SnapshotData(
    val libraries: List<LibraryEntity>,
    val questions: List<QuestionEntity>,
    val options: List<QuestionOptionEntity>,
    val practiceSessions: List<PracticeSessionEntity>,
    val favorites: List<FavoriteEntity>,
    val attempts: List<PracticeAttemptEntity>,
    val wrong: List<WrongQuestionEntity>,
    val examSettings: List<ExamSettingsEntity>,
    val examSessions: List<ExamSessionEntity>,
    val examResults: List<ExamResultEntity>,
)

private data class ParsedArchive(
    val manifest: BackupManifest,
    val databaseFile: File,
)

/** Creates and restores a category-selected SQLite snapshot wrapped in a .ts archive. */
class BackupRepository(
    private val context: Context,
    private val database: TishanDatabase,
    private val settingsRepository: SettingsRepository,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    suspend fun export(categories: Set<String>): ByteArray = withContext(Dispatchers.IO) {
        validateCategories(categories)
        val data = collectCurrentData(categories)
        val snapshotFile = temporaryFile("snapshot", ".db")
        try {
            createSnapshotDatabase(snapshotFile, data)
            val manifest = BackupManifest(
                formatVersion = FORMAT_VERSION,
                applicationId = APPLICATION_ID,
                schemaVersion = DATABASE_SCHEMA_VERSION,
                categories = orderedCategories(categories),
                counts = mapOf(
                    BackupCategory.QUESTIONS to data.questions.size,
                    BackupCategory.LEARNING to data.practiceSessions.size + data.favorites.size + data.attempts.size + data.wrong.size,
                    BackupCategory.EXAMS to data.examSettings.size + data.examSessions.size + data.examResults.size,
                    BackupCategory.SETTINGS to if (BackupCategory.SETTINGS in categories) 1 else 0,
                ),
                createdAtEpochMillis = nowMillis(),
                settings = settingsRepository.snapshot().takeIf { BackupCategory.SETTINGS in categories },
            )
            zipArchive(manifest, snapshotFile)
        } finally {
            snapshotFile.delete()
        }
    }

    suspend fun preview(archive: ByteArray): BackupPreview = withContext(Dispatchers.IO) {
        val parsed = readArchive(archive)
        try {
            validateSnapshot(parsed)
            BackupPreview(parsed.manifest)
        } finally {
            parsed.databaseFile.delete()
        }
    }

    suspend fun restore(archive: ByteArray): BackupPreview = withContext(Dispatchers.IO) {
        val parsed = readArchive(archive)
        try {
            validateSnapshot(parsed)
            val source = openDatabase(parsed.databaseFile)
            val data = try {
                readSnapshotData(source)
            } finally {
                source.close()
            }

            database.withTransaction {
                clearCurrentData()
                insertSnapshotData(data, parsed.manifest.categories.toSet())
            }
            settingsRepository.restore(parsed.manifest.settings.takeIf { BackupCategory.SETTINGS in parsed.manifest.categories })
            BackupPreview(parsed.manifest)
        } finally {
            parsed.databaseFile.delete()
        }
    }

    private suspend fun collectCurrentData(categories: Set<String>): SnapshotData {
        val libraries = database.libraryDao().findAll()
        val rows = database.questionDao().findAllWithOptions()
        val learning = BackupCategory.LEARNING in categories
        val exams = BackupCategory.EXAMS in categories
        return SnapshotData(
            libraries = libraries,
            questions = rows.map { it.question },
            options = rows.flatMap { it.options },
            practiceSessions = if (learning) database.practiceDao().findAllSessions() else emptyList(),
            favorites = if (learning) database.practiceDao().findAllFavorites() else emptyList(),
            attempts = if (learning) database.practiceDao().findAllAttempts() else emptyList(),
            wrong = if (learning) database.practiceDao().findAllWrong() else emptyList(),
            examSettings = if (exams) database.examDao().findAllSettings() else emptyList(),
            examSessions = if (exams) database.examDao().findAllSessions() else emptyList(),
            examResults = if (exams) database.examDao().findAllResults() else emptyList(),
        )
    }

    private suspend fun createSnapshotDatabase(file: File, data: SnapshotData) {
        val snapshot = openDatabase(file)
        try {
            snapshot.withTransaction {
                if (data.libraries.isNotEmpty()) snapshot.libraryDao().insertAll(data.libraries)
                if (data.questions.isNotEmpty()) snapshot.questionDao().insertAll(data.questions)
                if (data.options.isNotEmpty()) snapshot.questionDao().insertOptions(data.options)
                if (data.practiceSessions.isNotEmpty()) snapshot.practiceDao().insertSessions(data.practiceSessions)
                if (data.favorites.isNotEmpty()) snapshot.practiceDao().insertFavorites(data.favorites)
                if (data.attempts.isNotEmpty()) snapshot.practiceDao().insertAttempts(data.attempts)
                if (data.wrong.isNotEmpty()) snapshot.practiceDao().insertWrong(data.wrong)
                if (data.examSettings.isNotEmpty()) snapshot.examDao().insertSettings(data.examSettings)
                if (data.examSessions.isNotEmpty()) snapshot.examDao().insertSessions(data.examSessions)
                if (data.examResults.isNotEmpty()) snapshot.examDao().insertResults(data.examResults)
            }
        } finally {
            snapshot.close()
        }
    }

    private suspend fun clearCurrentData() {
        database.examDao().deleteAllResults()
        database.examDao().deleteAllSessions()
        database.examDao().deleteAllSettings()
        database.practiceDao().deleteAllSessions()
        database.practiceDao().deleteAllAttempts()
        database.practiceDao().deleteAllFavorites()
        database.practiceDao().deleteAllWrong()
        database.questionDao().deleteAllOptions()
        database.questionDao().deleteAll()
        database.libraryDao().deleteAll()
    }

    private suspend fun insertSnapshotData(data: SnapshotData, categories: Set<String>) {
        if (data.libraries.isNotEmpty()) database.libraryDao().insertAll(data.libraries)
        if (data.questions.isNotEmpty()) database.questionDao().insertAll(data.questions)
        if (data.options.isNotEmpty()) database.questionDao().insertOptions(data.options)
        if (BackupCategory.LEARNING in categories) {
            if (data.practiceSessions.isNotEmpty()) database.practiceDao().insertSessions(data.practiceSessions)
            if (data.favorites.isNotEmpty()) database.practiceDao().insertFavorites(data.favorites)
            if (data.attempts.isNotEmpty()) database.practiceDao().insertAttempts(data.attempts)
            if (data.wrong.isNotEmpty()) database.practiceDao().insertWrong(data.wrong)
        }
        if (BackupCategory.EXAMS in categories) {
            if (data.examSettings.isNotEmpty()) database.examDao().insertSettings(data.examSettings)
            if (data.examSessions.isNotEmpty()) database.examDao().insertSessions(data.examSessions)
            if (data.examResults.isNotEmpty()) database.examDao().insertResults(data.examResults)
        }
    }

    private suspend fun readSnapshotData(snapshot: TishanDatabase): SnapshotData {
        val libraries = snapshot.libraryDao().findAll()
        val rows = snapshot.questionDao().findAllWithOptions()
        return SnapshotData(
            libraries = libraries,
            questions = rows.map { it.question },
            options = rows.flatMap { it.options },
            practiceSessions = snapshot.practiceDao().findAllSessions(),
            favorites = snapshot.practiceDao().findAllFavorites(),
            attempts = snapshot.practiceDao().findAllAttempts(),
            wrong = snapshot.practiceDao().findAllWrong(),
            examSettings = snapshot.examDao().findAllSettings(),
            examSessions = snapshot.examDao().findAllSessions(),
            examResults = snapshot.examDao().findAllResults(),
        )
    }

    private suspend fun readArchive(archive: ByteArray): ParsedArchive {
        if (archive.size > MAX_ARCHIVE_BYTES) throw BackupRejectedException("备份文件过大")
        var manifestText: String? = null
        val databaseFile = temporaryFile("restore", ".db")
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifestText = readTextEntry(input)
                        DATABASE_ENTRY -> writeDatabaseEntry(input, databaseFile)
                    }
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
            val manifest = runCatching {
                json.decodeFromString<BackupManifest>(manifestText ?: error("缺少备份清单"))
            }.getOrElse { throw BackupRejectedException("备份清单无效：${it.message}") }
            if (!databaseFile.exists() || databaseFile.length() == 0L) throw BackupRejectedException("缺少 SQLite 快照")
            return ParsedArchive(manifest, databaseFile)
        } catch (error: Throwable) {
            databaseFile.delete()
            if (error is BackupRejectedException) throw error
            throw BackupRejectedException("备份文件损坏：${error.message}")
        }
    }

    private suspend fun validateSnapshot(parsed: ParsedArchive) {
        val manifest = parsed.manifest
        if (manifest.formatVersion != FORMAT_VERSION) throw BackupRejectedException("不支持的备份格式")
        if (manifest.applicationId != APPLICATION_ID) throw BackupRejectedException("备份不是题山数据")
        if (manifest.schemaVersion > DATABASE_SCHEMA_VERSION) throw BackupRejectedException("备份需要更新版本的应用")
        validateCategories(manifest.categories.toSet())
        val snapshot = try {
            openDatabase(parsed.databaseFile)
        } catch (error: Throwable) {
            throw BackupRejectedException("SQLite 快照无法打开：${error.message}")
        }
        try {
            val data = readSnapshotData(snapshot)
            if (manifest.counts[BackupCategory.QUESTIONS] != data.questions.size) {
                throw BackupRejectedException("备份题目数量与清单不一致")
            }
            if (BackupCategory.LEARNING !in manifest.categories &&
                (data.practiceSessions.isNotEmpty() || data.favorites.isNotEmpty() || data.attempts.isNotEmpty() || data.wrong.isNotEmpty())
            ) throw BackupRejectedException("备份清单与学习数据不一致")
            if (BackupCategory.EXAMS !in manifest.categories &&
                (data.examSettings.isNotEmpty() || data.examSessions.isNotEmpty() || data.examResults.isNotEmpty())
            ) throw BackupRejectedException("备份清单与模考数据不一致")
            if (BackupCategory.SETTINGS in manifest.categories && manifest.settings == null) {
                throw BackupRejectedException("备份缺少设置数据")
            }
        } catch (error: BackupRejectedException) {
            throw error
        } catch (error: Throwable) {
            throw BackupRejectedException("SQLite 快照校验失败：${error.message}")
        } finally {
            snapshot.close()
        }
    }

    private fun zipArchive(manifest: BackupManifest, databaseFile: File): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            FileInputStream(databaseFile).use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun readTextEntry(input: ZipInputStream): String {
        val bytes = input.readBounded(MAX_ENTRY_BYTES)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun writeDatabaseEntry(input: ZipInputStream, file: File) {
        FileOutputStream(file).use { output ->
            var total = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_ENTRY_BYTES) throw BackupRejectedException("SQLite 快照过大")
                output.write(buffer, 0, read)
            }
        }
    }

    private fun ZipInputStream.readBounded(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw BackupRejectedException("备份清单过大")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun openDatabase(file: File): TishanDatabase =
        Room.databaseBuilder(context, TishanDatabase::class.java, file.absolutePath).build()

    private fun temporaryFile(prefix: String, suffix: String): File {
        val file = File(context.cacheDir, "$prefix-${UUID.randomUUID()}$suffix")
        file.delete()
        return file
    }

    private fun validateCategories(categories: Set<String>) {
        if (BackupCategory.QUESTIONS !in categories) throw BackupRejectedException("备份必须包含题库与题目")
        if (!BackupCategory.all.containsAll(categories)) throw BackupRejectedException("备份包含未知数据类别")
    }

    private fun orderedCategories(categories: Set<String>): List<String> =
        listOf(BackupCategory.QUESTIONS, BackupCategory.LEARNING, BackupCategory.EXAMS, BackupCategory.SETTINGS)
            .filter(categories::contains)

    private companion object {
        const val APPLICATION_ID = "com.tishan.app"
        const val DATABASE_SCHEMA_VERSION = 5
        const val FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATABASE_ENTRY = "snapshot.db"
        const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    }
}
