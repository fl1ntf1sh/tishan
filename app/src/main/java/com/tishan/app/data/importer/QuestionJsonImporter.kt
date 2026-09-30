package com.tishan.app.data.importer

import com.tishan.app.domain.question.AnswerRules
import com.tishan.app.domain.question.ExpectedAnswer
import com.tishan.app.domain.question.Question
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.domain.question.QuestionKey
import com.tishan.app.domain.question.QuestionOption
import com.tishan.app.domain.question.QuestionType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class QuestionOptionDto(
    val key: String,
    val text: String,
    val position: Int,
)

data class QuestionJsonDto(
    val questionType: String,
    val questionNumber: Int,
    val prompt: String,
    /** `null` represents the JSON `null` used by judgment and essay questions. */
    val options: List<QuestionOptionDto>?,
    val answer: String,
    val explanation: String?,
)

data class ImportedQuestion(
    val sourceObjectNumber: Int,
    val dto: QuestionJsonDto,
    val question: Question,
)

enum class ImportIssueSeverity {
    WARNING,
    ERROR,
}

data class ImportIssue(
    val sourceObjectNumber: Int,
    val severity: ImportIssueSeverity,
    val code: String,
    val message: String,
    val questionType: String? = null,
    val questionNumber: Int? = null,
)

data class QuestionImportReport(
    val questions: List<ImportedQuestion>,
    val issues: List<ImportIssue>,
) {
    val questionCount: Int get() = questions.size
    val warningCount: Int get() = issues.count { it.severity == ImportIssueSeverity.WARNING }
    val errorCount: Int get() = issues.count { it.severity == ImportIssueSeverity.ERROR }
    val unanswerableCount: Int
        get() = questions.count { it.question.availability is QuestionAvailability.Unanswerable }
}

class JsonImportException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Parses the source array contract without depending on Android, Room, or file APIs. */
class QuestionJsonImporter(
    private val json: Json = Json,
) {
    fun parse(rawJson: String, libraryId: String): QuestionImportReport {
        require(libraryId.isNotBlank()) { "libraryId must not be blank" }

        val root = try {
            json.parseToJsonElement(rawJson)
        } catch (error: SerializationException) {
            throw JsonImportException("JSON syntax is invalid", error)
        }
        val array = root as? JsonArray
            ?: throw JsonImportException("JSON root must be an array of question objects")

        val questions = mutableListOf<ImportedQuestion>()
        val issues = mutableListOf<ImportIssue>()
        val seenKeys = mutableSetOf<Pair<QuestionType, Int>>()

        array.forEachIndexed { index, element ->
            parseItem(
                sourceObjectNumber = index + 1,
                element = element,
                libraryId = libraryId,
                seenKeys = seenKeys,
                issues = issues,
            )?.let(questions::add)
        }

        return QuestionImportReport(questions, issues)
    }

    private fun parseItem(
        sourceObjectNumber: Int,
        element: JsonElement,
        libraryId: String,
        seenKeys: MutableSet<Pair<QuestionType, Int>>,
        issues: MutableList<ImportIssue>,
    ): ImportedQuestion? {
        val objectValue = element as? JsonObject
        if (objectValue == null) {
            issues.error(sourceObjectNumber, "ELEMENT_NOT_OBJECT", "数组元素必须是对象")
            return null
        }

        val unsupportedFields = objectValue.keys - FIELD_NAMES
        if (unsupportedFields.isNotEmpty()) {
            issues.error(
                sourceObjectNumber,
                "UNSUPPORTED_FIELDS",
                "不支持的字段：${unsupportedFields.sorted().joinToString(",")}",
            )
            return null
        }

        val questionTypeRaw = objectValue.requiredString(TYPE_FIELD, sourceObjectNumber, issues)
            ?: return null
        val questionType = QuestionType.fromExternalValue(questionTypeRaw)
        if (questionType == null) {
            issues.error(
                sourceObjectNumber,
                "UNKNOWN_QUESTION_TYPE",
                "不支持的题型：$questionTypeRaw",
                questionType = questionTypeRaw,
            )
            return null
        }

        val questionNumber = objectValue.requiredPositiveInt(NUMBER_FIELD, sourceObjectNumber, issues)
            ?: return null
        if (!seenKeys.add(questionType to questionNumber)) {
            issues.error(
                sourceObjectNumber,
                "DUPLICATE_BUSINESS_KEY",
                "同一文件内重复的题型/题号：${questionType.externalValue} #$questionNumber",
                questionType = questionTypeRaw,
                questionNumber = questionNumber,
            )
            return null
        }

        val prompt = objectValue.requiredString(PROMPT_FIELD, sourceObjectNumber, issues)
            ?: return null
        if (prompt.isBlank()) {
            issues.error(
                sourceObjectNumber,
                "BLANK_PROMPT",
                "题干不能为空",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        val answerRaw = objectValue.requiredString(ANSWER_FIELD, sourceObjectNumber, issues)
            ?: return null
        val optionsIssueStart = issues.size
        val options = parseOptions(
            objectValue = objectValue,
            questionType = questionType,
            sourceObjectNumber = sourceObjectNumber,
            questionTypeRaw = questionTypeRaw,
            questionNumber = questionNumber,
            issues = issues,
        )
        val optionErrors = issues.subList(optionsIssueStart, issues.size)
            .filter { it.severity == ImportIssueSeverity.ERROR }
            .filterNot { it.code == "OPTIONS_EMPTY" }
        if (optionErrors.isNotEmpty()) return null

        val explanationIssueStart = issues.size
        val explanation = parseExplanation(
            objectValue = objectValue,
            sourceObjectNumber = sourceObjectNumber,
            questionTypeRaw = questionTypeRaw,
            questionNumber = questionNumber,
            issues = issues,
        )
        if (issues.subList(explanationIssueStart, issues.size)
                .any { it.severity == ImportIssueSeverity.ERROR }
        ) return null

        val expectedAnswer = parseAnswer(
            questionType = questionType,
            answerRaw = answerRaw,
            options = options,
            sourceObjectNumber = sourceObjectNumber,
            questionTypeRaw = questionTypeRaw,
            questionNumber = questionNumber,
            issues = issues,
        ) ?: return null

        if (questionType == QuestionType.MULTIPLE_CHOICE && expectedAnswer is ExpectedAnswer.Choice) {
            if (expectedAnswer.keys.size < 2) {
                issues.warning(
                    sourceObjectNumber,
                    "MULTIPLE_CHOICE_SINGLE_ANSWER",
                    "多选题答案只有一个选项，保留原题并提示数据异常",
                    questionTypeRaw,
                    questionNumber,
                )
            }
        }

        val answerable = questionType !in CHOICE_TYPES || !options.isNullOrEmpty()
        val availability = if (answerable) {
            QuestionAvailability.Answerable
        } else {
            QuestionAvailability.Unanswerable("选项为空")
        }

        val question = try {
            Question(
                key = QuestionKey(libraryId, questionType, questionNumber),
                prompt = prompt,
                options = options.orEmpty().map { option ->
                    QuestionOption(option.key, option.text, option.position)
                },
                answer = expectedAnswer,
                explanation = explanation,
                availability = availability,
            )
        } catch (error: IllegalArgumentException) {
            issues.error(
                sourceObjectNumber,
                "INVALID_QUESTION",
                error.message ?: "题目不符合领域模型约束",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        return ImportedQuestion(
            sourceObjectNumber = sourceObjectNumber,
            dto = QuestionJsonDto(
                questionType = questionTypeRaw,
                questionNumber = questionNumber,
                prompt = prompt,
                options = options,
                answer = answerRaw,
                explanation = explanation,
            ),
            question = question,
        )
    }

    private fun parseOptions(
        objectValue: JsonObject,
        questionType: QuestionType,
        sourceObjectNumber: Int,
        questionTypeRaw: String,
        questionNumber: Int,
        issues: MutableList<ImportIssue>,
    ): List<QuestionOptionDto>? {
        if (!objectValue.containsKey(OPTIONS_FIELD)) {
            issues.error(
                sourceObjectNumber,
                "MISSING_OPTIONS",
                "缺少选项字段",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        val optionsElement = objectValue[OPTIONS_FIELD]
        if (optionsElement == JsonNull) {
            if (questionType in CHOICE_TYPES) {
                issues.error(
                    sourceObjectNumber,
                    "OPTIONS_EMPTY",
                    "客观题选项为空，保留为不可作答异常题",
                    questionTypeRaw,
                    questionNumber,
                )
            } else if (questionType !in NO_OPTIONS_TYPES) {
                issues.error(
                    sourceObjectNumber,
                    "INVALID_OPTIONS",
                    "该题型的选项字段必须为 null",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
            return null
        }

        val optionsObject = optionsElement as? JsonObject
        if (optionsObject == null) {
            issues.error(
                sourceObjectNumber,
                "INVALID_OPTIONS",
                "选项必须是对象或 null",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        if (questionType !in CHOICE_TYPES) {
            issues.error(
                sourceObjectNumber,
                "INVALID_OPTIONS",
                "判断题和申论题的选项必须为 null",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        if (optionsObject.isEmpty()) {
            issues.error(
                sourceObjectNumber,
                "OPTIONS_EMPTY",
                "客观题选项为空，保留为不可作答异常题",
                questionTypeRaw,
                questionNumber,
            )
            return emptyList()
        }

        val options = mutableListOf<QuestionOptionDto>()
        optionsObject.entries.forEachIndexed { position, (key, value) ->
            if (!key.matches(OPTION_KEY_PATTERN)) {
                issues.error(
                    sourceObjectNumber,
                    "INVALID_OPTION_KEY",
                    "选项键必须是单个大写字母：$key",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
            val text = value.asStringOrNull()
            if (text == null) {
                issues.error(
                    sourceObjectNumber,
                    "INVALID_OPTION_TEXT",
                    "选项文本必须是字符串：$key",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
            options += QuestionOptionDto(key, text, position)
        }
        return options
    }

    private fun parseExplanation(
        objectValue: JsonObject,
        sourceObjectNumber: Int,
        questionTypeRaw: String,
        questionNumber: Int,
        issues: MutableList<ImportIssue>,
    ): String? {
        val element = objectValue[EXPLANATION_FIELD]
        val explanation = when {
            element == null || element == JsonNull -> null
            else -> element.asStringOrNull() ?: run {
                issues.error(
                    sourceObjectNumber,
                    "INVALID_EXPLANATION",
                    "解析必须是字符串、null 或空字符串",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
        }
        if (explanation.isNullOrBlank()) {
            issues.warning(
                sourceObjectNumber,
                "MISSING_EXPLANATION",
                "解析缺失，答题后显示暂无解析",
                questionTypeRaw,
                questionNumber,
            )
        }
        return explanation
    }

    private fun parseAnswer(
        questionType: QuestionType,
        answerRaw: String,
        options: List<QuestionOptionDto>?,
        sourceObjectNumber: Int,
        questionTypeRaw: String,
        questionNumber: Int,
        issues: MutableList<ImportIssue>,
    ): ExpectedAnswer? {
        val answer = try {
            when (questionType) {
                QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE ->
                    AnswerRules.normalizeChoice(answerRaw)

                QuestionType.TRUE_FALSE -> AnswerRules.normalizeTrueFalse(answerRaw)
                QuestionType.ESSAY -> AnswerRules.normalizeEssay(answerRaw)
            }
        } catch (error: IllegalArgumentException) {
            issues.error(
                sourceObjectNumber,
                "INVALID_ANSWER",
                error.message ?: "答案格式无效",
                questionTypeRaw,
                questionNumber,
            )
            return null
        }

        if (answer is ExpectedAnswer.Choice) {
            if (questionType == QuestionType.SINGLE_CHOICE && answer.keys.size != 1) {
                issues.error(
                    sourceObjectNumber,
                    "SINGLE_CHOICE_MULTIPLE_ANSWER",
                    "单选题答案必须只有一个选项",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
            val optionKeys = options.orEmpty().map(QuestionOptionDto::key).toSet()
            if (optionKeys.isNotEmpty() && !optionKeys.containsAll(answer.keys)) {
                val missing = answer.keys - optionKeys
                issues.error(
                    sourceObjectNumber,
                    "ANSWER_OPTION_MISMATCH",
                    "答案包含不存在的选项：${missing.joinToString(",")}",
                    questionTypeRaw,
                    questionNumber,
                )
                return null
            }
        }
        return answer
    }

    private fun JsonObject.requiredString(
        field: String,
        sourceObjectNumber: Int,
        issues: MutableList<ImportIssue>,
    ): String? {
        val value = this[field]?.asStringOrNull()
        if (value == null) {
            issues.error(sourceObjectNumber, "MISSING_OR_INVALID_FIELD", "字段 $field 必须是字符串")
        }
        return value
    }

    private fun JsonObject.requiredPositiveInt(
        field: String,
        sourceObjectNumber: Int,
        issues: MutableList<ImportIssue>,
    ): Int? {
        val primitive = this[field] as? JsonPrimitive
        val value = primitive?.takeUnless(JsonPrimitive::isString)?.content?.toIntOrNull()
        if (value == null || value <= 0) {
            issues.error(sourceObjectNumber, "MISSING_OR_INVALID_FIELD", "字段 $field 必须是正整数")
            return null
        }
        return value
    }

    private fun JsonElement.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.takeIf(JsonPrimitive::isString)?.content
    }

    private fun MutableList<ImportIssue>.warning(
        sourceObjectNumber: Int,
        code: String,
        message: String,
        questionType: String? = null,
        questionNumber: Int? = null,
    ) = add(
        ImportIssue(
            sourceObjectNumber,
            ImportIssueSeverity.WARNING,
            code,
            message,
            questionType,
            questionNumber,
        ),
    )

    private fun MutableList<ImportIssue>.error(
        sourceObjectNumber: Int,
        code: String,
        message: String,
        questionType: String? = null,
        questionNumber: Int? = null,
    ) = add(
        ImportIssue(
            sourceObjectNumber,
            ImportIssueSeverity.ERROR,
            code,
            message,
            questionType,
            questionNumber,
        ),
    )

    private companion object {
        const val TYPE_FIELD = "题型"
        const val NUMBER_FIELD = "题号"
        const val PROMPT_FIELD = "题目"
        const val OPTIONS_FIELD = "选项"
        const val ANSWER_FIELD = "答案"
        const val EXPLANATION_FIELD = "解析"
        val FIELD_NAMES = setOf(TYPE_FIELD, NUMBER_FIELD, PROMPT_FIELD, OPTIONS_FIELD, ANSWER_FIELD, EXPLANATION_FIELD)
        val CHOICE_TYPES = setOf(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE)
        val NO_OPTIONS_TYPES = setOf(QuestionType.TRUE_FALSE, QuestionType.ESSAY)
        val OPTION_KEY_PATTERN = Regex("[A-Z]")
    }
}
