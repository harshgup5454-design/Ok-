package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sensitivity_profiles")
data class SensitivityProfileEntity(
    @PrimaryKey val id: String,
    val profileName: String,
    val gameName: String,
    val sensitivityStyle: String,
    val dpiTier: Int,
    val general: Int,
    val redDot: Int,
    val scope2x: Int,
    val scope4x: Int,
    val sniper: Int,
    val freeLook: Int,
    val mathematicalRationale: String
)

@Entity(tableName = "rotation_calibrations")
data class RotationCalibrationEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val swipeDistancePx: Float,
    val durationMs: Long,
    val avgSpeedPxPerSec: Float,
    val consistencyScore: Float,
    val recommendedStyle: String,
    val recommendedGeneralSensitivity: Int,
    val advice: String
)

@Entity(tableName = "aim_training_sessions")
data class AimTrainingSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val difficulty: String,
    val targetSize: String,
    val targetMovement: String,
    val targetsSpawned: Int,
    val targetsHit: Int,
    val accuracyPercent: Float,
    val avgReactionTimeMs: Int,
    val bestStreak: Int,
    val missCount: Int,
    val score: Int
)

@Dao
interface AimIntelligenceDao {
    @Query("SELECT * FROM sensitivity_profiles")
    fun getAllProfilesFlow(): Flow<List<SensitivityProfileEntity>>

    @Query("SELECT * FROM sensitivity_profiles")
    suspend fun getAllProfiles(): List<SensitivityProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SensitivityProfileEntity)

    @Query("DELETE FROM sensitivity_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM rotation_calibrations ORDER BY timestamp DESC")
    fun getAllCalibrationsFlow(): Flow<List<RotationCalibrationEntity>>

    @Query("SELECT * FROM rotation_calibrations ORDER BY timestamp DESC")
    suspend fun getAllCalibrations(): List<RotationCalibrationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: RotationCalibrationEntity)

    @Query("SELECT * FROM aim_training_sessions ORDER BY timestamp DESC")
    fun getAllTrainingSessionsFlow(): Flow<List<AimTrainingSessionEntity>>

    @Query("SELECT * FROM aim_training_sessions ORDER BY timestamp DESC")
    suspend fun getAllTrainingSessions(): List<AimTrainingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingSession(session: AimTrainingSessionEntity)

    @Query("DELETE FROM aim_training_sessions")
    suspend fun clearTrainingHistory()
}

@Entity(tableName = "game_profiles")
data class GameProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val performanceMode: String,
    val fpsLock: Int,
    val isInstalled: Boolean,
    val isInLibrary: Boolean,
    val isFavorite: Boolean,
    val lastPlayedTimestamp: Long,
    val customNotes: String,
    val recommendedGraphics: String,
    val recommendedFpsCap: String,
    val shadowSetting: String,
    val antiAliasing: String,
    val thermalAdvice: String,
    val batteryAdvice: String,
    val networkAdvice: String,
    val deviceLimitations: String,
    val previousMode: String? = null,
    val previousFpsLock: Int = 60
) {
    fun toDomain(): GameProfile = GameProfile(
        id = id,
        name = name,
        packageName = packageName,
        performanceMode = try { PerformanceMode.valueOf(performanceMode) } catch (e: Exception) { PerformanceMode.BALANCED },
        fpsLock = fpsLock,
        isInstalled = isInstalled,
        isInLibrary = isInLibrary,
        isFavorite = isFavorite,
        lastPlayedTimestamp = lastPlayedTimestamp,
        customNotes = customNotes,
        recommendedGraphics = recommendedGraphics,
        recommendedFpsCap = recommendedFpsCap,
        shadowSetting = shadowSetting,
        antiAliasing = antiAliasing,
        thermalAdvice = thermalAdvice,
        batteryAdvice = batteryAdvice,
        networkAdvice = networkAdvice,
        deviceLimitations = deviceLimitations,
        previousMode = previousMode?.let {
            try { PerformanceMode.valueOf(it) } catch (_: Exception) { null }
        },
        previousFpsLock = previousFpsLock
    )

    companion object {
        fun fromDomain(profile: GameProfile): GameProfileEntity = GameProfileEntity(
            id = profile.id,
            name = profile.name,
            packageName = profile.packageName,
            performanceMode = profile.performanceMode.name,
            fpsLock = profile.fpsLock,
            isInstalled = profile.isInstalled,
            isInLibrary = profile.isInLibrary,
            isFavorite = profile.isFavorite,
            lastPlayedTimestamp = profile.lastPlayedTimestamp,
            customNotes = profile.customNotes,
            recommendedGraphics = profile.recommendedGraphics,
            recommendedFpsCap = profile.recommendedFpsCap,
            shadowSetting = profile.shadowSetting,
            antiAliasing = profile.antiAliasing,
            thermalAdvice = profile.thermalAdvice,
            batteryAdvice = profile.batteryAdvice,
            networkAdvice = profile.networkAdvice,
            deviceLimitations = profile.deviceLimitations,
            previousMode = profile.previousMode?.name,
            previousFpsLock = profile.previousFpsLock
        )
    }
}

@Entity(tableName = "session_reports")
data class SessionReportEntity(
    @PrimaryKey val id: String,
    val sessionLabel: String,
    val gameTitle: String,
    val graphicsSettings: String,
    val durationSeconds: Int,
    val startTempC: Float,
    val peakTempC: Float,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,
    val startThermalStatus: String,
    val peakThermalStatus: String,
    val avgAvailRamGb: Float,
    val minAvailRamGb: Float,
    val avgPingMs: Int,
    val avgJitterMs: Int,
    val displayRefreshRateHz: Float,
    val fpsMeasurementSource: String,
    val measuredAvgFps: Float?,
    val measuredMinFps: Float?,
    val measuredFrameTimeMs: Float?,
    val throttlingObserved: Boolean,
    val testNotes: String,
    val conclusion: String,
    val timestamp: Long,
    val packageName: String = "",
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val autopilotMode: String = "Manual",
    val snapshotsJson: String = ""
) {
    fun toDomain(): BenchmarkSessionReport = BenchmarkSessionReport(
        id = id,
        sessionLabel = sessionLabel,
        gameTitle = gameTitle,
        packageName = packageName,
        graphicsSettings = graphicsSettings,
        startTimeMs = if (startTimeMs > 0L) startTimeMs else timestamp,
        endTimeMs = if (endTimeMs > 0L) endTimeMs else timestamp,
        autopilotMode = autopilotMode,
        durationSeconds = durationSeconds,
        startTempC = startTempC,
        peakTempC = peakTempC,
        startBatteryLevel = startBatteryLevel,
        endBatteryLevel = endBatteryLevel,
        startThermalStatus = startThermalStatus,
        peakThermalStatus = peakThermalStatus,
        avgAvailRamGb = avgAvailRamGb,
        minAvailRamGb = minAvailRamGb,
        avgPingMs = avgPingMs,
        avgJitterMs = avgJitterMs,
        displayRefreshRateHz = displayRefreshRateHz,
        fpsMeasurementSource = fpsMeasurementSource,
        measuredAvgFps = measuredAvgFps,
        measuredMinFps = measuredMinFps,
        measuredFrameTimeMs = measuredFrameTimeMs,
        throttlingObserved = throttlingObserved,
        testNotes = testNotes,
        conclusion = conclusion,
        snapshots = BenchmarkSessionReport.deserializeSnapshots(snapshotsJson)
    )

    companion object {
        fun fromDomain(report: BenchmarkSessionReport): SessionReportEntity = SessionReportEntity(
            id = report.id,
            sessionLabel = report.sessionLabel,
            gameTitle = report.gameTitle,
            graphicsSettings = report.graphicsSettings,
            durationSeconds = report.durationSeconds,
            startTempC = report.startTempC,
            peakTempC = report.peakTempC,
            startBatteryLevel = report.startBatteryLevel,
            endBatteryLevel = report.endBatteryLevel,
            startThermalStatus = report.startThermalStatus,
            peakThermalStatus = report.peakThermalStatus,
            avgAvailRamGb = report.avgAvailRamGb,
            minAvailRamGb = report.minAvailRamGb,
            avgPingMs = report.avgPingMs,
            avgJitterMs = report.avgJitterMs,
            displayRefreshRateHz = report.displayRefreshRateHz,
            fpsMeasurementSource = report.fpsMeasurementSource,
            measuredAvgFps = report.measuredAvgFps,
            measuredMinFps = report.measuredMinFps,
            measuredFrameTimeMs = report.measuredFrameTimeMs,
            throttlingObserved = report.throttlingObserved,
            testNotes = report.testNotes,
            conclusion = report.conclusion,
            timestamp = report.endTimeMs,
            packageName = report.packageName,
            startTimeMs = report.startTimeMs,
            endTimeMs = report.endTimeMs,
            autopilotMode = report.autopilotMode,
            snapshotsJson = BenchmarkSessionReport.serializeSnapshots(report.snapshots)
        )
    }
}

@Dao
interface GameSpaceDao {
    @Query("SELECT * FROM game_profiles")
    fun getAllGameProfilesFlow(): Flow<List<GameProfileEntity>>

    @Query("SELECT * FROM game_profiles")
    suspend fun getAllGameProfiles(): List<GameProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameProfile(profile: GameProfileEntity)

    @Query("DELETE FROM game_profiles WHERE id = :id")
    suspend fun deleteGameProfile(id: String)

    @Query("SELECT * FROM session_reports ORDER BY timestamp DESC")
    fun getAllSessionReportsFlow(): Flow<List<SessionReportEntity>>

    @Query("SELECT * FROM session_reports ORDER BY timestamp DESC")
    suspend fun getAllSessionReports(): List<SessionReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionReport(report: SessionReportEntity)

    @Query("DELETE FROM session_reports")
    suspend fun clearSessionReports()

    @Query("SELECT * FROM benchmark_lab_results ORDER BY timestamp DESC")
    fun getAllBenchmarkLabResultsFlow(): Flow<List<BenchmarkLabResultEntity>>

    @Query("SELECT * FROM benchmark_lab_results ORDER BY timestamp DESC")
    suspend fun getAllBenchmarkLabResults(): List<BenchmarkLabResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmarkLabResult(result: BenchmarkLabResultEntity)

    @Query("DELETE FROM benchmark_lab_results")
    suspend fun clearBenchmarkLabResults()

    @Query("SELECT * FROM advisor_recommendations ORDER BY timestamp DESC")
    fun getAllAdvisorRecommendationsFlow(): Flow<List<AdvisorRecommendationEntity>>

    @Query("SELECT * FROM advisor_recommendations ORDER BY timestamp DESC")
    suspend fun getAllAdvisorRecommendations(): List<AdvisorRecommendationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdvisorRecommendation(rec: AdvisorRecommendationEntity)

    @Query("UPDATE advisor_recommendations SET isDismissed = 1 WHERE ruleId = :ruleId")
    suspend fun dismissRecommendationByRuleId(ruleId: String)

    @Query("DELETE FROM advisor_recommendations")
    suspend fun clearAdvisorRecommendations()
}

@Entity(tableName = "benchmark_lab_results")
data class BenchmarkLabResultEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val workloadType: String,
    val durationMs: Long,
    val scoreOrOpsPerSec: Double,
    val startBatteryTempC: Float,
    val peakBatteryTempC: Float,
    val startThermalStatus: String,
    val peakThermalStatus: String,
    val detailsSummary: String,
    val wasAbortedDueToThermal: Boolean
)

@Entity(tableName = "advisor_recommendations")
data class AdvisorRecommendationEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val title: String,
    val measuredFact: String,
    val possibleCausesAndOutcomes: String,
    val suggestedAction: String,
    val severity: String,
    val timestamp: Long,
    val isDismissed: Boolean
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_profiles` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `performanceMode` TEXT NOT NULL,
                `fpsLock` INTEGER NOT NULL,
                `isInstalled` INTEGER NOT NULL,
                `isInLibrary` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `lastPlayedTimestamp` INTEGER NOT NULL,
                `customNotes` TEXT NOT NULL,
                `recommendedGraphics` TEXT NOT NULL,
                `recommendedFpsCap` TEXT NOT NULL,
                `shadowSetting` TEXT NOT NULL,
                `antiAliasing` TEXT NOT NULL,
                `thermalAdvice` TEXT NOT NULL,
                `batteryAdvice` TEXT NOT NULL,
                `networkAdvice` TEXT NOT NULL,
                `deviceLimitations` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_reports` (
                `id` TEXT NOT NULL,
                `sessionLabel` TEXT NOT NULL,
                `gameTitle` TEXT NOT NULL,
                `graphicsSettings` TEXT NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `startTempC` REAL NOT NULL,
                `peakTempC` REAL NOT NULL,
                `startBatteryLevel` INTEGER NOT NULL,
                `endBatteryLevel` INTEGER NOT NULL,
                `startThermalStatus` TEXT NOT NULL,
                `peakThermalStatus` TEXT NOT NULL,
                `avgAvailRamGb` REAL NOT NULL,
                `minAvailRamGb` REAL NOT NULL,
                `avgPingMs` INTEGER NOT NULL,
                `avgJitterMs` INTEGER NOT NULL,
                `displayRefreshRateHz` REAL NOT NULL,
                `fpsMeasurementSource` TEXT NOT NULL,
                `measuredAvgFps` REAL,
                `measuredMinFps` REAL,
                `measuredFrameTimeMs` REAL,
                `throttlingObserved` INTEGER NOT NULL,
                `testNotes` TEXT NOT NULL,
                `conclusion` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `benchmark_lab_results` (
                `id` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `deviceModel` TEXT NOT NULL,
                `androidVersion` TEXT NOT NULL,
                `workloadType` TEXT NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `scoreOrOpsPerSec` REAL NOT NULL,
                `startBatteryTempC` REAL NOT NULL,
                `peakBatteryTempC` REAL NOT NULL,
                `startThermalStatus` TEXT NOT NULL,
                `peakThermalStatus` TEXT NOT NULL,
                `detailsSummary` TEXT NOT NULL,
                `wasAbortedDueToThermal` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

@Entity(tableName = "saved_system_state")
data class SavedSystemStateEntity(
    @PrimaryKey val id: String = "original_state",
    val timestamp: Long,
    val originalBrightnessMode: Int,
    val originalBrightnessLevel: Int,
    val originalScreenTimeoutMs: Int,
    val originalAutoRotate: Int,
    val originalDndFilter: Int,
    val appliedProfileName: String,
    val lastAppliedTimestamp: Long
)

@Dao
interface DeviceControlDao {
    @Query("SELECT * FROM saved_system_state WHERE id = 'original_state'")
    suspend fun getSavedSystemState(): SavedSystemStateEntity?

    @Query("SELECT * FROM saved_system_state WHERE id = 'original_state'")
    fun getSavedSystemStateFlow(): Flow<SavedSystemStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSystemState(state: SavedSystemStateEntity)

    @Query("DELETE FROM saved_system_state WHERE id = 'original_state'")
    suspend fun clearSavedSystemState()
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `advisor_recommendations` (
                `id` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `measuredFact` TEXT NOT NULL,
                `possibleCausesAndOutcomes` TEXT NOT NULL,
                `suggestedAction` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isDismissed` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_system_state` (
                `id` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `originalBrightnessMode` INTEGER NOT NULL,
                `originalBrightnessLevel` INTEGER NOT NULL,
                `originalScreenTimeoutMs` INTEGER NOT NULL,
                `originalAutoRotate` INTEGER NOT NULL,
                `originalDndFilter` INTEGER NOT NULL,
                `appliedProfileName` TEXT NOT NULL,
                `lastAppliedTimestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

@Entity(tableName = "network_history")
data class NetworkHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val connectionType: String,
    val avgPingMs: Int,
    val jitterMs: Int,
    val packetLossPercent: Int,
    val downloadMbps: Float,
    val uploadMbps: Float,
    val rating: String
) {
    fun toDomain(): NetworkHistoryRecord = NetworkHistoryRecord(
        id = id,
        timestamp = timestamp,
        connectionType = connectionType,
        avgPingMs = avgPingMs,
        jitterMs = jitterMs,
        packetLossPercent = packetLossPercent,
        downloadMbps = downloadMbps,
        uploadMbps = uploadMbps,
        rating = rating
    )

    companion object {
        fun fromDomain(record: NetworkHistoryRecord): NetworkHistoryEntity = NetworkHistoryEntity(
            id = record.id,
            timestamp = record.timestamp,
            connectionType = record.connectionType,
            avgPingMs = record.avgPingMs,
            jitterMs = record.jitterMs,
            packetLossPercent = record.packetLossPercent,
            downloadMbps = record.downloadMbps,
            uploadMbps = record.uploadMbps,
            rating = record.rating
        )
    }
}

@Dao
interface NetworkHistoryDao {
    @Query("SELECT * FROM network_history ORDER BY timestamp DESC LIMIT 30")
    fun getHistoryFlow(): Flow<List<NetworkHistoryEntity>>

    @Query("SELECT * FROM network_history ORDER BY timestamp DESC LIMIT 30")
    suspend fun getHistory(): List<NetworkHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: NetworkHistoryEntity)

    @Query("DELETE FROM network_history")
    suspend fun clearHistory()
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `game_profiles` ADD COLUMN `previousMode` TEXT")
        db.execSQL("ALTER TABLE `game_profiles` ADD COLUMN `previousFpsLock` INTEGER NOT NULL DEFAULT 60")
        db.execSQL("ALTER TABLE `session_reports` ADD COLUMN `packageName` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `session_reports` ADD COLUMN `startTimeMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `session_reports` ADD COLUMN `endTimeMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `session_reports` ADD COLUMN `autopilotMode` TEXT NOT NULL DEFAULT 'Manual'")
        db.execSQL("ALTER TABLE `session_reports` ADD COLUMN `snapshotsJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `network_history` (
                `id` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `connectionType` TEXT NOT NULL,
                `avgPingMs` INTEGER NOT NULL,
                `jitterMs` INTEGER NOT NULL,
                `packetLossPercent` INTEGER NOT NULL,
                `downloadMbps` REAL NOT NULL,
                `uploadMbps` REAL NOT NULL,
                `rating` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        SensitivityProfileEntity::class,
        RotationCalibrationEntity::class,
        AimTrainingSessionEntity::class,
        GameProfileEntity::class,
        SessionReportEntity::class,
        BenchmarkLabResultEntity::class,
        AdvisorRecommendationEntity::class,
        SavedSystemStateEntity::class,
        NetworkHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class GameBoostDatabase : RoomDatabase() {
    abstract fun aimIntelligenceDao(): AimIntelligenceDao
    abstract fun gameSpaceDao(): GameSpaceDao
    abstract fun deviceControlDao(): DeviceControlDao
    abstract fun networkHistoryDao(): NetworkHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: GameBoostDatabase? = null

        fun getDatabase(context: Context): GameBoostDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameBoostDatabase::class.java,
                    "gameboost_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
