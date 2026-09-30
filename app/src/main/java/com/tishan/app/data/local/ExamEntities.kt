package com.tishan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exam_settings",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamSettingsEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "single_choice_seconds")
    val singleChoiceSeconds: Int,
    @ColumnInfo(name = "multiple_choice_seconds")
    val multipleChoiceSeconds: Int,
    @ColumnInfo(name = "true_false_seconds")
    val trueFalseSeconds: Int,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "exam_sessions",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["library_id"], unique = true)],
)
data class ExamSessionEntity(
    @androidx.room.PrimaryKey
    val id: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "question_ids_json")
    val questionIdsJson: String,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int,
    @ColumnInfo(name = "selected_keys")
    val selectedKeys: String,
    @ColumnInfo(name = "answers_json")
    val answersJson: String,
    @ColumnInfo(name = "elapsed_by_type_json")
    val elapsedByTypeJson: String,
    @ColumnInfo(name = "active_question_type")
    val activeQuestionType: String?,
    @ColumnInfo(name = "active_since_epoch_millis")
    val activeSinceEpochMillis: Long?,
    @ColumnInfo(name = "is_paused")
    val isPaused: Boolean,
    @ColumnInfo(name = "is_submitted")
    val isSubmitted: Boolean,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "exam_results",
    foreignKeys = [
        ForeignKey(
            entity = ExamSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"], unique = true), Index(value = ["library_id"])],
)
data class ExamResultEntity(
    @androidx.room.PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "total_score")
    val totalScore: Int,
    @ColumnInfo(name = "objective_total")
    val objectiveTotal: Int,
    @ColumnInfo(name = "elapsed_millis")
    val elapsedMillis: Long,
    @ColumnInfo(name = "type_scores_json")
    val typeScoresJson: String,
    @ColumnInfo(name = "wrong_question_ids_json")
    val wrongQuestionIdsJson: String,
    @ColumnInfo(name = "submitted_at_epoch_millis")
    val submittedAtEpochMillis: Long,
)
