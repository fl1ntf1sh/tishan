package com.tishan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

data class ReviewGroupRow(
    @ColumnInfo(name = "library_id") val libraryId: String,
    @ColumnInfo(name = "library_name") val libraryName: String,
    @ColumnInfo(name = "library_type") val libraryType: String,
    @ColumnInfo(name = "question_type") val questionType: String,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
)

@Dao
interface ReviewDao {
    @Query(
        """
        SELECT libraries.id AS library_id, libraries.name AS library_name,
               libraries.libraryType AS library_type, questions.question_type AS question_type,
               COUNT(*) AS question_count, CAST(NULL AS TEXT) AS source_label
        FROM favorites
        JOIN questions ON questions.id = favorites.question_id
        JOIN libraries ON libraries.id = questions.library_id
        GROUP BY libraries.id, libraries.name, libraries.libraryType, questions.question_type
        ORDER BY libraries.name COLLATE NOCASE, questions.question_type
        """,
    )
    suspend fun favoriteGroups(): List<ReviewGroupRow>

    @Query(
        """
        SELECT libraries.id AS library_id, libraries.name AS library_name,
               libraries.libraryType AS library_type, questions.question_type AS question_type,
               COUNT(DISTINCT wrong_questions.question_id) AS question_count,
               GROUP_CONCAT(DISTINCT CASE
                   WHEN practice_attempts.source = 'practice' OR practice_attempts.source LIKE 'practice-session:%' THEN '普通刷题'
                   WHEN practice_attempts.source LIKE 'exam%' THEN '模考'
                   ELSE '其他练习'
               END) AS source_label
        FROM wrong_questions
        JOIN questions ON questions.id = wrong_questions.question_id
        JOIN libraries ON libraries.id = questions.library_id
        LEFT JOIN practice_attempts ON practice_attempts.question_id = wrong_questions.question_id
        GROUP BY libraries.id, libraries.name, libraries.libraryType, questions.question_type
        ORDER BY libraries.name COLLATE NOCASE, questions.question_type
        """,
    )
    suspend fun wrongGroups(): List<ReviewGroupRow>

    @Query(
        """
        SELECT libraries.id AS library_id, libraries.name AS library_name,
               libraries.libraryType AS library_type, questions.question_type AS question_type,
               COUNT(*) AS question_count, CAST(NULL AS TEXT) AS source_label
        FROM questions
        JOIN libraries ON libraries.id = questions.library_id
        GROUP BY libraries.id, libraries.name, libraries.libraryType, questions.question_type
        ORDER BY libraries.name COLLATE NOCASE, questions.question_type
        """,
    )
    suspend fun backGroups(): List<ReviewGroupRow>

    @Query(
        """
        SELECT favorites.question_id FROM favorites
        JOIN questions ON questions.id = favorites.question_id
        WHERE questions.library_id = :libraryId AND questions.question_type = :questionType
        ORDER BY questions.question_number
        """,
    )
    suspend fun favoriteQuestionIds(libraryId: String, questionType: String): List<String>

    @Query(
        """
        SELECT wrong_questions.question_id FROM wrong_questions
        JOIN questions ON questions.id = wrong_questions.question_id
        WHERE questions.library_id = :libraryId AND questions.question_type = :questionType
        ORDER BY questions.question_number
        """,
    )
    suspend fun wrongQuestionIds(libraryId: String, questionType: String): List<String>

    @Query(
        """
        SELECT id FROM questions
        WHERE library_id = :libraryId AND question_type = :questionType
        ORDER BY question_number
        """,
    )
    suspend fun allQuestionIds(libraryId: String, questionType: String): List<String>
}
