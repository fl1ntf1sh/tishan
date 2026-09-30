package com.tishan.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P08PerformanceTest {
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
    fun commonLibraryAndPracticeListsHandle1573QuestionsWithinBudget() = runBlocking {
        val library = LibraryEntity("performance-library", "性能题库", "题库", 1L, 1L)
        val questions = (0 until 1573).map { index ->
            val type = when (index % 3) {
                0 -> "单选题"
                1 -> "多选题"
                else -> "判断题"
            }
            QuestionEntity(
                id = "performance-question-$index",
                libraryId = library.id,
                questionType = type,
                questionNumber = index / 3 + 1,
                prompt = "性能题目 $index",
                answerRaw = if (type == "判断题") "正确" else "A",
                explanation = null,
                isAnswerable = true,
                unanswerableReason = null,
            )
        }
        database.libraryDao().insert(library)
        database.questionDao().insertAll(questions)

        val startedAt = System.nanoTime()
        val summaries = database.libraryDao().findSummaries()
        val practiceIds = database.questionDao().findPracticeQuestionIds(library.id, "单选题", "题目")
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(1, summaries.size)
        assertEquals(1573, summaries.single().questionCount)
        assertTrue(practiceIds.isNotEmpty())
        assertTrue("常用题库/刷题列表查询耗时过长：${elapsedMillis}ms", elapsedMillis < 2_000)
    }
}
