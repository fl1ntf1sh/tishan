package com.tishan.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.tishan.app.data.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var database: TishanDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TishanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        settings.restore(null)
        repository = BackupRepository(context, database, settings)
    }

    @After
    fun tearDown() {
        database.close()
        settings.restore(null)
    }

    @Test
    fun categoryExportAndReplacementRestoreAreAtomic() = runBlocking {
        seedAllCategories()
        settings.update { it.copy(themeMode = ThemeMode.DARK, fontScale = 1.15f, singleChoiceSeconds = 120) }

        val allArchive = repository.export(BackupCategory.all)
        val allPreview = repository.preview(allArchive)
        assertEquals(
            listOf("questions", "learning", "exams", "settings"),
            allPreview.manifest.categories,
        )
        assertEquals(1, allPreview.manifest.counts[BackupCategory.QUESTIONS])
        assertEquals(4, allPreview.manifest.counts[BackupCategory.LEARNING])
        assertEquals(3, allPreview.manifest.counts[BackupCategory.EXAMS])
        assertEquals(1, allPreview.manifest.counts[BackupCategory.SETTINGS])

        val questionsOnly = repository.export(setOf(BackupCategory.QUESTIONS))
        val questionsPreview = repository.preview(questionsOnly)
        assertEquals(listOf(BackupCategory.QUESTIONS), questionsPreview.manifest.categories)

        database.libraryDao().insert(
            LibraryEntity("library-2", "后来添加", "题库", 2L, 2L),
        )
        settings.update { it.copy(themeMode = ThemeMode.LIGHT) }
        repository.restore(questionsOnly)
        assertEquals(1, database.libraryDao().findAll().size)
        assertEquals(1, database.questionDao().findByLibraryId("library-1").size)
        assertTrue(database.practiceDao().findAllAttempts().isEmpty())
        assertTrue(database.examDao().findAllResults().isEmpty())
        assertEquals(ThemeMode.SYSTEM, settings.state.value.themeMode)

        repository.restore(allArchive)
        assertEquals(1, database.libraryDao().findAll().size)
        assertEquals(1, database.practiceDao().findAllAttempts().size)
        assertEquals(1, database.practiceDao().findAllFavorites().size)
        assertEquals(1, database.practiceDao().findAllWrong().size)
        assertEquals(1, database.examDao().findAllResults().size)
        assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
        assertEquals(120, settings.state.value.singleChoiceSeconds)
    }

    @Test
    fun corruptArchiveIsRejectedAndOriginalDataRemains() = runBlocking {
        seedAllCategories()
        val archive = repository.export(setOf(BackupCategory.QUESTIONS))
        val beforeLibraries = database.libraryDao().findAll()
        val beforeQuestions = database.questionDao().findByLibraryId("library-1")
        val corrupt = archive.copyOf(archive.size / 2)

        assertThrows(BackupRejectedException::class.java) {
            runBlocking { repository.restore(corrupt) }
        }

        assertEquals(beforeLibraries, database.libraryDao().findAll())
        assertEquals(beforeQuestions, database.questionDao().findByLibraryId("library-1"))
    }

    @Test
    fun settingsPersistAcrossRepositoryInstances() {
        settings.update { it.copy(themeMode = ThemeMode.LIGHT, fontScale = 1.15f, trueFalseSeconds = 600) }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reloaded = SettingsRepository(context)

        assertEquals(ThemeMode.LIGHT, reloaded.state.value.themeMode)
        assertEquals(1.15f, reloaded.state.value.fontScale)
        assertEquals(600, reloaded.state.value.trueFalseSeconds)
        reloaded.restore(null)
    }

    private suspend fun seedAllCategories() {
        val library = LibraryEntity("library-1", "测试题库", "题库", 1L, 1L)
        val question = QuestionEntity(
            id = "question-1",
            libraryId = library.id,
            questionType = "单选题",
            questionNumber = 1,
            prompt = "题干",
            answerRaw = "A",
            explanation = "解析",
            isAnswerable = true,
            unanswerableReason = null,
        )
        database.libraryDao().insert(library)
        database.questionDao().insertWithOptions(
            question,
            listOf(QuestionOptionEntity(question.id, "A", "选项 A", 0)),
        )
        database.practiceDao().saveSession(
            PracticeSessionEntity(
                id = "practice-1",
                libraryId = library.id,
                questionType = "单选题",
                sessionScope = "PRACTICE",
                orderMode = "SEQUENTIAL",
                randomSeed = 0L,
                questionIdsJson = "[\"question-1\"]",
                currentIndex = 0,
                selectedKeys = "A",
                isSubmitted = true,
                lastJudgement = "CORRECT",
                updatedAtEpochMillis = 1L,
            ),
        )
        database.practiceDao().addFavorite(FavoriteEntity(question.id, 1L))
        database.practiceDao().insertAttempt(
            PracticeAttemptEntity("attempt-1", question.id, library.id, "practice", "A", "CORRECT", true, 1L),
        )
        database.practiceDao().saveWrong(WrongQuestionEntity(question.id, library.id, 1, 0, false, 1L))
        database.examDao().saveSettings(ExamSettingsEntity(library.id, 1800, 1800, 900, 1L))
        database.examDao().saveSession(
            ExamSessionEntity(
                id = "exam-session-1",
                libraryId = library.id,
                questionIdsJson = "[\"question-1\"]",
                currentIndex = 0,
                selectedKeys = "A",
                answersJson = "[]",
                elapsedByTypeJson = "{}",
                activeQuestionType = null,
                activeSinceEpochMillis = null,
                isPaused = true,
                isSubmitted = false,
                updatedAtEpochMillis = 1L,
            ),
        )
        database.examDao().saveResult(
            ExamResultEntity("exam-result-1", "exam-session-1", library.id, 1, 1, 1000L, "[]", "[]", 1L),
        )
    }
}
