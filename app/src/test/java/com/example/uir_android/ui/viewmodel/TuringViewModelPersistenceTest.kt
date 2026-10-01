package com.example.uir_android.ui.viewmodel

import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AppSettings
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.repository.SettingsRepository
import com.example.uir_android.domain.repository.TmRepository
import com.example.uir_android.domain.usecase.CreateInitialExecutionUseCase
import com.example.uir_android.domain.usecase.ParseProgramUseCase
import com.example.uir_android.domain.usecase.RunUseCase
import com.example.uir_android.domain.usecase.SaveRunUseCase
import com.example.uir_android.domain.usecase.StepUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TuringViewModelPersistenceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearing a finished run is immediate and persists its snapshot once`() = runTest(dispatcher) {
        val repository = DelayedTmRepository()
        val viewModel = createViewModel(repository)
        runCurrent()

        val rowId = viewModel.state.value.programRows.single().id
        viewModel.updateAlphabetText("a")
        viewModel.updateInputTape("a")
        viewModel.updateCommandLeftRule(rowId, "S0 a")
        viewModel.updateCommandRightRule(rowId, "a S HALT")
        viewModel.placeOnTape()
        viewModel.step()
        runCurrent()

        assertTrue(repository.saveStarted.isCompleted)
        assertEquals(1, viewModel.state.value.steps)

        viewModel.clearAll()

        assertEquals(0, viewModel.state.value.steps)
        assertEquals("", viewModel.state.value.inputTape)

        repository.allowSave.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.savedRuns.size)
        assertEquals(1, repository.savedRuns.single().stepsCount)
        assertEquals("a", repository.savedRuns.single().inputTape)
    }

    private fun createViewModel(repository: TmRepository): TuringViewModel {
        val stepUseCase = StepUseCase()
        val appDispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        return TuringViewModel(
            parseProgramUseCase = ParseProgramUseCase(appDispatchers),
            stepUseCase = stepUseCase,
            runUseCase = RunUseCase(
                stepUseCase = stepUseCase,
                dispatchers = appDispatchers
            ),
            createInitialExecutionUseCase = CreateInitialExecutionUseCase(),
            tmRepository = repository,
            saveRunUseCase = SaveRunUseCase(repository, Json),
            dispatchers = appDispatchers,
            applicationScope = CoroutineScope(dispatcher),
            settingsRepository = FakeSettingsRepository()
        )
    }
}

private class DelayedTmRepository : TmRepository {
    val saveStarted = CompletableDeferred<Unit>()
    val allowSave = CompletableDeferred<Unit>()
    val savedRuns = mutableListOf<TmRun>()
    private val programs = MutableStateFlow<List<TmProgram>>(emptyList())
    private val runs = MutableStateFlow<List<TmRun>>(emptyList())

    override fun observePrograms(): Flow<List<TmProgram>> = programs

    override fun observeRecentRuns(limit: Int): Flow<List<TmRun>> = runs

    override suspend fun saveProgram(program: TmProgram): AppResult<Long> =
        AppResult.Success(program.id)

    override suspend fun saveRun(run: TmRun): AppResult<Long> {
        saveStarted.complete(Unit)
        allowSave.await()
        savedRuns += run
        return AppResult.Success(savedRuns.size.toLong())
    }

    override suspend fun ensurePresetPrograms(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeSettingsRepository : SettingsRepository {
    private val settings = MutableStateFlow(
        AppSettings(maxRunSteps = 200, runDelayMs = 0, debugEnabled = false)
    )

    override fun observeSettings(): Flow<AppSettings> = settings

    override suspend fun updateThemeMode(mode: com.example.uir_android.domain.model.AppThemeMode) = Unit
}
