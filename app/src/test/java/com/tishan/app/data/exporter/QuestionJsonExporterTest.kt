package com.tishan.app.data.exporter

import com.tishan.app.data.importer.QuestionJsonDto
import com.tishan.app.data.importer.QuestionJsonImporter
import com.tishan.app.data.importer.QuestionOptionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionJsonExporterTest {
    private val exporter = QuestionJsonExporter()
    private val importer = QuestionJsonImporter()

    @Test
    fun outputIsSortedAndContainsOnlyPublicFields() {
        val json = exporter.export(
            listOf(
                QuestionJsonDto("判断题", 2, "判断", null, "×", null),
                QuestionJsonDto(
                    questionType = "单选题",
                    questionNumber = 1,
                    prompt = "单选",
                    options = listOf(
                        QuestionOptionDto("B", "乙", 1),
                        QuestionOptionDto("A", "甲", 0),
                    ),
                    answer = "A",
                    explanation = "解析",
                ),
            ),
        )
        val root = Json.parseToJsonElement(json) as JsonArray
        assertEquals("单选题", root[0].jsonObject["题型"]!!.toString().trim('"'))
        assertEquals(2, root.size)
        assertTrue(root.all { it is JsonObject })
        assertEquals(
            setOf("题型", "题号", "题目", "选项", "答案", "解析"),
            root.flatMap { it.jsonObject.keys }.toSet(),
        )
        assertFalse(json.contains("internalId"))
        assertFalse(json.contains("收藏"))
    }

    @Test
    fun exportedResourceCanBeParsedAgainIncludingNullFields() {
        val source = listOf(
            QuestionJsonDto("多选题", 4, "动态", listOf(QuestionOptionDto("E", "戊", 0)), "E", null),
            QuestionJsonDto("申论题", 1, "申论", null, "参考答案", "说明"),
        )

        val report = importer.parse(exporter.export(source), "new-library")

        assertEquals(2, report.questionCount)
        assertEquals(0, report.errorCount)
        assertEquals(null, report.questions[0].dto.explanation)
        assertEquals("申论题", report.questions[1].dto.questionType)
    }
}
