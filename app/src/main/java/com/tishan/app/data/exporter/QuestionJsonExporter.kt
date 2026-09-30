package com.tishan.app.data.exporter

import com.tishan.app.data.importer.QuestionJsonDto
import com.tishan.app.domain.question.QuestionType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** Serializes only the six public fields of the shared question-resource contract. */
class QuestionJsonExporter {
    fun export(questions: List<QuestionJsonDto>): String {
        val sorted = questions.sortedWith(
            compareBy<QuestionJsonDto> {
                QuestionType.fromExternalValue(it.questionType)?.ordinal ?: Int.MAX_VALUE
            }.thenBy { it.questionNumber },
        )
        val root = buildJsonArray {
            sorted.forEach { question ->
                add(question.toJsonObject())
            }
        }
        return root.toString()
    }

    private fun QuestionJsonDto.toJsonObject(): JsonObject = buildJsonObject {
        put(TYPE_FIELD, JsonPrimitive(questionType))
        put(NUMBER_FIELD, JsonPrimitive(questionNumber))
        put(PROMPT_FIELD, JsonPrimitive(prompt))
        if (options == null) {
            put(OPTIONS_FIELD, JsonNull)
        } else {
            put(
                OPTIONS_FIELD,
                buildJsonObject {
                    options.sortedBy { it.position }.forEach { option ->
                        put(option.key, JsonPrimitive(option.text))
                    }
                },
            )
        }
        put(ANSWER_FIELD, JsonPrimitive(answer))
        if (explanation == null) put(EXPLANATION_FIELD, JsonNull)
        else put(EXPLANATION_FIELD, JsonPrimitive(explanation))
    }

    private companion object {
        const val TYPE_FIELD = "题型"
        const val NUMBER_FIELD = "题号"
        const val PROMPT_FIELD = "题目"
        const val OPTIONS_FIELD = "选项"
        const val ANSWER_FIELD = "答案"
        const val EXPLANATION_FIELD = "解析"
    }
}
