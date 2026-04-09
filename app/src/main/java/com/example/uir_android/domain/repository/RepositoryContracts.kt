package com.example.uir_android.domain.repository

import com.example.uir_android.core.util.AppResult
import com.example.uir_android.domain.model.AppSettings
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import kotlinx.coroutines.flow.Flow

interface TmRepository {
    fun observePrograms(): Flow<List<TmProgram>>

    fun observeRecentRuns(limit: Int = 10): Flow<List<TmRun>>

    suspend fun saveProgram(program: TmProgram): AppResult<Long>

    suspend fun saveRun(run: TmRun): AppResult<Long>

    suspend fun ensurePresetPrograms(): AppResult<Unit>
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun getCurrentSettings(): AppSettings

    suspend fun updateMaxRunSteps(value: Int)

    suspend fun updateRunDelayMs(value: Long)

    suspend fun updateDebugEnabled(enabled: Boolean)
}
