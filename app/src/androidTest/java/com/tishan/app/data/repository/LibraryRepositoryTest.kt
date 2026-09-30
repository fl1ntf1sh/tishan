package com.tishan.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.library.LibraryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {
    private lateinit var database: TishanDatabase
    private lateinit var repository: LibraryRepository
    private var nextId = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TishanDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LibraryRepository(
            database = database,
            idGenerator = { "id-${++nextId}" },
            nowMillis = { 100L + nextId },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updateKeepsQuestionIdAndReplaceRemovesMissingQuestions() = runBlocking {
        val created = repository.commit(
            repository.previewNew(LibraryType.QUESTION_BANK, "测试库", json("A", 1, "原题")),
            "测试库",
        )
        val original = database.questionDao().findByBusinessKey(created.libraryId, "单选题", 1)
            ?: error("missing original question")

        val update = repository.previewExisting(
            created.libraryId,
            "[${jsonObject("A", 1, "更新题")},${jsonObject("A", 2, "新增题")} ]",
            ImportMode.UPDATE,
        )
        val updateResult = repository.commit(update, "更新后的库")
        assertEquals(1, updateResult.updatedCount)
        assertEquals(1, updateResult.addedCount)
        assertEquals(0, updateResult.retainedCount)
        assertEquals(original.id, database.questionDao()
            .findByBusinessKey(created.libraryId, "单选题", 1)!!.id)

        val replace = repository.previewExisting(
            created.libraryId,
            json("A", 2, "只保留第二题"),
            ImportMode.REPLACE,
        )
        val replaceResult = repository.commit(replace, "覆盖后的库")
        assertEquals(1, replaceResult.removedCount)
        assertEquals(null, database.questionDao().findByBusinessKey(created.libraryId, "单选题", 1))
        assertNotNull(database.questionDao().findByBusinessKey(created.libraryId, "单选题", 2))
        assertEquals("覆盖后的库", repository.findLibrary(created.libraryId)!!.name)
    }

    @Test
    fun unanswerableQuestionIsRetainedAndExportRoundTrips() = runBlocking {
        val preview = repository.previewNew(
            LibraryType.EXAM_PAPER,
            "异常试卷",
            """
            [
              {"题型":"单选题","题号":487,"题目":"题干含选项","选项":{},"答案":"A","解析":null},
              {"题型":"判断题","题号":1,"题目":"判断","选项":null,"答案":"√","解析":"说明"}
            ]
            """.trimIndent(),
        )
        val result = repository.commit(preview, "异常试卷")

        assertEquals(2, result.report.questionCount)
        assertEquals(1, result.report.unanswerableCount)
        assertEquals(2, repository.listLibraries().single().library.warningCount)
        val exported = repository.exportJson(result.libraryId)
        val roundTrip = com.tishan.app.data.importer.QuestionJsonImporter().parse(exported, "round-trip")
        assertEquals(2, roundTrip.questionCount)
        assertEquals(1, roundTrip.unanswerableCount)
    }

    @Test
    fun renameAndDeleteCascadeThroughRepository() = runBlocking {
        val created = repository.commit(
            repository.previewNew(LibraryType.QUESTION_BANK, "原名称", json("A", 1, "题目")),
            "原名称",
        )
        repository.renameLibrary(created.libraryId, "新名称")
        assertEquals("新名称", repository.findLibrary(created.libraryId)!!.name)

        repository.deleteLibrary(created.libraryId)

        assertEquals(null, repository.findLibrary(created.libraryId))
        assertEquals(emptyList<com.tishan.app.data.local.QuestionEntity>(), database.questionDao().findByLibraryId(created.libraryId))
    }

    private fun json(answer: String, number: Int, prompt: String) =
        "[${jsonObject(answer, number, prompt)}]"

    private fun jsonObject(answer: String, number: Int, prompt: String) =
        "{\"题型\":\"单选题\",\"题号\":$number,\"题目\":\"$prompt\",\"选项\":{\"A\":\"选项\"},\"答案\":\"$answer\",\"解析\":null}"
}
