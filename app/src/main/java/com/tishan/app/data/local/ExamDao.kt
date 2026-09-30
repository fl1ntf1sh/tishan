package com.tishan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ExamSettingsEntity)

    @Query("SELECT * FROM exam_settings WHERE library_id = :libraryId")
    suspend fun findSettings(libraryId: String): ExamSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: ExamSessionEntity)

    @Query("SELECT * FROM exam_sessions WHERE library_id = :libraryId")
    suspend fun findSession(libraryId: String): ExamSessionEntity?

    @Query("DELETE FROM exam_sessions WHERE library_id = :libraryId")
    suspend fun deleteSession(libraryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveResult(result: ExamResultEntity)

    @Query("SELECT * FROM exam_results WHERE session_id = :sessionId")
    suspend fun findResult(sessionId: String): ExamResultEntity?

    @Query("SELECT * FROM exam_settings")
    suspend fun findAllSettings(): List<ExamSettingsEntity>

    @Query("SELECT * FROM exam_sessions")
    suspend fun findAllSessions(): List<ExamSessionEntity>

    @Query("SELECT * FROM exam_results")
    suspend fun findAllResults(): List<ExamResultEntity>

    @Query("SELECT * FROM exam_results ORDER BY submitted_at_epoch_millis DESC LIMIT :limit")
    suspend fun findRecentResults(limit: Int): List<ExamResultEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettings(settings: List<ExamSettingsEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessions(sessions: List<ExamSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResults(results: List<ExamResultEntity>)

    @Query("DELETE FROM exam_results")
    suspend fun deleteAllResults()

    @Query("DELETE FROM exam_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM exam_settings")
    suspend fun deleteAllSettings()
}
