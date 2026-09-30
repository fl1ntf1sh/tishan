package com.tishan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class PracticeStatsSnapshot(
    @androidx.room.ColumnInfo(name = "today_question_count")
    val todayQuestionCount: Int,
    @androidx.room.ColumnInfo(name = "wrong_question_count")
    val wrongQuestionCount: Int,
    @androidx.room.ColumnInfo(name = "favorite_count")
    val favoriteCount: Int,
)

@Dao
interface PracticeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: PracticeSessionEntity)

    @Query(
        "SELECT * FROM practice_sessions WHERE library_id = :libraryId AND question_type = :questionType AND session_scope = :sessionScope",
    )
    suspend fun findSession(
        libraryId: String,
        questionType: String,
        sessionScope: String,
    ): PracticeSessionEntity?

    @Query("DELETE FROM practice_sessions WHERE library_id = :libraryId AND question_type = :questionType AND session_scope = :sessionScope")
    suspend fun deleteSession(libraryId: String, questionType: String, sessionScope: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE question_id = :questionId")
    suspend fun removeFavorite(questionId: String)

    @Query("SELECT question_id FROM favorites WHERE question_id IN (:questionIds)")
    suspend fun findFavoriteIds(questionIds: List<String>): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE question_id = :questionId)")
    suspend fun isFavorite(questionId: String): Boolean

    @Insert
    suspend fun insertAttempt(attempt: PracticeAttemptEntity)

    @Query(
        """
        SELECT * FROM practice_attempts
        WHERE question_id = :questionId AND source = :source
        ORDER BY occurred_at_epoch_millis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestAttempt(questionId: String, source: String): PracticeAttemptEntity?

    @Query("SELECT * FROM wrong_questions WHERE question_id = :questionId")
    suspend fun findWrong(questionId: String): WrongQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWrong(wrong: WrongQuestionEntity)

    @Query("DELETE FROM wrong_questions WHERE question_id = :questionId")
    suspend fun removeWrong(questionId: String)

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM practice_attempts WHERE occurred_at_epoch_millis >= :startMillis)
                AS today_question_count,
            (SELECT COUNT(*) FROM wrong_questions) AS wrong_question_count,
            (SELECT COUNT(*) FROM favorites) AS favorite_count
        """,
    )
    suspend fun loadStats(startMillis: Long): PracticeStatsSnapshot

    @Query("SELECT * FROM practice_sessions")
    suspend fun findAllSessions(): List<PracticeSessionEntity>

    @Query("SELECT * FROM favorites")
    suspend fun findAllFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM practice_attempts")
    suspend fun findAllAttempts(): List<PracticeAttemptEntity>

    @Query("SELECT * FROM wrong_questions")
    suspend fun findAllWrong(): List<WrongQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessions(sessions: List<PracticeSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempts(attempts: List<PracticeAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWrong(wrong: List<WrongQuestionEntity>)

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM favorites")
    suspend fun deleteAllFavorites()

    @Query("DELETE FROM practice_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM wrong_questions")
    suspend fun deleteAllWrong()
}
