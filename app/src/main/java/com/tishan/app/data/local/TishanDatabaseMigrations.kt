package com.tishan.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TishanDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val database = db
            database.execSQL(
                "ALTER TABLE libraries ADD COLUMN warning_count INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val database = db
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `practice_sessions` (
                    `id` TEXT NOT NULL,
                    `library_id` TEXT NOT NULL,
                    `question_type` TEXT NOT NULL,
                    `order_mode` TEXT NOT NULL,
                    `random_seed` INTEGER NOT NULL,
                    `question_ids_json` TEXT NOT NULL,
                    `current_index` INTEGER NOT NULL,
                    `selected_keys` TEXT NOT NULL,
                    `is_submitted` INTEGER NOT NULL,
                    `last_judgement` TEXT,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_sessions_library_id` ON `practice_sessions` (`library_id`)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_practice_sessions_library_id_question_type` ON `practice_sessions` (`library_id`, `question_type`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorites` (
                    `question_id` TEXT NOT NULL,
                    `added_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`question_id`),
                    FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_question_id` ON `favorites` (`question_id`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `practice_attempts` (
                    `id` TEXT NOT NULL,
                    `question_id` TEXT NOT NULL,
                    `library_id` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `submitted_answer` TEXT NOT NULL,
                    `judgement` TEXT NOT NULL,
                    `is_correct` INTEGER NOT NULL,
                    `occurred_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_attempts_question_id` ON `practice_attempts` (`question_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_attempts_library_id` ON `practice_attempts` (`library_id`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wrong_questions` (
                    `question_id` TEXT NOT NULL,
                    `library_id` TEXT NOT NULL,
                    `total_attempts` INTEGER NOT NULL,
                    `consecutive_correct` INTEGER NOT NULL,
                    `last_is_correct` INTEGER NOT NULL,
                    `last_attempt_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`question_id`),
                    FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_wrong_questions_library_id` ON `wrong_questions` (`library_id`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val database = db
            database.execSQL(
                "ALTER TABLE practice_sessions ADD COLUMN session_scope TEXT NOT NULL DEFAULT 'PRACTICE'",
            )
            database.execSQL(
                "DROP INDEX IF EXISTS `index_practice_sessions_library_id_question_type`",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_practice_sessions_library_id_question_type_session_scope` ON `practice_sessions` (`library_id`, `question_type`, `session_scope`)",
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val database = db
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_settings` (
                    `library_id` TEXT NOT NULL,
                    `single_choice_seconds` INTEGER NOT NULL,
                    `multiple_choice_seconds` INTEGER NOT NULL,
                    `true_false_seconds` INTEGER NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`library_id`),
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_sessions` (
                    `id` TEXT NOT NULL,
                    `library_id` TEXT NOT NULL,
                    `question_ids_json` TEXT NOT NULL,
                    `current_index` INTEGER NOT NULL,
                    `selected_keys` TEXT NOT NULL,
                    `answers_json` TEXT NOT NULL,
                    `elapsed_by_type_json` TEXT NOT NULL,
                    `active_question_type` TEXT,
                    `active_since_epoch_millis` INTEGER,
                    `is_paused` INTEGER NOT NULL,
                    `is_submitted` INTEGER NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_sessions_library_id` ON `exam_sessions` (`library_id`)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exam_results` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `library_id` TEXT NOT NULL,
                    `total_score` INTEGER NOT NULL,
                    `objective_total` INTEGER NOT NULL,
                    `elapsed_millis` INTEGER NOT NULL,
                    `type_scores_json` TEXT NOT NULL,
                    `wrong_question_ids_json` TEXT NOT NULL,
                    `submitted_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`session_id`) REFERENCES `exam_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`library_id`) REFERENCES `libraries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_results_session_id` ON `exam_results` (`session_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_results_library_id` ON `exam_results` (`library_id`)")
        }
    }
}
