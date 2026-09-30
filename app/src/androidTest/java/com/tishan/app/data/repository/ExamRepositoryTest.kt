package com.tishan.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tishan.app.data.local.LibraryEntity
import com.tishan.app.data.local.ExamResultEntity
import com.tishan.app.data.local.ExamSessionEntity
import com.tishan.app.data.local.QuestionEntity
import com.tishan.app.data.local.QuestionOptionEntity
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.practice.ExamDurationSettings
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.domain.question.SubmittedAnswer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamRepositoryTest {
    private lateinit var database: TishanDatabase
    private lateinit var practiceRepository: PracticeRepository
    private lateinit var repository: ExamRepository
    private var now = 1_000L
    private var nextAttemptId = 0
    private var nextExamId = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TishanDatabase::class.java,
        ).allowMainThreadQueries().build()
        practiceRepository = PracticeRepository(
            database = database,
            idGenerator = { "attempt-${++nextAttemptId}" },
            nowMillis = { now },
        )
        repository = ExamRepository(
            database = database,
            practiceRepository = practiceRepository,
            idGenerator = { "exam-${++nextExamId}" },
            nowMillis = { now },
        )
        runBlocking { seedQuestions() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settingsAndPausedSessionRestore() = runBlocking {
        val settings = ExamDurationSettings(singleChoiceSeconds = 10, multipleChoiceSeconds = 20, trueFalseSeconds = 5)
        repository.saveSettings("exam-1", settings)
        var session = repository.openSession("exam-1")!!
        assertEquals(settings, session.settings)

        now += 4_000L
        session = repository.pause(session)
        assertTrue(session.isPaused)
        now += 10_000L
        val restored = repository.openSession("exam-1")!!

        assertTrue(restored.isPaused)
        assertEquals(4_000L, restored.timerState.elapsedByTypeMillis[restored.currentQuestion.question.key.questionType])

        val resumed = repository.resume(restored)
        assertFalse(resumed.isPaused)
        now += 1_000L
        assertTrue(repository.timerSnapshot(resumed).elapsedMillis >= 5_000L)
    }

    @Test
    fun submitExamScoresObjectiveQuestionsAndIsIdempotent() = runBlocking {
        var session = repository.openSession("exam-1")!!
        while (true) {
            now += 1_000L
            session = when (session.currentQuestion.question.key.questionType) {
                QuestionType.SINGLE_CHOICE -> repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
                QuestionType.MULTIPLE_CHOICE -> repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A", "B")))
                QuestionType.TRUE_FALSE -> repository.submitCurrent(session, SubmittedAnswer.TrueFalse(true))
                QuestionType.ESSAY -> session
            }
            if (session.currentIndex == session.questions.lastIndex) break
            session = repository.nextQuestion(session)
        }

        assertEquals(0, database.practiceDao().findAllAttempts().size)

        val (submitted, result) = repository.submitExam(session)
        assertEquals(3, database.practiceDao().findAllAttempts().size)
        val (again, sameResult) = repository.submitExam(submitted)
        assertEquals(3, database.practiceDao().findAllAttempts().size)

        assertTrue(submitted.isSubmitted)
        assertEquals(3, result.totalScore)
        assertEquals(3, result.objectiveTotal)
        assertEquals(emptyList<String>(), result.wrongQuestionIds)
        assertEquals(result, sameResult)
        assertTrue(again.isSubmitted)
    }

    @Test
    fun restartClearsPreviousResultAndCheckpoint() = runBlocking {
        val session = repository.openSession("exam-1")!!
        repository.submitExam(session)

        val restarted = repository.openSession("exam-1", restart = true)!!

        assertFalse(restarted.isSubmitted)
        assertEquals(null, restarted.result)
        assertEquals(0, restarted.currentIndex)
    }

    @Test
    fun previousQuestionRestoresAnsweredQuestionWithoutRecordingAttempt() = runBlocking {
        var session = repository.openSession("exam-1")!!
        while (session.currentQuestion.question.internalId != "q-single") {
            session = repository.nextQuestion(session)
        }
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
        session = repository.nextQuestion(session)

        session = repository.previousQuestion(session)

        assertEquals("q-single", session.currentQuestion.question.internalId)
        assertEquals(setOf("A"), session.selectedChoiceKeys)
        assertTrue(session.currentAnswer?.isCorrect == true)
        assertEquals(0, database.practiceDao().findAllAttempts().size)
    }

    @Test
    fun recentResultsAreSortedAndLimitedByTheDatabaseQuery() = runBlocking {
        repeat(4) { index ->
            val libraryId = "history-library-$index"
            val sessionId = "history-session-$index"
            database.libraryDao().insert(
                LibraryEntity(libraryId, "历史试卷 $index", "试卷", index.toLong(), index.toLong()),
            )
            database.examDao().saveSession(
                ExamSessionEntity(
                    id = sessionId,
                    libraryId = libraryId,
                    questionIdsJson = "[\"q-single\"]",
                    currentIndex = 0,
                    selectedKeys = "",
                    answersJson = "[]",
                    elapsedByTypeJson = "{}",
                    activeQuestionType = null,
                    activeSinceEpochMillis = null,
                    isPaused = true,
                    isSubmitted = true,
                    updatedAtEpochMillis = index.toLong(),
                ),
            )
            database.examDao().saveResult(
                ExamResultEntity(
                    id = "history-result-$index",
                    sessionId = sessionId,
                    libraryId = libraryId,
                    totalScore = index,
                    objectiveTotal = 3,
                    elapsedMillis = 1_000L,
                    typeScoresJson = "[]",
                    wrongQuestionIdsJson = "[]",
                    submittedAtEpochMillis = (index + 1) * 1_000L,
                ),
            )
        }

        val recent = repository.listRecentResults(limit = 2)

        assertEquals(listOf(4_000L, 3_000L), recent.map(ExamResultSummary::submittedAtEpochMillis))
        assertEquals(listOf("历史试卷 3", "历史试卷 2"), recent.map(ExamResultSummary::libraryName))
        assertTrue(repository.listRecentResults(limit = 0).isEmpty())
    }

    @Test
    fun editsRestoreAcrossNavigationAndOnlyFinalAnswerIsScored() = runBlocking {
        var session = repository.openSession("exam-1")!!
        val trueIndex = session.questions.indexOfFirst { it.question.internalId == "q-true" }
        val singleIndex = session.questions.indexOfFirst { it.question.internalId == "q-single" }
        val multiIndex = session.questions.indexOfFirst { it.question.internalId == "q-multiple" }
        session = repository.moveToQuestion(session, trueIndex)
        session = repository.submitCurrent(session, SubmittedAnswer.TrueFalse(false))
        session = repository.submitCurrent(session, SubmittedAnswer.TrueFalse(true))
        session = repository.moveToQuestion(session, singleIndex)
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("B")))
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
        session = repository.moveToQuestion(session, multiIndex)
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
        session = repository.clearCurrentAnswer(session)
        assertEquals(null, session.currentAnswer)
        assertTrue(session.selectedChoiceKeys.isEmpty())
        session = repository.openSession("exam-1")!!
        assertEquals(null, session.currentAnswer)
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A", "B")))
        session = repository.moveToQuestion(session, trueIndex)
        assertEquals(setOf("√"), session.selectedChoiceKeys)
        session = repository.openSession("exam-1")!!
        assertEquals(setOf("√"), session.selectedChoiceKeys)
        assertEquals(0, database.practiceDao().findAllAttempts().size)
        val result = repository.submitExam(session).second
        assertEquals(3, result.totalScore)
        assertEquals(3, database.practiceDao().findAllAttempts().size)
    }

    @Test
    fun favoritesPersistWhilePausedWithoutChangingTimerOrAnswers() = runBlocking {
        val opened = repository.openSession("exam-1")!!
        val paused = repository.pause(opened)
        val favorited = repository.toggleFavorite(paused)
        assertTrue(favorited.currentQuestion.isFavorite)
        assertEquals(paused.timerState, favorited.timerState)
        assertEquals(paused.answers, favorited.answers)
        val restored = repository.openSession("exam-1")!!
        assertTrue(restored.currentQuestion.isFavorite)
        assertTrue(restored.isPaused)
        assertFalse(repository.toggleFavorite(restored).currentQuestion.isFavorite)
    }

    @Test
    fun pausedAndSubmittedSessionsRejectAnswerChanges() = runBlocking {
        val session = repository.openSession("exam-1")!!
        val paused = repository.pause(session)
        assertTrue(runCatching { repository.submitCurrent(paused, SubmittedAnswer.TrueFalse(true)) }.isFailure)
        assertTrue(runCatching { repository.clearCurrentAnswer(paused) }.isFailure)
        val submitted = repository.submitExam(repository.resume(paused)).first
        assertTrue(runCatching { repository.submitCurrent(submitted, SubmittedAnswer.TrueFalse(true)) }.isFailure)
        assertTrue(runCatching { repository.clearCurrentAnswer(submitted) }.isFailure)
    }

    private suspend fun seedQuestions() {
        database.libraryDao().insert(LibraryEntity("exam-1", "模拟试卷", "试卷", 1L, 1L))
        database.questionDao().insertWithOptions(
            QuestionEntity("q-single", "exam-1", "单选题", 1, "单选", "A", "解析", true, null),
            listOf(
                QuestionOptionEntity("q-single", "A", "正确", 0),
                QuestionOptionEntity("q-single", "B", "错误", 1),
            ),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-multiple", "exam-1", "多选题", 1, "多选", "AB", null, true, null),
            listOf(
                QuestionOptionEntity("q-multiple", "A", "一", 0),
                QuestionOptionEntity("q-multiple", "B", "二", 1),
                QuestionOptionEntity("q-multiple", "C", "三", 2),
            ),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-true", "exam-1", "判断题", 1, "判断", "√", null, true, null),
            emptyList(),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-essay", "exam-1", "申论题", 1, "申论", "参考答案", null, true, null),
            emptyList(),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-broken", "exam-1", "单选题", 2, "异常", "A", null, false, "缺少选项"),
            emptyList(),
        )
    }
}
