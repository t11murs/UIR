package com.example.uir_android.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.data.db.UirDatabase
import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.CompletedTestReviewStore
import com.example.uir_android.data.local.SessionCookieCipher
import com.example.uir_android.data.repository.impl.EmulatorControlRepositoryImpl
import com.example.uir_android.data.repository.impl.TestRepositoryImpl
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.TuringStateData
import com.example.uir_android.domain.model.TuringTaskData
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentNetworkInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()
    private lateinit var database: UirDatabase
    private lateinit var settingsStore: AppSettingsStore

    @Before
    fun setUp() = runBlocking {
        server.start()
        database = Room.inMemoryDatabaseBuilder(context, UirDatabase::class.java).build()
        settingsStore = AppSettingsStore(
            context = context,
            sessionCookieCipher = SessionCookieCipher(),
            dispatchers = dispatchers,
            applicationScope = applicationScope
        )
        settingsStore.clearServerSession()
        settingsStore.saveServerSession(
            email = "student@example.com",
            cookieHeader = "laravel_session=instrumented-session"
        )
    }

    @After
    fun tearDown() = runBlocking {
        settingsStore.clearServerSession()
        database.close()
        applicationScope.cancel()
        server.shutdown()
    }

    @Test
    fun ambiguousSubmissionsAreReconciledByExactRunId() = runBlocking {
        val errorMapper = HttpErrorMapper(ServerSessionGuard(settingsStore))
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
        val testService = ServerTestsService(
            httpClient = client,
            settingsStore = settingsStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
            serverBaseUrl = server.url("/"),
            applicationScope = applicationScope
        )
        val testRepository = TestRepositoryImpl(
            service = testService,
            completedReviewStore = CompletedTestReviewStore(
                dao = database.completedTestReviewDao(),
                json = Json,
                dispatchers = dispatchers
            ),
            settingsStore = settingsStore,
            applicationScope = applicationScope
        )
        val controlRepository = EmulatorControlRepositoryImpl(
            ServerEmulatorControlsService(
                httpClient = client,
                settingsStore = settingsStore,
                errorMapper = errorMapper,
                dispatchers = dispatchers,
                serverBaseUrl = server.url("/")
            )
        )

        server.enqueue(jsonResponse(502, """{"success":false,"message":"proxy failure"}"""))
        server.enqueue(jsonResponse(200, completedTestResultJson(TEST_RUN_ID)))

        val testResult = testRepository.submitTest(
            testId = TEST_ID,
            runId = TEST_RUN_ID,
            timedOut = true,
            answers = listOf(TestAnswer(QUESTION_ID, listOf("Ответ")))
        )

        assertTrue(testResult is AppResult.Success)
        assertEquals(TEST_RUN_ID, (testResult as AppResult.Success).data.runId)
        val testSubmit = server.takeRequest()
        assertEquals("/api/mobile/tests/$TEST_ID/submit", testSubmit.requestUrl?.encodedPath)
        assertTrue(testSubmit.body.readUtf8().contains("\"timedOut\":true"))
        assertEquals("laravel_session=instrumented-session", testSubmit.getHeader("Cookie"))
        val resultLookup = server.takeRequest()
        assertEquals("/api/mobile/tests/$TEST_ID/result", resultLookup.requestUrl?.encodedPath)
        assertEquals(TEST_RUN_ID.toString(), resultLookup.requestUrl?.queryParameter("runId"))

        server.enqueue(jsonResponse(504, """{"success":false,"message":"gateway timeout"}"""))
        server.enqueue(jsonResponse(200, completedControlResultJson(CONTROL_RUN_ID)))

        val controlResult = controlRepository.submit(
            controlId = CONTROL_ID,
            runId = CONTROL_RUN_ID,
            timedOut = true,
            answers = listOf(
                EmulatorAnswer(
                    questionId = QUESTION_ID,
                    task = TuringTaskData(
                        alphabet = listOf("a"),
                        automaton = listOf(
                            TuringStateData("S0", mapOf("a" to "a R S0"))
                        )
                    )
                )
            )
        )

        assertTrue(controlResult is AppResult.Success)
        assertEquals(CONTROL_RUN_ID, (controlResult as AppResult.Success).data.runId)
        val controlSubmit = server.takeRequest()
        assertEquals(
            "/api/mobile/emulator-controls/$CONTROL_ID/submit",
            controlSubmit.requestUrl?.encodedPath
        )
        assertTrue(controlSubmit.body.readUtf8().contains("\"timedOut\":true"))
        val controlLookup = server.takeRequest()
        assertEquals(
            "/api/mobile/emulator-controls/$CONTROL_ID/result",
            controlLookup.requestUrl?.encodedPath
        )
        assertEquals(CONTROL_RUN_ID.toString(), controlLookup.requestUrl?.queryParameter("runId"))
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private fun completedTestResultJson(runId: Int) = """
        {
          "success": true,
          "test": {
            "id": $TEST_ID,
            "name": "Критический тест",
            "type": "usual",
            "timeMinutes": 15,
            "totalPoints": 1.0,
            "runId": $runId,
            "endsAt": "2030-01-01T00:00:00Z",
            "questions": [{
              "id": $QUESTION_ID,
              "count": 1,
              "typeCode": 1,
              "typeName": "Один ответ",
              "text": ["Вопрос"],
              "variants": ["Ответ"],
              "supported": true
            }]
          },
          "result": {
            "runId": $runId,
            "score": 1.0,
            "total": 1.0,
            "markRu": "зачтено",
            "details": [{
              "questionId": $QUESTION_ID,
              "score": 1.0,
              "points": 1.0,
              "rightPercent": 100,
              "answers": ["Ответ"],
              "correctAnswers": ["Ответ"]
            }]
          }
        }
    """.trimIndent()

    private fun completedControlResultJson(runId: Int) = """
        {
          "success": true,
          "name": "Контрольная Тьюринга",
          "completed": true,
          "status": "COMPLETED",
          "result": {
            "runId": $runId,
            "score": 2.0,
            "total": 2.0,
            "markRu": "зачтено",
            "details": [{
              "questionId": $QUESTION_ID,
              "score": 2.0,
              "points": 2.0,
              "rightPercent": 100,
              "passed": 2,
              "totalSequences": 2,
              "feePercent": 0
            }]
          }
        }
    """.trimIndent()

    private companion object {
        const val TEST_ID = 7
        const val CONTROL_ID = 9
        const val QUESTION_ID = 17
        const val TEST_RUN_ID = 55
        const val CONTROL_RUN_ID = 88
    }
}
