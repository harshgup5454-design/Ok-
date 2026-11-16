package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameBoostDatabaseTest {

    private lateinit var database: GameBoostDatabase
    private lateinit var aimDao: AimIntelligenceDao
    private lateinit var gameSpaceDao: GameSpaceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameBoostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aimDao = database.aimIntelligenceDao()
        gameSpaceDao = database.gameSpaceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSensitivityProfileInsertReadDelete() = runBlocking {
        val profile = SensitivityProfileEntity(
            id = "sens_test_1",
            profileName = "Pro Free Fire",
            gameName = "Free Fire MAX",
            sensitivityStyle = "Aggressive Sniping",
            dpiTier = 440,
            general = 95,
            redDot = 90,
            scope2x = 85,
            scope4x = 80,
            sniper = 60,
            freeLook = 75,
            mathematicalRationale = "Low DPI scaling factor applied."
        )

        aimDao.insertProfile(profile)
        var all = aimDao.getAllProfiles()
        assertEquals(1, all.size)
        assertEquals("Pro Free Fire", all[0].profileName)

        aimDao.deleteProfile("sens_test_1")
        all = aimDao.getAllProfiles()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGameProfileAndSessionCrud() = runBlocking {
        val gameProfile = GameProfileEntity(
            id = "game_test_100",
            name = "Test FPS Mobile",
            packageName = "com.test.fps",
            performanceMode = "BALANCED",
            fpsLock = 60,
            isInstalled = true,
            isInLibrary = true,
            isFavorite = true,
            lastPlayedTimestamp = System.currentTimeMillis(),
            customNotes = "High priority profile",
            recommendedGraphics = "Smooth",
            recommendedFpsCap = "60 FPS",
            shadowSetting = "Disabled",
            antiAliasing = "Off",
            thermalAdvice = "Normal",
            batteryAdvice = "Normal",
            networkAdvice = "5GHz Wi-Fi",
            deviceLimitations = "RAM 4GB limit"
        )

        gameSpaceDao.insertGameProfile(gameProfile)
        var profiles = gameSpaceDao.getAllGameProfiles()
        assertEquals(1, profiles.size)
        assertEquals("Test FPS Mobile", profiles[0].name)

        gameSpaceDao.deleteGameProfile("game_test_100")
        profiles = gameSpaceDao.getAllGameProfiles()
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun testAdvisorRecommendationInsertAndDismiss() = runBlocking {
        val rec = AdvisorRecommendationEntity(
            id = "rec_db_test_1",
            ruleId = PerformanceAdvisorEngine.RULE_HIGH_BATTERY_TEMP,
            title = "High Battery Temp",
            measuredFact = "42°C measured",
            possibleCausesAndOutcomes = "Heat accumulation",
            suggestedAction = "Allow phone to cool",
            severity = "WARNING",
            timestamp = System.currentTimeMillis(),
            isDismissed = false
        )

        gameSpaceDao.insertAdvisorRecommendation(rec)
        var recs = gameSpaceDao.getAllAdvisorRecommendations()
        assertEquals(1, recs.size)
        assertFalse(recs[0].isDismissed)

        gameSpaceDao.dismissRecommendationByRuleId(PerformanceAdvisorEngine.RULE_HIGH_BATTERY_TEMP)
        recs = gameSpaceDao.getAllAdvisorRecommendations()
        assertEquals(1, recs.size)
        assertTrue(recs[0].isDismissed)

        gameSpaceDao.clearAdvisorRecommendations()
        recs = gameSpaceDao.getAllAdvisorRecommendations()
        assertTrue(recs.isEmpty())
    }

    @Test
    fun testDatabaseMigrationsExist() {
        assertNotNull(MIGRATION_1_2)
        assertNotNull(MIGRATION_2_3)
        assertNotNull(MIGRATION_3_4)
        assertNotNull(MIGRATION_4_5)
        assertNotNull(MIGRATION_5_6)
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
    }

    @Test
    fun testFullMigrationChainPreservesUserData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        // 1. Create a raw SQLite database mimicking Schema Version 1
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("test_migration.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `sensitivity_profiles` (
                                    `id` TEXT NOT NULL,
                                    `profileName` TEXT NOT NULL,
                                    `gameName` TEXT NOT NULL,
                                    `sensitivityStyle` TEXT NOT NULL,
                                    `dpiTier` INTEGER NOT NULL,
                                    `general` INTEGER NOT NULL,
                                    `redDot` INTEGER NOT NULL,
                                    `scope2x` INTEGER NOT NULL,
                                    `scope4x` INTEGER NOT NULL,
                                    `sniper` INTEGER NOT NULL,
                                    `freeLook` INTEGER NOT NULL,
                                    `mathematicalRationale` TEXT NOT NULL,
                                    PRIMARY KEY(`id`)
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `rotation_calibrations` (
                                    `id` TEXT NOT NULL,
                                    `timestamp` INTEGER NOT NULL,
                                    `swipeDistancePx` REAL NOT NULL,
                                    `durationMs` INTEGER NOT NULL,
                                    `avgSpeedPxPerSec` REAL NOT NULL,
                                    `consistencyScore` REAL NOT NULL,
                                    `recommendedStyle` TEXT NOT NULL,
                                    `recommendedGeneralSensitivity` INTEGER NOT NULL,
                                    `advice` TEXT NOT NULL,
                                    PRIMARY KEY(`id`)
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `aim_training_sessions` (
                                    `id` TEXT NOT NULL,
                                    `timestamp` INTEGER NOT NULL,
                                    `durationSeconds` INTEGER NOT NULL,
                                    `difficulty` TEXT NOT NULL,
                                    `targetSize` TEXT NOT NULL,
                                    `targetMovement` TEXT NOT NULL,
                                    `targetsSpawned` INTEGER NOT NULL,
                                    `targetsHit` INTEGER NOT NULL,
                                    `accuracyPercent` REAL NOT NULL,
                                    `avgReactionTimeMs` INTEGER NOT NULL,
                                    `bestStreak` INTEGER NOT NULL,
                                    `missCount` INTEGER NOT NULL,
                                    `score` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`)
                                )
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )

        val v1Db = helper.writableDatabase
        v1Db.execSQL(
            """
            INSERT INTO sensitivity_profiles VALUES (
                'prof_v1_001', 'Competitive Apex', 'Apex Mobile', 'Aggressive', 480, 85, 75, 70, 60, 50, 65, 'Mathematical baseline v1'
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            """
            INSERT INTO rotation_calibrations VALUES (
                'cal_v1_001', 1700000000000, 380.0, 350, 1085.0, 88.5, 'Balanced Rotation', 75, 'Calibrated on v1'
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            """
            INSERT INTO aim_training_sessions VALUES (
                'aim_v1_001', 1700000000100, 30, 'Medium', 'Medium', 'Moving (Drift)', 15, 13, 86.6, 280, 7, 2, 650
            )
            """.trimIndent()
        )
        v1Db.close()

        // 2. Open via Room migrating 1 -> 2 -> 3 -> 4 -> 5 -> 6
        val upgradedDb = Room.databaseBuilder(context, GameBoostDatabase::class.java, "test_migration.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        // Verify V1 records survived intact
        val aimDaoMigrated = upgradedDb.aimIntelligenceDao()
        val profiles = runBlocking { aimDaoMigrated.getAllProfiles() }
        assertEquals(1, profiles.size)
        assertEquals("prof_v1_001", profiles[0].id)
        assertEquals("Competitive Apex", profiles[0].profileName)

        val calibrations = runBlocking { aimDaoMigrated.getAllCalibrations() }
        assertEquals(1, calibrations.size)
        assertEquals("cal_v1_001", calibrations[0].id)
        assertEquals(1085.0f, calibrations[0].avgSpeedPxPerSec, 0.1f)

        val sessions = runBlocking { aimDaoMigrated.getAllTrainingSessions() }
        assertEquals(1, sessions.size)
        assertEquals("aim_v1_001", sessions[0].id)
        assertEquals(13, sessions[0].targetsHit)

        // Verify new tables created in MIGRATION_1_2 (game_profiles, session_reports)
        val gameSpaceDaoMigrated = upgradedDb.gameSpaceDao()
        runBlocking {
            gameSpaceDaoMigrated.insertGameProfile(
                GameProfileEntity(
                    id = "migrated_game_1",
                    name = "Battle Royale Pro",
                    packageName = "com.br.pro",
                    performanceMode = "PERFORMANCE_ASSISTANT",
                    fpsLock = 60,
                    isInstalled = true,
                    isInLibrary = true,
                    isFavorite = true,
                    lastPlayedTimestamp = 1700000050000,
                    customNotes = "Migrated from test",
                    recommendedGraphics = "Smooth",
                    recommendedFpsCap = "60 FPS",
                    shadowSetting = "Off",
                    antiAliasing = "Off",
                    thermalAdvice = "Normal",
                    batteryAdvice = "Normal",
                    networkAdvice = "5GHz",
                    deviceLimitations = "None"
                )
            )
            val allGames = gameSpaceDaoMigrated.getAllGameProfiles()
            assertEquals(1, allGames.size)
            assertEquals("Battle Royale Pro", allGames[0].name)
        }

        // Verify new tables created in MIGRATION_2_3 (benchmark_lab_results)
        runBlocking {
            gameSpaceDaoMigrated.insertBenchmarkLabResult(
                BenchmarkLabResultEntity(
                    id = "bench_migrated_1",
                    timestamp = 1700000060000,
                    deviceModel = "Pixel 8",
                    androidVersion = "Android 14",
                    workloadType = "CPU Floating-Point & Math Workload",
                    durationMs = 3000,
                    scoreOrOpsPerSec = 450000.0,
                    startBatteryTempC = 32.0f,
                    peakBatteryTempC = 33.5f,
                    startThermalStatus = "Normal",
                    peakThermalStatus = "Normal",
                    detailsSummary = "Lab test migration verification",
                    wasAbortedDueToThermal = false
                )
            )
            val allLab = gameSpaceDaoMigrated.getAllBenchmarkLabResults()
            assertEquals(1, allLab.size)
            assertEquals("Pixel 8", allLab[0].deviceModel)
        }

        // Verify new tables created in MIGRATION_3_4 (advisor_recommendations)
        runBlocking {
            gameSpaceDaoMigrated.insertAdvisorRecommendation(
                AdvisorRecommendationEntity(
                    id = "adv_migrated_1",
                    ruleId = "rule_high_battery_temp",
                    title = "Battery Heat Alert",
                    measuredFact = "41.5°C measured",
                    possibleCausesAndOutcomes = "Sustained gaming load",
                    suggestedAction = "Remove case and cool",
                    severity = "WARNING",
                    timestamp = 1700000070000,
                    isDismissed = false
                )
            )
            val allAdv = gameSpaceDaoMigrated.getAllAdvisorRecommendations()
            assertEquals(1, allAdv.size)
            assertEquals("rule_high_battery_temp", allAdv[0].ruleId)
        }

        // Verify new tables created in MIGRATION_4_5 (saved_system_state)
        val deviceControlDaoMigrated = upgradedDb.deviceControlDao()
        runBlocking {
            deviceControlDaoMigrated.saveSystemState(
                SavedSystemStateEntity(
                    id = "original_state",
                    timestamp = 1700000080000,
                    originalBrightnessMode = 1,
                    originalBrightnessLevel = 200,
                    originalScreenTimeoutMs = 120000,
                    originalAutoRotate = 1,
                    originalDndFilter = 1,
                    appliedProfileName = "Smoothness Focus Profile",
                    lastAppliedTimestamp = 1700000080000
                )
            )
            val state = deviceControlDaoMigrated.getSavedSystemState()
            assertNotNull(state)
            assertEquals("Smoothness Focus Profile", state?.appliedProfileName)
        }

        // Verify new network_history table and v6 columns created in MIGRATION_5_6
        val netDaoMigrated = upgradedDb.networkHistoryDao()
        runBlocking {
            netDaoMigrated.insertHistory(
                NetworkHistoryEntity(
                    id = "net_v6_1",
                    timestamp = 1700000090000,
                    connectionType = "Wi-Fi",
                    avgPingMs = 28,
                    jitterMs = 4,
                    packetLossPercent = 0,
                    downloadMbps = 85.4f,
                    uploadMbps = 32.1f,
                    rating = "Excellent"
                )
            )
            val records = netDaoMigrated.getHistory()
            assertEquals(1, records.size)
            assertEquals("Wi-Fi", records[0].connectionType)
            assertEquals(28, records[0].avgPingMs)

            val gamesAfterV6 = gameSpaceDaoMigrated.getAllGameProfiles()
            assertEquals(1, gamesAfterV6.size)
            assertNull(gamesAfterV6[0].previousMode)
            assertEquals(60, gamesAfterV6[0].previousFpsLock)
        }

        upgradedDb.close()
        dbFile.delete()
    }

    @Test
    fun testSnapshotSerializationRoundTrip() {
        val original = listOf(
            TelemetrySnapshot(10, 35.2f, "Normal", 3.1f, 32, 5, 120f),
            TelemetrySnapshot(20, 37.8f, "Light", 2.9f, 38, 9, 120f)
        )
        val encoded = BenchmarkSessionReport.serializeSnapshots(original)
        val decoded = BenchmarkSessionReport.deserializeSnapshots(encoded)
        assertEquals(2, decoded.size)
        assertEquals(10, decoded[0].elapsedSeconds)
        assertEquals(35.2f, decoded[0].batteryTempC, 0.01f)
        assertEquals("Light", decoded[1].thermalStatus)
        assertEquals(120f, decoded[1].refreshRateHz, 0.01f)
    }
}
