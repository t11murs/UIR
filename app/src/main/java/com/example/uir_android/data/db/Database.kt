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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(
    tableName = "active_test_drafts",
    primaryKeys = ["ownerKey", "testId"]
)
data class ActiveTestDraftEntity(
    val ownerKey: String,
    val testId: Int,
    val runId: Int,
    val endsAt: String,
    val currentQuestionIndex: Int,
    val answersJson: String,
    val markedQuestionIdsJson: String,
    val updatedAt: Long
)

@Entity(
    tableName = "emulator_control_drafts",
    primaryKeys = ["ownerKey", "controlId"]
)
data class EmulatorControlDraftEntity(
    val ownerKey: String,
    val controlId: Int,
    val runId: Int,
    val endsAt: String,
    val currentQuestionIndex: Int,
    val questionsJson: String,
    val updatedAt: Long
)

@Entity(
    tableName = "completed_test_reviews",
    primaryKeys = ["ownerKey", "testId", "runId"]
)
data class CompletedTestReviewEntity(
    val ownerKey: String,
    val testId: Int,
    val runId: Int,
    val reviewJson: String,
    val updatedAt: Long
)

@Entity(
    tableName = "seminar_comments",
    primaryKeys = ["ownerKey", "seminarPassId"]
)
data class SeminarCommentEntity(
    val ownerKey: String,
    val seminarPassId: Int,
    val comment: String,
    val updatedAt: Long
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

@Dao
interface ActiveTestDraftDao {
    @Query("SELECT * FROM active_test_drafts WHERE ownerKey = :ownerKey AND testId = :testId LIMIT 1")
    suspend fun get(ownerKey: String, testId: Int): ActiveTestDraftEntity?

    @Query("SELECT * FROM active_test_drafts WHERE ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<ActiveTestDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ActiveTestDraftEntity)

    @Query("DELETE FROM active_test_drafts WHERE ownerKey = :ownerKey AND testId = :testId")
    suspend fun delete(ownerKey: String, testId: Int)

    @Query("DELETE FROM active_test_drafts WHERE ownerKey = :ownerKey")
    suspend fun deleteAllForOwner(ownerKey: String)
}

@Dao
interface EmulatorControlDraftDao {
    @Query("SELECT * FROM emulator_control_drafts WHERE ownerKey = :ownerKey AND controlId = :controlId LIMIT 1")
    suspend fun get(ownerKey: String, controlId: Int): EmulatorControlDraftEntity?

    @Query("SELECT * FROM emulator_control_drafts WHERE ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<EmulatorControlDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: EmulatorControlDraftEntity)

    @Query("DELETE FROM emulator_control_drafts WHERE ownerKey = :ownerKey AND controlId = :controlId")
    suspend fun delete(ownerKey: String, controlId: Int)
}

@Dao
interface CompletedTestReviewDao {
    @Query(
        "SELECT * FROM completed_test_reviews " +
            "WHERE ownerKey = :ownerKey AND testId = :testId " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatest(ownerKey: String, testId: Int): CompletedTestReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: CompletedTestReviewEntity)

    @Query("DELETE FROM completed_test_reviews WHERE ownerKey = :ownerKey AND testId = :testId")
    suspend fun deleteForTest(ownerKey: String, testId: Int)
}

@Database(
    entities = [
        TmProgramEntity::class,
        TmRunEntity::class,
        ActiveTestDraftEntity::class,
        EmulatorControlDraftEntity::class,
        CompletedTestReviewEntity::class,
        SeminarCommentEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class UirDatabase : RoomDatabase() {
    abstract fun tmProgramDao(): TmProgramDao
    abstract fun tmRunDao(): TmRunDao
    abstract fun activeTestDraftDao(): ActiveTestDraftDao
    abstract fun emulatorControlDraftDao(): EmulatorControlDraftDao
    abstract fun completedTestReviewDao(): CompletedTestReviewDao
}

private val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS active_test_drafts (
                ownerKey TEXT NOT NULL,
                testId INTEGER NOT NULL,
                runId INTEGER NOT NULL,
                endsAt TEXT NOT NULL,
                currentQuestionIndex INTEGER NOT NULL,
                answersJson TEXT NOT NULL,
                markedQuestionIdsJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerKey, testId)
            )
            """.trimIndent()
        )
    }
}

private val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS seminar_comments (
                ownerKey TEXT NOT NULL,
                seminarPassId INTEGER NOT NULL,
                comment TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerKey, seminarPassId)
            )
            """.trimIndent()
        )
    }
}

private val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS emulator_control_drafts (
                ownerKey TEXT NOT NULL,
                controlId INTEGER NOT NULL,
                runId INTEGER NOT NULL,
                endsAt TEXT NOT NULL,
                currentQuestionIndex INTEGER NOT NULL,
                questionsJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerKey, controlId)
            )
            """.trimIndent()
        )
    }
}

private val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS completed_test_reviews (
                ownerKey TEXT NOT NULL,
                testId INTEGER NOT NULL,
                reviewJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerKey, testId)
            )
            """.trimIndent()
        )
    }
}

private val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Version 5 cannot identify the attempt stored in each row reliably.
        // Only the derived review cache is reset; programs, runs and drafts stay intact.
        db.execSQL("DROP TABLE IF EXISTS completed_test_reviews")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS completed_test_reviews (
                ownerKey TEXT NOT NULL,
                testId INTEGER NOT NULL,
                runId INTEGER NOT NULL,
                reviewJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerKey, testId, runId)
            )
            """.trimIndent()
        )
    }
}

val UIR_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(
    Migration1To2,
    Migration2To3,
    Migration3To4,
    Migration4To5,
    Migration5To6
)

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
    )
        .addMigrations(*UIR_DATABASE_MIGRATIONS)
        .build()

    @Provides
    fun provideTmProgramDao(database: UirDatabase): TmProgramDao = database.tmProgramDao()

    @Provides
    fun provideTmRunDao(database: UirDatabase): TmRunDao = database.tmRunDao()

    @Provides
    fun provideActiveTestDraftDao(database: UirDatabase): ActiveTestDraftDao =
        database.activeTestDraftDao()

    @Provides
    fun provideEmulatorControlDraftDao(database: UirDatabase): EmulatorControlDraftDao =
        database.emulatorControlDraftDao()

    @Provides
    fun provideCompletedTestReviewDao(database: UirDatabase): CompletedTestReviewDao =
        database.completedTestReviewDao()

}
