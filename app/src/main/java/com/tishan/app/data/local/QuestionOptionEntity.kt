package com.tishan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "question_options",
    primaryKeys = ["question_id", "option_key"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["question_id"]), Index(value = ["question_id", "position"], unique = true)],
)
data class QuestionOptionEntity(
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "option_key")
    val optionKey: String,
    @ColumnInfo(name = "option_text")
    val text: String,
    val position: Int,
)
