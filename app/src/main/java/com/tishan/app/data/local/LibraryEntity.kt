package com.tishan.app.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val libraryType: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "warning_count", defaultValue = "0")
    val warningCount: Int = 0,
)
