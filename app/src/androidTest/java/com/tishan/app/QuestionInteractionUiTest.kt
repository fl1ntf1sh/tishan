package com.tishan.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.tishan.app.core.designsystem.LocalQuestionMode
import com.tishan.app.core.designsystem.theme.TishanTheme
import com.tishan.app.data.local.*
import com.tishan.app.data.repository.*
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.feature.practice.PracticeSessionScreen
import com.tishan.app.feature.practice.exam.ExamSessionScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class QuestionInteractionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: TishanDatabase
    private lateinit var practice: PracticeRepository
    private lateinit var exam: ExamRepository

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TishanDatabase::class.java)
            .allowMainThreadQueries().build()
        practice = PracticeRepository(database)
        exam = ExamRepository(database, practice)
        database.libraryDao().insert(LibraryEntity("ui-exam", "交互测试", "试卷", 1L, 1L))
        database.questionDao().insertWithOptions(
            QuestionEntity("ui-true", "ui-exam", "判断题", 1, "判断题测试", "√", "解析", true, null), emptyList(),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("ui-single", "ui-exam", "单选题", 2, "长题干阅读测试。".repeat(60), "A", "长解析。".repeat(80), true, null),
            listOf(QuestionOptionEntity("ui-single", "A", "选项一", 0), QuestionOptionEntity("ui-single", "B", "选项二", 1)),
        )
    }

    @After fun tearDown() { database.close() }

    @Test fun examCanChangeSelectionInspectUnansweredAndCancelSubmission() {
        var session by mutableStateOf(runBlocking {
            val opened = exam.openSession("ui-exam")!!
            exam.moveToQuestion(opened, opened.questions.indexOfFirst { it.question.internalId == "ui-true" })
        })
        var exited = false
        compose.setContent { TishanTheme { ExamSessionScreen(session, exam, { session = it }, { exited = true }) } }
        compose.onNodeWithContentDescription("√ 正确").performClick()
        compose.waitUntil { session.selectedChoiceKeys == setOf("√") }
        compose.onNodeWithContentDescription("√ 正确").assertIsSelected()
        compose.onNodeWithContentDescription("× 错误").performClick()
        compose.waitUntil { session.selectedChoiceKeys == setOf("×") }
        compose.onNodeWithContentDescription("× 错误").assertIsSelected()
        compose.onNodeWithText("暂停").performClick()
        compose.waitUntil { session.isPaused }
        compose.onNodeWithText("继续").assertIsDisplayed()
        compose.onNodeWithContentDescription("× 错误").assertIsNotEnabled()
        compose.onNodeWithText("答题卡").performClick()
        compose.onNodeWithText("收藏本题").performClick()
        compose.waitUntil { session.currentQuestion.isFavorite }
        compose.onNodeWithText("已收藏本题").assertIsDisplayed()
        compose.onNodeWithText("继续作答").performClick()
        compose.onNodeWithText("继续").performClick()
        compose.waitUntil { !session.isPaused }
        compose.onNodeWithText("答题卡").performClick()
        compose.onNodeWithText("检查并交卷").performClick()
        compose.onNodeWithText("确认交卷？").assertIsDisplayed()
        compose.onNodeWithText("客观题已答 1 / 2，未答 1 题。", substring = true).assertIsDisplayed()
        compose.onNodeWithText("继续作答").performClick()
        compose.waitUntil { session.currentQuestion.question.internalId == "ui-single" }
        assertFalse(session.isSubmitted)
        compose.onNodeWithText("答题卡").performClick()
        compose.onNodeWithText("1 ✓").performClick()
        compose.waitUntil { session.currentQuestion.question.internalId == "ui-true" }
        compose.onNodeWithContentDescription("× 错误").assertIsSelected()
        compose.onNodeWithText("答题卡").performClick()
        compose.onNodeWithText("已收藏本题").assertIsDisplayed()
        compose.onNodeWithText("继续作答").performClick()
        Espresso.pressBack()
        compose.waitUntil { exited }
        assertTrue(runBlocking { exam.openSession("ui-exam")!!.isPaused })
    }

    @Test fun longPracticeKeepsActionsVisibleAndSavesOnSystemBack() {
        var session by mutableStateOf(runBlocking { practice.openSession("ui-exam", QuestionType.SINGLE_CHOICE, PracticeOrderMode.SEQUENTIAL)!! })
        var questionMode = false
        var exited = false
        val setMode: (Boolean) -> Unit = { questionMode = it }
        compose.setContent {
            TishanTheme {
                CompositionLocalProvider(LocalQuestionMode provides setMode) {
                    PracticeSessionScreen(session, practice, { session = it }, { exited = true })
                }
            }
        }
        compose.onNodeWithText("完成").assertIsDisplayed().assertIsNotEnabled()
        assertTrue(questionMode)
        compose.onNodeWithTag("practice-question-list").performScrollToIndex(2)
        compose.onNodeWithContentDescription("A 选项一").performClick()
        compose.waitUntil { session.isSubmitted }
        compose.onNodeWithText("完成").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("收藏").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitUntil { exited }
        assertTrue(runBlocking { practice.openSession("ui-exam", QuestionType.SINGLE_CHOICE, PracticeOrderMode.SEQUENTIAL)!!.isSubmitted })
    }

    @Test fun correctAnswerAutoAdvancesButLastQuestionStays() {
        runBlocking {
            database.questionDao().insertWithOptions(
                QuestionEntity("ui-single-2", "ui-exam", "单选题", 3, "第二题题干", "A", "解析", true, null),
                listOf(QuestionOptionEntity("ui-single-2", "A", "选项一", 0), QuestionOptionEntity("ui-single-2", "B", "选项二", 1)),
            )
        }
        var session by mutableStateOf(runBlocking { practice.openSession("ui-exam", QuestionType.SINGLE_CHOICE, PracticeOrderMode.SEQUENTIAL)!! })
        var exited = false
        compose.setContent { TishanTheme { PracticeSessionScreen(session, practice, { session = it }, { exited = true }) } }
        assertEquals("ui-single", session.currentQuestion.question.internalId)

        compose.onNodeWithTag("practice-question-list").performScrollToIndex(2)
        compose.onNodeWithContentDescription("A 选项一").performClick()
        compose.waitUntil(2_000) { session.currentQuestion.question.internalId == "ui-single-2" }
        assertFalse("答对后应自动进入下一题，且不应退出会话", exited)
        assertFalse(session.isSubmitted)

        compose.onNodeWithTag("practice-question-list").performScrollToIndex(2)
        compose.onNodeWithContentDescription("A 选项一").performClick()
        compose.waitUntil { session.isSubmitted }
        Thread.sleep(1_200)
        compose.waitForIdle()
        assertEquals("最后一题答对后应停留在本题", "ui-single-2", session.currentQuestion.question.internalId)
        assertFalse("最后一题不应自动完成", exited)
        compose.onNodeWithText("完成").assertIsDisplayed().assertIsEnabled()
    }
}
