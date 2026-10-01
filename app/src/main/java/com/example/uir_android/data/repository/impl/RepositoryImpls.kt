package com.example.uir_android.data.repository.impl

import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.domain.repository.TestDraftRepository
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.data.db.TmProgramDao
import com.example.uir_android.data.db.TmRunDao
import com.example.uir_android.data.db.toDomain
import com.example.uir_android.data.db.toEntity
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.model.AppThemeMode
import com.example.uir_android.domain.model.defaultTmPrograms
import com.example.uir_android.domain.repository.SettingsRepository
import com.example.uir_android.domain.repository.TmRepository
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.TestRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TmRepositoryImpl @Inject constructor(
    private val programDao: TmProgramDao,
    private val runDao: TmRunDao,
    private val dispatchers: AppDispatchers
) : TmRepository {
    override fun observePrograms() = programDao.observePrograms().map { programs ->
        programs.map { it.toDomain() }
    }

    override fun observeRecentRuns(limit: Int) = runDao.observeRecentRuns(limit).map { runs ->
        runs.map { it.toDomain() }
    }

    override suspend fun saveProgram(program: TmProgram): AppResult<Long> = withContext(dispatchers.io) {
        if (program.name.isBlank()) {
            return@withContext AppResult.Error("Введите имя программы")
        }
        if (program.sourceText.isBlank()) {
            return@withContext AppResult.Error("Введите правила машины Тьюринга")
        }

        val rowId = programDao.insert(program.toEntity())
        AppResult.Success(rowId, "Программа сохранена")
    }

    override suspend fun saveRun(run: TmRun): AppResult<Long> = withContext(dispatchers.io) {
        val rowId = runDao.insert(run.toEntity())
        AppResult.Success(rowId)
    }

    override suspend fun ensurePresetPrograms(): AppResult<Unit> = withContext(dispatchers.io) {
        if (programDao.countPrograms() == 0) {
            defaultTmPrograms().forEach { preset ->
                programDao.insert(preset.toEntity())
            }
        }
        AppResult.Success(Unit)
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsStore: AppSettingsStore,
    private val dispatchers: AppDispatchers
) : SettingsRepository {
    override fun observeSettings() = settingsStore.settingsFlow

    override suspend fun updateThemeMode(mode: AppThemeMode) {
        withContext(dispatchers.io) {
            settingsStore.setThemeMode(mode)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindTmRepository(impl: TmRepositoryImpl): TmRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindTestRepository(impl: TestRepositoryImpl): TestRepository

    @Binds
    abstract fun bindEmulatorControlRepository(
        impl: EmulatorControlRepositoryImpl
    ): EmulatorControlRepository

    @Binds
    abstract fun bindTestDraftRepository(impl: RoomTestDraftRepository): TestDraftRepository

    @Binds
    abstract fun bindEmulatorControlDraftRepository(
        impl: RoomEmulatorControlDraftRepository
    ): EmulatorControlDraftRepository
}
