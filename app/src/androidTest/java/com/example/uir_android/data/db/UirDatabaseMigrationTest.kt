package com.example.uir_android.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UirDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        UirDatabase::class.java
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate1To6_preservesExistingData() = migrateToLatestFrom(1)

    @Test
    fun migrate2To6_preservesExistingData() = migrateToLatestFrom(2)

    @Test
    fun migrate3To6_preservesExistingData() = migrateToLatestFrom(3)

    @Test
    fun migrate4To6_preservesExistingData() = migrateToLatestFrom(4)

    @Test
    fun migrate5To6_preservesExistingDataAndClearsLegacyReviewCache() = migrateToLatestFrom(5)

    private fun migrateToLatestFrom(startVersion: Int) {
        createDatabaseAtVersion(startVersion)

        val migrated = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            LATEST_VERSION,
            true,
            *UIR_DATABASE_MIGRATIONS
        )

        assertText(migrated, "SELECT name FROM tm_programs WHERE id = 41", "Saved program")
        assertText(migrated, "SELECT inputTape FROM tm_runs WHERE id = 42", "101")
        assertRowCount(migrated, "active_test_drafts", if (startVersion >= 2) 1 else 0)
        assertRowCount(migrated, "seminar_comments", if (startVersion >= 3) 1 else 0)
        assertRowCount(migrated, "emulator_control_drafts", if (startVersion >= 4) 1 else 0)

        // Version 5 cache entries had no runId and cannot be associated with an attempt safely.
        assertRowCount(migrated, "completed_test_reviews", 0)
        migrated.close()
    }

    private fun createDatabaseAtVersion(version: Int) {
        context.deleteDatabase(DATABASE_NAME)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersionOneSchema(db)
                        UIR_DATABASE_MIGRATIONS
                            .filter { migration -> migration.endVersion <= version }
                            .forEach { migration -> migration.migrate(db) }
                        insertFixtures(db, version)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .also { helper -> helper.writableDatabase }
            .close()
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tm_programs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                sourceText TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tm_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                programId INTEGER,
                inputTape TEXT NOT NULL,
                halted INTEGER NOT NULL,
                stepsCount INTEGER NOT NULL,
                endedState TEXT NOT NULL,
                errorMessage TEXT,
                traceJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun insertFixtures(db: SupportSQLiteDatabase, version: Int) {
        db.execSQL(
            """
            INSERT INTO tm_programs(id, name, description, sourceText, createdAt, updatedAt)
            VALUES(41, 'Saved program', 'description', 'S0 a -> b R S0', 100, 200)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO tm_runs(
                id, programId, inputTape, halted, stepsCount, endedState,
                errorMessage, traceJson, createdAt
            ) VALUES(42, 41, '101', 1, 3, 'HALT', NULL, '[]', 300)
            """.trimIndent()
        )
        if (version >= 2) {
            db.execSQL(
                """
                INSERT INTO active_test_drafts(
                    ownerKey, testId, runId, endsAt, currentQuestionIndex,
                    answersJson, markedQuestionIdsJson, updatedAt
                ) VALUES('student@example.com', 11, 12, '2030-01-01T00:00:00Z', 2, '{}', '[]', 400)
                """.trimIndent()
            )
        }
        if (version >= 3) {
            db.execSQL(
                """
                INSERT INTO seminar_comments(ownerKey, seminarPassId, comment, updatedAt)
                VALUES('teacher@example.com', 21, 'comment', 500)
                """.trimIndent()
            )
        }
        if (version >= 4) {
            db.execSQL(
                """
                INSERT INTO emulator_control_drafts(
                    ownerKey, controlId, runId, endsAt, currentQuestionIndex,
                    questionsJson, updatedAt
                ) VALUES('student@example.com', 31, 32, '2030-01-01T00:00:00Z', 1, '[]', 600)
                """.trimIndent()
            )
        }
        if (version >= 5) {
            db.execSQL(
                """
                INSERT INTO completed_test_reviews(ownerKey, testId, reviewJson, updatedAt)
                VALUES('student@example.com', 11, '{}', 700)
                """.trimIndent()
            )
        }
    }

    private fun assertText(db: SupportSQLiteDatabase, query: String, expected: String) {
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private fun assertRowCount(db: SupportSQLiteDatabase, table: String, expected: Int) {
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
        const val LATEST_VERSION = 6
    }
}
