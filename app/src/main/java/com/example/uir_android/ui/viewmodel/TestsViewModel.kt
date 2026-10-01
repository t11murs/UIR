package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import com.example.uir_android.domain.repository.TestRepository
import com.example.uir_android.domain.repository.TestDraftRepository
import com.example.uir_android.ui.state.ActiveAssessmentLock
import com.example.uir_android.ui.state.ActiveAssessmentType
import com.example.uir_android.ui.state.TestsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@HiltViewModel
class TestsViewModel @Inject constructor(
    private val testRepository: TestRepository,
    private val emulatorControlRepository: EmulatorControlRepository,
    private val authRepository: AuthRepository,
    private val testDraftRepository: TestDraftRepository,
    private val emulatorControlDraftRepository: EmulatorControlDraftRepository
) : EventViewModel() {
    private val _state = MutableStateFlow(TestsUiState(isLoading = true))
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var sessionOwnerKey: String = ""
    private val finishedTestIds = mutableSetOf<Int>()
    private val finishedControlIds = mutableSetOf<Int>()

    fun onSessionChanged(isLoggedIn: Boolean, email: String) {
        val normalizedOwner = email.trim().lowercase()
        if (!isLoggedIn) {
            refreshJob?.cancel()
            sessionOwnerKey = ""
            finishedTestIds.clear()
            finishedControlIds.clear()
            _state.value = TestsUiState(isLoading = true)
            return
        }
        if (sessionOwnerKey != normalizedOwner) {
            refreshJob?.cancel()
            sessionOwnerKey = normalizedOwner
            finishedTestIds.clear()
            finishedControlIds.clear()
            _state.value = TestsUiState(isLoading = true)
            refresh()
        } else if (!_state.value.assessmentGuardReady && refreshJob?.isActive != true) {
            refresh()
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val ownerKey = authRepository.currentSession().email
                val testsDeferred = async {
                    loadSafely("Не удалось загрузить тесты") { testRepository.loadTests() }
                }
                val controlsDeferred = async {
                    loadSafely("Не удалось загрузить контрольные") {
                        emulatorControlRepository.loadControls()
                    }
                }
                val draftsDeferred = async {
                    loadDraftIdsSafely(ownerKey) { testDraftRepository.getActiveTestIds(it) }
                }
                val controlDraftsDeferred = async {
                    loadDraftIdsSafely(ownerKey) {
                        emulatorControlDraftRepository.getActiveControlIds(it)
                    }
                }
                val draftsResult = draftsDeferred.await()
                val controlDraftsResult = controlDraftsDeferred.await()
                _state.update {
                    it.copy(
                        localDraftTestIds = draftsResult.ids - finishedTestIds,
                        localDraftControlIds = controlDraftsResult.ids - finishedControlIds
                    )
                }
                val testsResult = testsDeferred.await()
                val controlsResult = controlsDeferred.await()
                val allTests = (testsResult as? AppResult.Success)?.data.orEmpty()
                val controls = (controlsResult as? AppResult.Success)?.data.orEmpty()
                    .filterNot(TestSummary::isAdaptive)
                val tests = filterNativeTests(allTests, controls)
                val completedResults = completedResultsFromSummaries(tests)
                val errorMessage = (testsResult as? AppResult.Error)?.message
                    ?: (controlsResult as? AppResult.Error)?.message
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        assessmentGuardReady = true,
                        testsRequestSucceeded = testsResult is AppResult.Success,
                        controlsRequestSucceeded = controlsResult is AppResult.Success,
                        tests = tests,
                        emulatorControls = controls,
                        localDraftTestIds = draftsResult.ids - finishedTestIds,
                        localDraftControlIds = controlDraftsResult.ids - finishedControlIds,
                        completedResults = completedResults,
                        errorMessage = errorMessage
                    )
                }
                draftsResult.errorMessage?.let(::emitSnackbar)
                controlDraftsResult.errorMessage?.let(::emitSnackbar)
                if (errorMessage != null) {
                    emitSnackbar(errorMessage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Не удалось обновить список тестов"
                _state.update { it.copy(isLoading = false, hasLoaded = true, errorMessage = message) }
                emitSnackbar(message)
            }
        }
    }

    fun markAssessmentStarted(assessmentId: Int, runId: Int, emulatorControl: Boolean) {
        if (assessmentId <= 0 || runId <= 0) return
        val type = if (emulatorControl) {
            finishedControlIds.remove(assessmentId)
            ActiveAssessmentType.EMULATOR_CONTROL
        } else {
            finishedTestIds.remove(assessmentId)
            ActiveAssessmentType.TEST
        }
        _state.update { current ->
            current.copy(
                tests = if (emulatorControl) current.tests else current.tests.map { assessment ->
                    assessment.startCurrentRunIfMatches(assessmentId, runId)
                },
                emulatorControls = if (emulatorControl) {
                    current.emulatorControls.map { assessment ->
                        assessment.startCurrentRunIfMatches(assessmentId, runId)
                    }
                } else {
                    current.emulatorControls
                },
                activeAssessmentLock = ActiveAssessmentLock(
                    type = type,
                    assessmentId = assessmentId,
                    runId = runId
                )
            )
        }
    }

    fun markAssessmentFinished(assessmentId: Int, emulatorControl: Boolean) {
        if (emulatorControl) {
            finishedControlIds += assessmentId
        } else {
            finishedTestIds += assessmentId
        }
        _state.update { current ->
            val clearedLock = current.activeAssessmentLock?.takeUnless {
                it.assessmentId == assessmentId &&
                    (it.type == ActiveAssessmentType.EMULATOR_CONTROL) == emulatorControl
            }
            if (emulatorControl) {
                current.copy(
                    emulatorControls = current.emulatorControls.map { assessment ->
                        assessment.finishCurrentRunIfMatches(assessmentId)
                    },
                    localDraftControlIds = current.localDraftControlIds - assessmentId,
                    activeAssessmentLock = clearedLock
                )
            } else {
                current.copy(
                    tests = current.tests.map { assessment ->
                        assessment.finishCurrentRunIfMatches(assessmentId)
                    },
                    localDraftTestIds = current.localDraftTestIds - assessmentId,
                    activeAssessmentLock = clearedLock
                )
            }
        }
        refresh()
    }
}

private fun TestSummary.startCurrentRunIfMatches(
    assessmentId: Int,
    runId: Int
): TestSummary = if (id != assessmentId) {
    this
} else {
    copy(hasCurrentRun = true, currentResultId = runId)
}

private fun TestSummary.finishCurrentRunIfMatches(assessmentId: Int): TestSummary =
    if (id != assessmentId) {
        this
    } else {
        copy(
            attempts = maxOf(1, attempts),
            available = false,
            hasCurrentRun = false
        )
    }

internal data class DraftIdsLoadResult(
    val ids: Set<Int>,
    val errorMessage: String? = null
)

internal suspend fun loadDraftIdsSafely(
    ownerKey: String,
    load: suspend (String) -> Set<Int>
): DraftIdsLoadResult {
    if (ownerKey.isBlank()) return DraftIdsLoadResult(emptySet())
    return try {
        DraftIdsLoadResult(load(ownerKey))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DraftIdsLoadResult(
            ids = emptySet(),
            errorMessage = "Не удалось проверить сохранённые ответы"
        )
    }
}

private suspend fun <T> loadSafely(
    fallbackMessage: String,
    load: suspend () -> AppResult<T>
): AppResult<T> = try {
    load()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    AppResult.Error(fallbackMessage)
}

internal fun TestSummary.isCompleted(): Boolean =
    !hasCurrentRun && (attempts > 0 || latestResult != null)

internal fun completedResultsFromSummaries(tests: List<TestSummary>) =
    tests.mapNotNull { test -> test.latestResult?.let { test.id to it } }.toMap()

internal fun filterNativeTests(
    tests: List<TestSummary>,
    controls: List<TestSummary>
): List<TestSummary> {
    val controlIds = controls.mapTo(mutableSetOf()) { it.id }
    return tests.filterNot { it.isTuringControl || it.id in controlIds || it.isAdaptive }
}
