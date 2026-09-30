package com.tishan.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class LibrarySummary(
    @Embedded
    val library: LibraryEntity,
    @androidx.room.ColumnInfo(name = "question_count")
    val questionCount: Int,
    @androidx.room.ColumnInfo(name = "answerable_count")
    val answerableCount: Int,
)

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(library: LibraryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(libraries: List<LibraryEntity>)

    @Update
    suspend fun update(library: LibraryEntity)

    @Query(
        """
        SELECT libraries.*,
               COUNT(questions.id) AS question_count,
               COALESCE(SUM(CASE WHEN questions.is_answerable = 1 THEN 1 ELSE 0 END), 0)
                   AS answerable_count
        FROM libraries
        LEFT JOIN questions ON questions.library_id = libraries.id
        GROUP BY libraries.id
        ORDER BY libraries.updatedAtEpochMillis DESC, libraries.name COLLATE NOCASE
        """,
    )
    suspend fun findSummaries(): List<LibrarySummary>

    @Query(
        """
        SELECT libraries.*,
               COUNT(questions.id) AS question_count,
               COALESCE(SUM(CASE WHEN questions.is_answerable = 1 THEN 1 ELSE 0 END), 0)
                   AS answerable_count
        FROM libraries
        LEFT JOIN questions ON questions.library_id = libraries.id
        WHERE libraries.libraryType = :libraryType
        GROUP BY libraries.id
        ORDER BY libraries.updatedAtEpochMillis DESC, libraries.name COLLATE NOCASE
        """,
    )
    suspend fun findSummariesByType(libraryType: String): List<LibrarySummary>

    @Query("SELECT * FROM libraries WHERE id = :libraryId")
    suspend fun findById(libraryId: String): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE id IN (:libraryIds)")
    suspend fun findByIds(libraryIds: List<String>): List<LibraryEntity>

    @Query("SELECT * FROM libraries ORDER BY updatedAtEpochMillis DESC, name COLLATE NOCASE")
    suspend fun findAll(): List<LibraryEntity>

    @Delete
    suspend fun delete(library: LibraryEntity)

    @Query("DELETE FROM libraries WHERE id = :libraryId")
    suspend fun deleteById(libraryId: String)

    @Query("DELETE FROM libraries")
    suspend fun deleteAll()
}
