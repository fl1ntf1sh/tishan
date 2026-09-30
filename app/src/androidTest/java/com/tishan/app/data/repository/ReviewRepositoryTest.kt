package com.tishan.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tishan.app.data.local.FavoriteEntity
import com.tishan.app.data.local.LibraryEntity
import com.tishan.app.data.local.QuestionEntity
import com.tishan.app.data.local.QuestionOptionEntity
import com.tishan.app.data.local.PracticeAttemptEntity
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.data.local.WrongQuestionEntity
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.question.QuestionAvailability
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {
    private lateinit var database: TishanDatabase
    private lateinit var repository: ReviewRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TishanDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReviewRepository(
            database = database,
            practiceRepository = PracticeRepository(database, idGenerator = { "session" }, nowMillis = { 100L }),
            nowMillis = { 100L },
        )
        runBlocking { seedQuestions() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun groupsAndBackModeIncludeAllImportedQuestions() = runBlocking {
        assertEquals(1, repository.favoriteGroups().single().questionCount)
        assertEquals(1, repository.wrongGroups().single().questionCount)
        assertEquals("普通刷题", repository.wrongGroups().single().sourceLabel)
        val backGroups = repository.backGroups()
        assertEquals(2, backGroups.size)
        assertEquals(3, backGroups.sumOf { it.questionCount })

        val objectiveQuestions = repository.browseAll(
            backGroups.single { it.questionType.externalValue == "单选题" },
            PracticeOrderMode.SEQUENTIAL,
        )
        assertEquals(listOf("q-1", "q-2"), objectiveQuestions.map { it.question.internalId })
        assertTrue(objectiveQuestions.any { it.question.availability is QuestionAvailability.Unanswerable })
        val essayQuestions = repository.browseAll(
            backGroups.single { it.questionType.externalValue == "申论题" },
            PracticeOrderMode.SEQUENTIAL,
        )
        assertEquals(listOf("q-3"), essayQuestions.map { it.question.internalId })
    }

    @Test
    fun favoriteRedoUsesSeparateSessionScope() = runBlocking {
        val group = repository.favoriteGroups().single()
        val session = repository.openFavoriteSession(group, PracticeOrderMode.SEQUENTIAL)
        assertEquals(listOf("q-1"), session!!.questions.map { it.question.internalId })
        assertEquals("FAVORITES", session.sessionScope)
        assertEquals(
            "FAVORITES",
            database.practiceDao().findSession("library-1", "单选题", "FAVORITES")!!.sessionScope,
        )
    }

    @Test
    fun sessionSourcesArePresentedAsReadableLabels() = runBlocking {
        database.practiceDao().insertAttempt(
            PracticeAttemptEntity("attempt-session", "q-1", "library-1", "practice-session:internal-id", "B", "INCORRECT", false, 2L),
        )
        assertEquals("普通刷题", repository.wrongGroups().single().sourceLabel)
    }

    private suspend fun seedQuestions() {
        database.libraryDao().insert(LibraryEntity("library-1", "复习库", "题库", 1L, 1L))
        database.questionDao().insertWithOptions(
            QuestionEntity("q-1", "library-1", "单选题", 1, "可作答", "A", "解析", true, null),
            listOf(QuestionOptionEntity("q-1", "A", "正确", 0)),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-2", "library-1", "单选题", 2, "不可作答", "A", null, false, "缺少选项"),
            emptyList(),
        )
        database.questionDao().insertWithOptions(
            QuestionEntity("q-3", "library-1", "申论题", 1, "申论", "参考答案", null, true, null),
            emptyList(),
        )
        database.practiceDao().addFavorite(FavoriteEntity("q-1", 1L))
        database.practiceDao().insertAttempt(
            PracticeAttemptEntity("attempt-1", "q-1", "library-1", "practice", "B", "INCORRECT", false, 1L),
        )
        database.practiceDao().saveWrong(WrongQuestionEntity("q-1", "library-1", 1, 0, false, 1L))
    }
}
