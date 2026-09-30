package com.tishan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
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
        Index(value = ["library_id", "question_type", "question_number"], unique = true),
    ],
)
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "library_id")
    val libraryId: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    @ColumnInfo(name = "question_number")
    val questionNumber: Int,
    val prompt: String,
    @ColumnInfo(name = "answer_raw")
    val answerRaw: String,
    val explanation: String?,
    @ColumnInfo(name = "is_answerable")
    val isAnswerable: Boolean,
    @ColumnInfo(name = "unanswerable_reason")
    val unanswerableReason: String?,
)
