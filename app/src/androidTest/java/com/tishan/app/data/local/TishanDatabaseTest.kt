package com.tishan.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TishanDatabaseTest {
    private lateinit var database: TishanDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TishanDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun businessKeyIncludesQuestionTypeAndOptionsRoundTrip() = runBlocking {
        database.libraryDao().insert(sampleLibrary())
        database.questionDao().insertWithOptions(sampleQuestion("single", "单选题"), sampleOptions("single"))
        database.questionDao().insertWithOptions(sampleQuestion("multiple", "多选题"), emptyList())

        assertEquals(2, database.questionDao().findByLibraryId("library-1").size)
        assertNotNull(database.questionDao().findByBusinessKey("library-1", "单选题", 1))
        assertNotNull(database.questionDao().findByBusinessKey("library-1", "多选题", 1))

        val withOptions = database.questionDao().findWithOptions("single")
        assertNotNull(withOptions)
        assertEquals(listOf("A", "C", "E"), withOptions!!.options.map(QuestionOptionEntity::optionKey))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicateBusinessKeyIsRejected() = runBlocking {
        database.libraryDao().insert(sampleLibrary())
        database.questionDao().insert(sampleQuestion("first", "单选题"))
        database.questionDao().insert(sampleQuestion("second", "单选题"))
    }

    @Test
    fun deletingLibraryCascadesQuestionsAndOptions() = runBlocking {
        val library = sampleLibrary()
        database.libraryDao().insert(library)
        database.questionDao().insertWithOptions(sampleQuestion("single", "单选题"), sampleOptions("single"))

        database.libraryDao().delete(library)

        assertEquals(emptyList<QuestionEntity>(), database.questionDao().findByLibraryId(library.id))
        assertEquals(null, database.questionDao().findWithOptions("single"))
    }

    private fun sampleLibrary() = LibraryEntity(
        id = "library-1",
        name = "测试题库",
        libraryType = "题库",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun sampleQuestion(id: String, type: String) = QuestionEntity(
        id = id,
        libraryId = "library-1",
        questionType = type,
        questionNumber = 1,
        prompt = "题干",
        answerRaw = "A",
        explanation = null,
        isAnswerable = true,
        unanswerableReason = null,
    )

    private fun sampleOptions(questionId: String) = listOf(
        QuestionOptionEntity(questionId, "A", "选项 A", 0),
        QuestionOptionEntity(questionId, "C", "选项 C", 1),
        QuestionOptionEntity(questionId, "E", "选项 E", 2),
    )
}
