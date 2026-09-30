package com.tishan.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tishan.app.data.local.LibraryEntity
import com.tishan.app.data.local.QuestionEntity
import com.tishan.app.data.local.QuestionOptionEntity
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.question.AnswerJudgement
import com.tishan.app.domain.question.SubmittedAnswer
import com.tishan.app.domain.question.QuestionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeRepositoryTest {
    private lateinit var database: TishanDatabase
    private lateinit var repository: PracticeRepository
    private var nextId = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TishanDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = PracticeRepository(
            database = database,
            idGenerator = { "generated-${++nextId}" },
            nowMillis = { System.currentTimeMillis() + nextId },
        )
        runBlocking { seedQuestions() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun wrongAnswerEntersBookAndThreeCorrectAnswersRemoveIt() = runBlocking {
        var session = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.SEQUENTIAL,
        )!!

        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("B")))
        assertEquals(1, repository.stats().wrongQuestionCount)

        repeat(3) {
            session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
        }

        assertEquals(0, repository.stats().wrongQuestionCount)
        assertEquals(4, repository.stats().todayQuestionCount)
    }

    @Test
    fun favoriteSearchAndCheckpointRestoreWork() = runBlocking {
        var session = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.RANDOM,
        )!!
        val originalOrder = session.questions.map { it.question.internalId }

        session = repository.toggleFavorite(session)
        assertEquals(1, repository.stats().favoriteCount)
        session = repository.toggleFavorite(session)
        assertEquals(0, repository.stats().favoriteCount)

        val selected = session.copy(selectedChoiceKeys = setOf("A"))
        repository.saveDraft(selected)
        val restored = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.RANDOM,
        )!!
        assertEquals(originalOrder, restored.questions.map { it.question.internalId })
        assertEquals(setOf("A"), restored.selectedChoiceKeys)
        assertFalse(restored.isSubmitted)

        val search = repository.searchQuestions("library-1", QuestionType.SINGLE_CHOICE, "第二")
        assertEquals(1, search.size)
        assertTrue(search.single().question.prompt.contains("第二"))
        assertNotNull(session.currentQuestion.question.internalId)
    }

    @Test
    fun checkpointKeepsStoredOrderAndSkipsMissingQuestions() = runBlocking {
        repository.openSession("library-1", QuestionType.SINGLE_CHOICE, PracticeOrderMode.SEQUENTIAL)
        val stored = database.practiceDao().findSession("library-1", "单选题", PracticeRepository.PRACTICE_SCOPE)!!
        database.practiceDao().saveSession(
            stored.copy(questionIdsJson = """["q-2","missing","q-1"]""", currentIndex = 2),
        )

        val restored = repository.openSession("library-1", QuestionType.SINGLE_CHOICE, PracticeOrderMode.SEQUENTIAL)!!
        assertEquals(listOf("q-2", "q-1"), restored.questions.map { it.question.internalId })
        assertEquals(1, restored.currentIndex)
    }

    @Test
    fun previousAndNextRestoreSubmittedAnswersFromTheCurrentSession() = runBlocking {
        var session = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.SEQUENTIAL,
        )!!

        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("B")))
        session = repository.nextQuestion(session)
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))

        session = repository.previousQuestion(session)
        assertEquals(0, session.currentIndex)
        assertEquals(setOf("B"), session.selectedChoiceKeys)
        assertTrue(session.isSubmitted)
        assertEquals(AnswerJudgement.Incorrect, session.judgement)

        session = repository.nextQuestion(session)
        assertEquals(1, session.currentIndex)
        assertEquals(setOf("A"), session.selectedChoiceKeys)
        assertTrue(session.isSubmitted)
        assertEquals(AnswerJudgement.Correct, session.judgement)
    }

    @Test
    fun restartCreatesANewAttemptScope() = runBlocking {
        var session = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.SEQUENTIAL,
        )!!
        session = repository.submitCurrent(session, SubmittedAnswer.Choice(setOf("A")))
        val previousSessionId = session.sessionId

        val restarted = repository.openSession(
            libraryId = "library-1",
            questionType = QuestionType.SINGLE_CHOICE,
            orderMode = PracticeOrderMode.SEQUENTIAL,
            restart = true,
        )!!

        assertTrue(restarted.sessionId != previousSessionId)
        assertFalse(restarted.isSubmitted)
        assertTrue(restarted.selectedChoiceKeys.isEmpty())
    }

    private suspend fun seedQuestions() {
        database.libraryDao().insert(
            LibraryEntity("library-1", "测试库", "题库", 1L, 1L),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-1", "library-1", "单选题", 1, "第一题", "A", "解析", true, null),
            listOf(
                QuestionOptionEntity("q-1", "A", "正确", 0),
                QuestionOptionEntity("q-1", "B", "错误", 1),
            ),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-2", "library-1", "单选题", 2, "第二题", "A", null, true, null),
            listOf(
                QuestionOptionEntity("q-2", "A", "正确", 0),
                QuestionOptionEntity("q-2", "B", "错误", 1),
            ),
        )
    }
}
