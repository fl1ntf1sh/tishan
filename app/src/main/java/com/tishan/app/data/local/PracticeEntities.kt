package com.tishan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["library_id"]),
        Index(value = ["library_id", "question_type", "session_scope"], unique = true),
    ],
)
data class PracticeSessionEntity(
    @androidx.room.PrimaryKey
    val id: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    @ColumnInfo(name = "session_scope")
    val sessionScope: String,
    @ColumnInfo(name = "order_mode")
    val orderMode: String,
    @ColumnInfo(name = "random_seed")
    val randomSeed: Long,
    @ColumnInfo(name = "question_ids_json")
    val questionIdsJson: String,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int,
    @ColumnInfo(name = "selected_keys")
    val selectedKeys: String,
    @ColumnInfo(name = "is_submitted")
    val isSubmitted: Boolean,
    @ColumnInfo(name = "last_judgement")
    val lastJudgement: String?,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["question_id"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["question_id"])],
)
data class FavoriteEntity(
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,
)

@Entity(
    tableName = "practice_attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["question_id"]), Index(value = ["library_id"])],
)
data class PracticeAttemptEntity(
    @androidx.room.PrimaryKey
    val id: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    val source: String,
    @ColumnInfo(name = "submitted_answer")
    val submittedAnswer: String,
    val judgement: String,
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
)

@Entity(
    tableName = "wrong_questions",
    primaryKeys = ["question_id"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["library_id"])],
)
data class WrongQuestionEntity(
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "total_attempts")
    val totalAttempts: Int,
    @ColumnInfo(name = "consecutive_correct")
    val consecutiveCorrect: Int,
    @ColumnInfo(name = "last_is_correct")
    val lastIsCorrect: Boolean,
    @ColumnInfo(name = "last_attempt_at_epoch_millis")
    val lastAttemptAtEpochMillis: Long,
)
