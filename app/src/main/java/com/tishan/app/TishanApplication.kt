package com.tishan.app

import android.app.Application
import androidx.room.Room
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.data.local.TishanDatabaseMigrations
import com.tishan.app.data.repository.LibraryRepository
import com.tishan.app.data.repository.PracticeRepository
import com.tishan.app.data.repository.ReviewRepository
import com.tishan.app.data.repository.ExamRepository
import com.tishan.app.data.backup.BackupRepository
import com.tishan.app.data.settings.SettingsRepository

class TishanApplication : Application() {
    val database: TishanDatabase by lazy {
        Room.databaseBuilder(this, TishanDatabase::class.java, "tishan.db")
            .addMigrations(
                TishanDatabaseMigrations.MIGRATION_1_2,
                TishanDatabaseMigrations.MIGRATION_2_3,
                TishanDatabaseMigrations.MIGRATION_3_4,
                TishanDatabaseMigrations.MIGRATION_4_5,
            )
            .build()
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database)
    }

    val practiceRepository: PracticeRepository by lazy {
        PracticeRepository(database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepository(database, practiceRepository)
    }

    val examRepository: ExamRepository by lazy {
        ExamRepository(database, practiceRepository, defaultDuration = settingsRepository::defaultExamDuration)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(this, database, settingsRepository)
    }
}
