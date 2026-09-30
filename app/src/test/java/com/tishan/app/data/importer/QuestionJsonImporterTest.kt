package com.tishan.app.data.importer

import com.tishan.app.domain.question.QuestionAvailability
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QuestionJsonImporterTest {
    private val importer = QuestionJsonImporter()

    @Test
    fun realJsonFilesMatchTheObservedDatasetCounts() {
        val files = realDatasetFiles()

        val reports = files.map { file -> importer.parse(file.readText(), "library-1") }

        assertEquals(1573, reports.sumOf(QuestionImportReport::questionCount))
        assertEquals(0, reports.sumOf { report -> report.issues.count { it.code == "MISSING_EXPLANATION" } })
        assertEquals(1, reports.sumOf(QuestionImportReport::unanswerableCount))
        assertEquals(1, reports.sumOf { it.issues.count { issue -> issue.code == "OPTIONS_EMPTY" } })
        assertEquals(0, reports.sumOf { it.issues.count { issue -> issue.code == "MULTIPLE_CHOICE_SINGLE_ANSWER" } })
        assertEquals(1, reports.sumOf(QuestionImportReport::errorCount))
    }

    @Test
    fun realDatasetParsingStaysWithinImportPerformanceBudget() {
        val files = realDatasetFiles()
        val startedAt = System.nanoTime()

        val count = files.sumOf { file -> importer.parse(file.readText(), "performance-library").questionCount }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(1573, count)
        assertTrue("1,573 题导入解析耗时过长：${elapsedMillis}ms", elapsedMillis < 5_000)
    }

    @Test
    fun exceptionQuestionIsRetainedForReviewButMarkedUnanswerable() {
        val report = importer.parse(
            """
            [{
              "题型": "单选题",
              "题号": 487,
              "题目": "题干中包含选项",
              "选项": {},
              "答案": "A",
              "解析": null
            }]
            """.trimIndent(),
            "library-1",
        )

        assertEquals(1, report.questionCount)
        assertTrue(report.questions.single().question.availability is QuestionAvailability.Unanswerable)
        assertEquals(1, report.errorCount)
        assertEquals("OPTIONS_EMPTY", report.issues.single { it.severity == ImportIssueSeverity.ERROR }.code)
    }

    @Test
    fun duplicateQuestionTypeAndNumberIsRejectedWithinOneFile() {
        val report = importer.parse(
            """
            [
              {"题型":"单选题","题号":1,"题目":"A","选项":{"A":"甲"},"答案":"A","解析":""},
              {"题型":"单选题","题号":1,"题目":"B","选项":{"A":"乙"},"答案":"A","解析":""}
            ]
            """.trimIndent(),
            "library-1",
        )

        assertEquals(1, report.questionCount)
        assertEquals(1, report.issues.count { it.code == "DUPLICATE_BUSINESS_KEY" })
    }

    @Test
    fun dynamicOptionsAndNullExplanationArePreserved() {
        val report = importer.parse(
            """
            [{
              "题型":"多选题",
              "题号":1,
              "题目":"动态选项",
              "选项":{"A":"甲","B":"乙","C":"丙","D":"丁","E":"戊"},
              "答案":"AE",
              "解析":null
            }]
            """.trimIndent(),
            "library-1",
        )
        val item = report.questions.single()

        assertEquals(listOf("A", "B", "C", "D", "E"), item.dto.options!!.map(QuestionOptionDto::key))
        assertEquals(null, item.dto.explanation)
        assertEquals(1, report.warningCount)
    }

    @Test
    fun singleAnswerMultipleChoiceIsPreservedWithWarning() {
        val report = importer.parse(
            """
            [{
              "题型":"多选题",
              "题号":1,
              "题目":"外部题库中的单键多选",
              "选项":{"A":"甲","B":"乙"},
              "答案":"A",
              "解析":"保留源数据"
            }]
            """.trimIndent(),
            "library-1",
        )

        assertEquals(1, report.questionCount)
        assertEquals(1, report.issues.count { it.code == "MULTIPLE_CHOICE_SINGLE_ANSWER" })
    }

    @Test(expected = JsonImportException::class)
    fun rootMustBeAnArray() {
        importer.parse("{}", "library-1")
    }

    private fun realDatasetFiles(): List<File> {
        val names = listOf(
            "技术比武题库.json",
            "2025考试试题.json",
            "2026模拟考试试题.json",
        )
        val files = names.mapNotNull(::repoFile)
        // 题库数据不入版本库：缺少本机 docs/ 样例时跳过，而不是失败。
        assumeTrue("本机 docs/ 题库样例不存在，跳过真实数据集测试", files.size == names.size)
        return files
    }

    private fun repoFile(name: String): File? =
        listOf(
            File("docs", name),
            File("../docs", name),
            File("../../docs", name),
        ).firstOrNull(File::exists)
}
