package com.example.uir_android.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.data.repository.impl.RoomTestDraftRepository
import com.example.uir_android.domain.model.ActiveTestDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestDraftProcessRecreationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun latestAnswerSurvivesDatabaseAndRepositoryRecreation() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        val dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main)
        val expected = ActiveTestDraft(
            ownerKey = "Student@Example.com",
            testId = 7,
            runId = 55,
            endsAt = "2030-01-01T00:00:00Z",
            currentQuestionIndex = 2,
            answers = mapOf(
                17 to listOf("последний ответ"),
                18 to listOf("a", "c")
            ),
            markedQuestionIds = setOf(18),
            updatedAt = 1234L
        )

        val firstDatabase = openDatabase()
        try {
            RoomTestDraftRepository(
                firstDatabase.activeTestDraftDao(),
                Json,
                dispatchers
            ).save(expected)
        } finally {
            firstDatabase.close()
        }

        val recreatedDatabase = openDatabase()
        val restored: ActiveTestDraft?
        try {
            restored = RoomTestDraftRepository(
                recreatedDatabase.activeTestDraftDao(),
                Json,
                dispatchers
            ).get("student@example.com", expected.testId)
        } finally {
            recreatedDatabase.close()
        }

        assertNotNull(restored)
        assertEquals(expected.runId, restored?.runId)
        assertEquals(expected.endsAt, restored?.endsAt)
        assertEquals(expected.currentQuestionIndex, restored?.currentQuestionIndex)
        assertEquals(expected.answers, restored?.answers)
        assertEquals(expected.markedQuestionIds, restored?.markedQuestionIds)
    }

    private fun openDatabase(): UirDatabase = Room.databaseBuilder(
        context,
        UirDatabase::class.java,
        DATABASE_NAME
    )
        .addMigrations(*UIR_DATABASE_MIGRATIONS)
        .build()

    private companion object {
        const val DATABASE_NAME = "test-draft-process-recreation.db"
    }
}
