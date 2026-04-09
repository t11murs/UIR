package com.example.uir_android.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tm_programs")
data class TmProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val sourceText: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "tm_runs")
data class TmRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long?,
    val inputTape: String,
    val halted: Boolean,
    val stepsCount: Int,
    val endedState: String,
    val errorMessage: String?,
    val traceJson: String,
    val createdAt: Long
)

fun TmProgramEntity.toDomain(): TmProgram = TmProgram(
    id = id,
    name = name,
    description = description,
    sourceText = sourceText,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TmProgram.toEntity(): TmProgramEntity = TmProgramEntity(
    id = id,
    name = name,
    description = description,
    sourceText = sourceText,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TmRunEntity.toDomain(): TmRun = TmRun(
    id = id,
    programId = programId,
    inputTape = inputTape,
    halted = halted,
    stepsCount = stepsCount,
    endedState = endedState,
    errorMessage = errorMessage,
    traceJson = traceJson,
    createdAt = createdAt
)

fun TmRun.toEntity(): TmRunEntity = TmRunEntity(
    id = id,
    programId = programId,
    inputTape = inputTape,
    halted = halted,
    stepsCount = stepsCount,
    endedState = endedState,
    errorMessage = errorMessage,
    traceJson = traceJson,
    createdAt = createdAt
)

@Dao
interface TmProgramDao {
    @Query("SELECT * FROM tm_programs ORDER BY updatedAt DESC, id DESC")
    fun observePrograms(): Flow<List<TmProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: TmProgramEntity): Long

    @Query("SELECT COUNT(*) FROM tm_programs")
    suspend fun countPrograms(): Int
}

@Dao
interface TmRunDao {
    @Query("SELECT * FROM tm_runs ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentRuns(limit: Int): Flow<List<TmRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: TmRunEntity): Long
}

@Database(
    entities = [TmProgramEntity::class, TmRunEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UirDatabase : RoomDatabase() {
    abstract fun tmProgramDao(): TmProgramDao
    abstract fun tmRunDao(): TmRunDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): UirDatabase = Room.databaseBuilder(
        context,
        UirDatabase::class.java,
        "uir_turing.db"
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideTmProgramDao(database: UirDatabase): TmProgramDao = database.tmProgramDao()

    @Provides
    fun provideTmRunDao(database: UirDatabase): TmRunDao = database.tmRunDao()
}
