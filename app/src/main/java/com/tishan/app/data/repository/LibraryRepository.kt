package com.tishan.app.data.repository

import androidx.room.withTransaction
import com.tishan.app.data.exporter.QuestionJsonExporter
import com.tishan.app.data.importer.ImportIssue
import com.tishan.app.data.importer.ImportIssueSeverity
import com.tishan.app.data.importer.ImportedQuestion
import com.tishan.app.data.importer.QuestionImportReport
import com.tishan.app.data.importer.QuestionJsonDto
import com.tishan.app.data.importer.QuestionJsonImporter
import com.tishan.app.data.local.LibraryEntity
import com.tishan.app.data.local.LibrarySummary
import com.tishan.app.data.local.QuestionEntity
import com.tishan.app.data.local.QuestionOptionEntity
import com.tishan.app.data.local.QuestionWithOptions
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.library.LibraryType
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.domain.question.QuestionType
import java.util.UUID

enum class ImportMode {
    NEW,
    UPDATE,
    REPLACE,
}

data class ImportPreview(
    val libraryId: String,
    val libraryType: LibraryType,
    val suggestedName: String,
    val mode: ImportMode,
    val report: QuestionImportReport,
)

data class ImportResult(
    val libraryId: String,
    val mode: ImportMode,
    val report: QuestionImportReport,
    val addedCount: Int,
    val updatedCount: Int,
    val retainedCount: Int,
    val removedCount: Int,
)

class ImportRejectedException(
    val report: QuestionImportReport,
    message: String = "导入文件包含无法安全写入的问题",
) : IllegalArgumentException(message)

/** Room-backed application boundary for P03 library management. */
class LibraryRepository(
    private val database: TishanDatabase,
    private val importer: QuestionJsonImporter = QuestionJsonImporter(),
    private val exporter: QuestionJsonExporter = QuestionJsonExporter(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun listLibraries(): List<LibrarySummary> = database.libraryDao().findSummaries()

    suspend fun findLibrary(libraryId: String): LibraryEntity? = database.libraryDao().findById(libraryId)

    fun previewNew(
        libraryType: LibraryType,
        suggestedName: String,
        rawJson: String,
    ): ImportPreview {
        val libraryId = idGenerator()
        return ImportPreview(
            libraryId = libraryId,
            libraryType = libraryType,
            suggestedName = suggestedName,
            mode = ImportMode.NEW,
            report = parseOrThrow(rawJson, libraryId),
        )
    }

    suspend fun previewExisting(
        libraryId: String,
        rawJson: String,
        mode: ImportMode,
    ): ImportPreview {
        require(mode == ImportMode.UPDATE || mode == ImportMode.REPLACE) {
            "Existing-library preview must use UPDATE or REPLACE"
        }
        val library = database.libraryDao().findById(libraryId)
            ?: throw IllegalArgumentException("题库不存在：$libraryId")
        return ImportPreview(
            libraryId = library.id,
            libraryType = LibraryType.fromExternalValue(library.libraryType)
                ?: throw IllegalArgumentException("题库类型无效：${library.libraryType}"),
            suggestedName = library.name,
            mode = mode,
            report = parseOrThrow(rawJson, library.id),
        )
    }

    suspend fun commit(
        preview: ImportPreview,
        name: String,
    ): ImportResult {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "题库名称不能为空" }
        ensureWritable(preview.report)
        val now = nowMillis()

        return database.withTransaction {
            val libraryDao = database.libraryDao()
            val questionDao = database.questionDao()
            val existingLibrary = libraryDao.findById(preview.libraryId)
            val existingQuestions = if (existingLibrary == null) {
                emptyList()
            } else {
                questionDao.findByLibraryId(preview.libraryId)
            }

            when (preview.mode) {
                ImportMode.NEW -> {
                    check(existingLibrary == null) { "导入目标题库已存在，请重新预检" }
                    libraryDao.insert(
                        LibraryEntity(
                            id = preview.libraryId,
                            name = cleanName,
                            libraryType = preview.libraryType.externalValue,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            warningCount = preview.report.issues.size,
                        ),
                    )
                }

                ImportMode.UPDATE, ImportMode.REPLACE -> {
                    check(existingLibrary != null) { "导入目标题库不存在，请重新预检" }
                    check(existingLibrary.libraryType == preview.libraryType.externalValue) {
                        "不能跨题库类型导入"
                    }
                    libraryDao.update(
                        existingLibrary.copy(
                            name = cleanName,
                            updatedAtEpochMillis = now,
                            warningCount = preview.report.issues.size,
                        ),
                    )
                }
            }

            val existingByKey = existingQuestions.associateBy { it.questionType to it.questionNumber }
            val importedKeys = preview.report.questions.mapTo(mutableSetOf()) {
                it.dto.questionType to it.dto.questionNumber
            }
            var added = 0
            var updated = 0
            for (imported in preview.report.questions) {
                val old = existingByKey[imported.dto.questionType to imported.dto.questionNumber]
                val id = old?.id ?: idGenerator()
                questionDao.upsertWithOptions(
                    imported.toEntity(preview.libraryId, id),
                    imported.dto.options.orEmpty().map { option ->
                        QuestionOptionEntity(
                            questionId = id,
                            optionKey = option.key,
                            text = option.text,
                            position = option.position,
                        )
                    },
                )
                if (old == null) added++ else updated++
            }

            var removed = 0
            if (preview.mode == ImportMode.REPLACE) {
                existingQuestions.filterNot {
                    it.questionType to it.questionNumber in importedKeys
                }.forEach { old ->
                    questionDao.deleteById(old.id)
                    removed++
                }
            }

            ImportResult(
                libraryId = preview.libraryId,
                mode = preview.mode,
                report = preview.report,
                addedCount = added,
                updatedCount = updated,
                retainedCount = if (preview.mode == ImportMode.UPDATE) {
                    existingQuestions.count {
                        it.questionType to it.questionNumber !in importedKeys
                    }
                } else {
                    0
                },
                removedCount = removed,
            )
        }
    }

    suspend fun renameLibrary(libraryId: String, name: String) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "题库名称不能为空" }
        val library = database.libraryDao().findById(libraryId)
            ?: throw IllegalArgumentException("题库不存在：$libraryId")
        database.libraryDao().update(
            library.copy(name = cleanName, updatedAtEpochMillis = nowMillis()),
        )
    }

    suspend fun deleteLibrary(libraryId: String) {
        database.libraryDao().deleteById(libraryId)
    }

    suspend fun exportJson(libraryId: String): String {
        val library = database.libraryDao().findById(libraryId)
            ?: throw IllegalArgumentException("题库不存在：$libraryId")
        val questions = database.questionDao().findAllWithOptionsByLibraryId(library.id)
        return exporter.export(questions.map { it.toDto() })
    }

    private fun parseOrThrow(rawJson: String, libraryId: String): QuestionImportReport =
        importer.parse(rawJson, libraryId)

    private fun ensureWritable(report: QuestionImportReport) {
        val blockingIssues = report.issues.filter {
            it.severity == ImportIssueSeverity.ERROR && it.code != "OPTIONS_EMPTY"
        }
        if (blockingIssues.isNotEmpty() || report.questions.isEmpty()) {
            throw ImportRejectedException(
                report,
                blockingIssues.joinToString("；") { formatIssue(it) }
                    .ifBlank { "导入文件没有可写入的题目" },
            )
        }
    }

    private fun formatIssue(issue: ImportIssue): String = buildString {
        append("第 ${issue.sourceObjectNumber} 个对象")
        issue.questionType?.let { append("（$it") }
        issue.questionNumber?.let { append(" #$it") }
        if (issue.questionType != null) append("）")
        append("：${issue.message}")
    }

    private fun ImportedQuestion.toEntity(libraryId: String, id: String): QuestionEntity {
        val unanswerableReason = (question.availability as? QuestionAvailability.Unanswerable)?.reason
        return QuestionEntity(
            id = id,
            libraryId = libraryId,
            questionType = dto.questionType,
            questionNumber = dto.questionNumber,
            prompt = dto.prompt,
            answerRaw = dto.answer,
            explanation = dto.explanation,
            isAnswerable = unanswerableReason == null,
            unanswerableReason = unanswerableReason,
        )
    }

    private fun QuestionWithOptions.toDto(): QuestionJsonDto {
        val questionType = QuestionType.fromExternalValue(question.questionType)
        val publicOptions = if (questionType == QuestionType.SINGLE_CHOICE ||
            questionType == QuestionType.MULTIPLE_CHOICE
        ) {
            options.sortedBy(QuestionOptionEntity::position).map {
                com.tishan.app.data.importer.QuestionOptionDto(it.optionKey, it.text, it.position)
            }
        } else {
            null
        }
        return QuestionJsonDto(
            questionType = question.questionType,
            questionNumber = question.questionNumber,
            prompt = question.prompt,
            options = publicOptions,
            answer = question.answerRaw,
            explanation = question.explanation,
        )
    }
}
