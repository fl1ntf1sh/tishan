package com.tishan.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TishanDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TishanDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2AddsWarningCountColumn() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO libraries(id, name, libraryType, createdAtEpochMillis, updatedAtEpochMillis) VALUES ('library-1', '旧库', '题库', 1, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-test",
            2,
            true,
            TishanDatabaseMigrations.MIGRATION_1_2,
        ).use { database ->
            database.query("SELECT warning_count FROM libraries WHERE id = 'library-1'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3AddsPracticeTables() {
        helper.createDatabase("migration-test-v3", 2).apply {
            execSQL(
                "INSERT INTO libraries(id, name, libraryType, createdAtEpochMillis, updatedAtEpochMillis, warning_count) VALUES ('library-1', '库', '题库', 1, 1, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-test-v3",
            3,
            true,
            TishanDatabaseMigrations.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('practice_sessions', 'favorites', 'practice_attempts', 'wrong_questions')").use { cursor ->
                var count = 0
                while (cursor.moveToNext()) count++
                assertEquals(4, count)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4AddsSessionScopeAndCompositeIndex() {
        helper.createDatabase("migration-test-v4", 3).apply {
            execSQL(
                "INSERT INTO libraries(id, name, libraryType, createdAtEpochMillis, updatedAtEpochMillis, warning_count) VALUES ('library-1', '库', '题库', 1, 1, 0)",
            )
            execSQL(
                "INSERT INTO practice_sessions(id, library_id, question_type, order_mode, random_seed, question_ids_json, current_index, selected_keys, is_submitted, last_judgement, updated_at_epoch_millis) VALUES ('session-1', 'library-1', '单选题', 'SEQUENTIAL', 0, '[]', 0, '', 0, NULL, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-test-v4",
            4,
            true,
            TishanDatabaseMigrations.MIGRATION_3_4,
        ).use { database ->
            database.query("PRAGMA table_info(practice_sessions)").use { cursor ->
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "session_scope") {
                        found = true
                        assertEquals("PRACTICE", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")).trim('\'', '"'))
                    }
                }
                check(found)
            }
            database.query("SELECT session_scope FROM practice_sessions WHERE id = 'session-1'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("PRACTICE", cursor.getString(0))
            }
            database.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_practice_sessions_library_id_question_type_session_scope'").use { cursor ->
                check(cursor.moveToFirst())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5AddsExamTables() {
        helper.createDatabase("migration-test-v5", 4).apply {
            execSQL(
                "INSERT INTO libraries(id, name, libraryType, createdAtEpochMillis, updatedAtEpochMillis, warning_count) VALUES ('library-1', '试卷', '试卷', 1, 1, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-test-v5",
            5,
            true,
            TishanDatabaseMigrations.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('exam_settings', 'exam_sessions', 'exam_results')").use { cursor ->
                var count = 0
                while (cursor.moveToNext()) count++
                assertEquals(3, count)
            }
        }
    }
}
