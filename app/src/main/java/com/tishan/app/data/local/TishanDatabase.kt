package com.tishan.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LibraryEntity::class,
        QuestionEntity::class,
        QuestionOptionEntity::class,
        PracticeSessionEntity::class,
        FavoriteEntity::class,
        PracticeAttemptEntity::class,
        WrongQuestionEntity::class,
        ExamSettingsEntity::class,
        ExamSessionEntity::class,
        ExamResultEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class TishanDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun questionDao(): QuestionDao

    abstract fun practiceDao(): PracticeDao

    abstract fun reviewDao(): ReviewDao

    abstract fun examDao(): ExamDao
}
