package com.tishan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

data class QuestionWithOptions(
    @Embedded
    val question: QuestionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "question_id",
        entity = QuestionOptionEntity::class,
    )
    val options: List<QuestionOptionEntity>,
)

data class QuestionTypeCount(
    @androidx.room.ColumnInfo(name = "question_type")
    val questionType: String,
    val questionCount: Int,
)

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Update
    suspend fun update(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOptions(options: List<QuestionOptionEntity>)

    @Query("DELETE FROM question_options WHERE question_id = :questionId")
    suspend fun deleteOptions(questionId: String)

    @Transaction
    suspend fun insertWithOptions(
        question: QuestionEntity,
        options: List<QuestionOptionEntity>,
    ) {
        insert(question)
        insertOptions(options)
    }

    @Transaction
    suspend fun upsertWithOptions(
        question: QuestionEntity,
        options: List<QuestionOptionEntity>,
    ): String {
        val existing = findByBusinessKey(
            libraryId = question.libraryId,
            questionType = question.questionType,
            questionNumber = question.questionNumber,
        )
        val stableQuestion = existing?.let { current ->
            question.copy(id = current.id)
        } ?: question
        if (existing == null) {
            insert(stableQuestion)
        } else {
            update(stableQuestion)
            deleteOptions(stableQuestion.id)
        }
        if (options.isNotEmpty()) insertOptions(options)
        return stableQuestion.id
    }

    @Query(
        """
        SELECT * FROM questions
        WHERE library_id = :libraryId
        ORDER BY question_type, question_number
        """,
    )
    suspend fun findByLibraryId(libraryId: String): List<QuestionEntity>

    @Transaction
    @Query("SELECT * FROM questions WHERE library_id = :libraryId")
    suspend fun findAllWithOptionsByLibraryId(libraryId: String): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM questions ORDER BY library_id, question_type, question_number")
    suspend fun findAllWithOptions(): List<QuestionWithOptions>

    @Query(
        """
        SELECT question_type, COUNT(*) AS questionCount
        FROM questions
        WHERE library_id = :libraryId
          AND is_answerable = 1
          AND question_type IN ('单选题', '多选题', '判断题')
        GROUP BY question_type
        ORDER BY question_type
        """,
    )
    suspend fun findPracticeTypeCounts(libraryId: String): List<QuestionTypeCount>

    @Query(
        """
        SELECT id FROM questions
        WHERE library_id = :libraryId
          AND question_type = :questionType
          AND is_answerable = 1
          AND prompt LIKE '%' || :searchText || '%'
        ORDER BY question_number
        """,
    )
    suspend fun findPracticeQuestionIds(
        libraryId: String,
        questionType: String,
        searchText: String,
    ): List<String>

    @Query(
        """
        SELECT id FROM questions
        WHERE library_id = :libraryId
        ORDER BY question_type, question_number
        """,
    )
    suspend fun findExamQuestionIds(libraryId: String): List<String>

    @Transaction
    @Query("SELECT * FROM questions WHERE id IN (:questionIds)")
    suspend fun findAllWithOptionsByIds(questionIds: List<String>): List<QuestionWithOptions>

    @Query(
        """
        SELECT * FROM questions
        WHERE library_id = :libraryId
          AND question_type = :questionType
          AND question_number = :questionNumber
        """,
    )
    suspend fun findByBusinessKey(
        libraryId: String,
        questionType: String,
        questionNumber: Int,
    ): QuestionEntity?

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :questionId")
    suspend fun findWithOptions(questionId: String): QuestionWithOptions?

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteById(questionId: String)

    @Query("DELETE FROM question_options")
    suspend fun deleteAllOptions()

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}
