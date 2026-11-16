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
class DeviceControlEngineTest {

    private lateinit var database: GameBoostDatabase
    private lateinit var deviceControlDao: DeviceControlDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameBoostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceControlDao = database.deviceControlDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDetectCapabilitiesReturnsValidData() {
        val caps = DeviceControlEngine.detectCapabilities(context)
        assertNotNull(caps.androidVersion)
        assertTrue(caps.sdkInt > 0)
        assertNotNull(caps.manufacturer)
        assertNotNull(caps.model)
        assertTrue(caps.currentRefreshRateHz > 0f)
        assertTrue(caps.totalRamGb >= 0f)
    }

    @Test
    fun testProfileDefinitionsAreValid() {
        val balanced = DeviceControlEngine.getProfileDefinition(ProfileType.BALANCED)
        assertEquals("Balanced Profile", balanced.displayName)
        assertTrue(balanced.appliedAuthorizedSettings.isNotEmpty())
        assertTrue(balanced.manualRecommendations.isNotEmpty())

        val smoothness = DeviceControlEngine.getProfileDefinition(ProfileType.SMOOTHNESS_FOCUS)
        assertEquals("Smoothness Focus Profile", smoothness.displayName)
        assertTrue(smoothness.enableWifiLock)
        assertEquals(0, smoothness.targetAutoRotate) // Auto-rotate LOCKED

        val battery = DeviceControlEngine.getProfileDefinition(ProfileType.BATTERY_FRIENDLY)
        assertEquals("Battery Friendly Profile", battery.displayName)
        assertFalse(battery.enableWifiLock)
        assertEquals(60000, battery.targetTimeoutMs) // 1 minute
    }

    @Test
    fun testSavedSystemStateEntityRoomStorage() = runBlocking {
        val initialState = SavedSystemStateEntity(
            id = "original_state",
            timestamp = System.currentTimeMillis(),
            originalBrightnessMode = 1,
            originalBrightnessLevel = 180,
            originalScreenTimeoutMs = 120000,
            originalAutoRotate = 1,
            originalDndFilter = 1,
            appliedProfileName = "Smoothness Focus Profile",
            lastAppliedTimestamp = System.currentTimeMillis()
        )

        deviceControlDao.saveSystemState(initialState)
        var saved = deviceControlDao.getSavedSystemState()
        assertNotNull(saved)
        assertEquals("Smoothness Focus Profile", saved?.appliedProfileName)
        assertEquals(180, saved?.originalBrightnessLevel)

        deviceControlDao.clearSavedSystemState()
        saved = deviceControlDao.getSavedSystemState()
        assertNull(saved)
    }

    @Test
    fun testRestoreOriginalSettingsWhenEmpty() = runBlocking {
        val success = DeviceControlEngine.restoreOriginalSettings(context, database)
        assertFalse(success)
        val detailed = DeviceControlEngine.restoreOriginalSettingsDetailed(context, database)
        assertFalse(detailed.restoredAny)
        assertFalse(detailed.clearedBaseline)
    }

    @Test
    fun testAutopilotProfileTitlesAndGameModeMapping() {
        assertEquals("Maximum Stability", ProfileType.SMOOTHNESS_FOCUS.autopilotTitle)
        assertEquals("Balanced Gaming", ProfileType.BALANCED.autopilotTitle)
        assertEquals("Battery Friendly", ProfileType.BATTERY_FRIENDLY.autopilotTitle)

        assertEquals(
            ProfileType.SMOOTHNESS_FOCUS,
            DeviceControlEngine.mapGamePerformanceModeToProfile(PerformanceMode.PERFORMANCE_ASSISTANT)
        )
        assertEquals(
            ProfileType.BALANCED,
            DeviceControlEngine.mapGamePerformanceModeToProfile(PerformanceMode.BALANCED)
        )
        assertEquals(
            ProfileType.BATTERY_FRIENDLY,
            DeviceControlEngine.mapGamePerformanceModeToProfile(PerformanceMode.BATTERY_SAVER)
        )
        assertEquals(
            ProfileType.BATTERY_FRIENDLY,
            DeviceControlEngine.mapGamePerformanceModeToProfile(PerformanceMode.THERMAL_PROTECTION)
        )

        assertEquals(
            PerformanceMode.PERFORMANCE_ASSISTANT,
            DeviceControlEngine.mapProfileToGamePerformanceMode(ProfileType.SMOOTHNESS_FOCUS)
        )
        assertEquals(
            PerformanceMode.BALANCED,
            DeviceControlEngine.mapProfileToGamePerformanceMode(ProfileType.BALANCED)
        )
        assertEquals(
            PerformanceMode.BATTERY_SAVER,
            DeviceControlEngine.mapProfileToGamePerformanceMode(ProfileType.BATTERY_FRIENDLY)
        )
    }

    @Test
    fun testApplyProfilePreservesExistingValidBaselineAndRetainsWhenPermissionsMissing() = runBlocking {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        org.robolectric.Shadows.shadowOf(appOps).setMode(
            android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
            android.os.Process.myUid(),
            context.packageName,
            android.app.AppOpsManager.MODE_IGNORED
        )
        // Pre-populate a valid baseline recorded while permissions were active
        val priorBaseline = SavedSystemStateEntity(
            id = "original_state",
            timestamp = 1000L,
            originalBrightnessMode = 1,
            originalBrightnessLevel = 160,
            originalScreenTimeoutMs = 120000,
            originalAutoRotate = 1,
            originalDndFilter = 1,
            appliedProfileName = "Balanced Profile",
            lastAppliedTimestamp = 1000L
        )
        deviceControlDao.saveSystemState(priorBaseline)

        val missingPerms = mutableListOf<String>()
        DeviceControlEngine.applyProfile(
            context = context,
            profileType = ProfileType.SMOOTHNESS_FOCUS,
            database = database,
            onPermissionRequired = { perm -> missingPerms.add(perm) }
        )
        assertTrue(missingPerms.contains("android.permission.WRITE_SETTINGS"))

        // Prior valid baseline values must NOT be overwritten when switching profiles
        val afterApply = deviceControlDao.getSavedSystemState()
        assertNotNull(afterApply)
        assertEquals(120000, afterApply?.originalScreenTimeoutMs)
        assertEquals(160, afterApply?.originalBrightnessLevel)
        assertEquals("Smoothness Focus Profile", afterApply?.appliedProfileName)

        // When WRITE_SETTINGS is revoked, restore preserves WRITE_SETTINGS baseline intact rather than discarding it
        val restoreResult = DeviceControlEngine.restoreOriginalSettingsDetailed(context, database)
        assertFalse(restoreResult.clearedBaseline)
        val retained = deviceControlDao.getSavedSystemState()
        assertNotNull(retained)
        assertEquals(120000, retained?.originalScreenTimeoutMs)
    }

    @Test
    fun testRestoreRespectsManualUserModificationWhenAuthorized() = runBlocking {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val shadowAppOps = org.robolectric.Shadows.shadowOf(appOps)
        shadowAppOps.setMode(
            android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
            android.os.Process.myUid(),
            context.packageName,
            android.app.AppOpsManager.MODE_ALLOWED
        )
        try {
            val cr = context.contentResolver
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 60000)
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 1)

            DeviceControlEngine.applyProfile(context, ProfileType.SMOOTHNESS_FOCUS, database) {}
            assertEquals(1800000, android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT))
            assertEquals(0, android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION))

            // Simulate user manually changing screen timeout mid-session to 300000ms (5 min)
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 300000)

            val result = DeviceControlEngine.restoreOriginalSettingsDetailed(context, database)
            assertTrue(result.clearedBaseline)
            assertTrue(result.skippedUserModified.contains("Screen Timeout"))
            assertTrue(result.restoredSettings.contains("Auto-Rotate"))
            // Manual user timeout (300000ms) must NOT be overwritten back to 60000ms
            assertEquals(300000, android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT))
            // Unmodified Auto-Rotate IS restored back to 1
            assertEquals(1, android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION))
        } finally {
            shadowAppOps.setMode(
                android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
                android.os.Process.myUid(),
                context.packageName,
                android.app.AppOpsManager.MODE_IGNORED
            )
        }
    }
}


