package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameSpaceAndProfileTest {

    @Test
    fun testGameProfileRoomPersistenceAndCrud() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = GameSpaceRepository(context)

        repository.initializeDefaultsIfNeeded()
        val initialProfiles = repository.profilesFlow.first()
        assertTrue(initialProfiles.isNotEmpty())

        // 1. Profile Creation / Save
        val customProfile = GameProfile(
            id = "test_custom_1",
            name = "Test Combat Game",
            packageName = "com.test.combat",
            performanceMode = PerformanceMode.BALANCED,
            fpsLock = 60,
            isInLibrary = true,
            isFavorite = false,
            customNotes = "Custom test notes for user-customized profile."
        )
        repository.saveProfile(customProfile)

        var profiles = repository.profilesFlow.first()
        val found = profiles.find { it.id == "test_custom_1" }
        assertNotNull(found)
        assertEquals("Test Combat Game", found?.name)
        assertEquals(60, found?.fpsLock)

        // 2. Profile Editing
        val edited = found!!.copy(fpsLock = 90, customNotes = "Edited custom notes.")
        repository.saveProfile(edited)

        profiles = repository.profilesFlow.first()
        val updated = profiles.find { it.id == "test_custom_1" }
        assertEquals(90, updated?.fpsLock)
        assertEquals("Edited custom notes.", updated?.customNotes)

        // 3. Profile Duplication
        repository.duplicateProfile(edited)
        profiles = repository.profilesFlow.first()
        val copyProfile = profiles.find { it.name.contains("Test Combat Game (Copy)") }
        assertNotNull(copyProfile)
        assertEquals(90, copyProfile?.fpsLock)

        // 4. Profile Deletion
        repository.deleteProfile("test_custom_1")
        profiles = repository.profilesFlow.first()
        assertNull(profiles.find { it.id == "test_custom_1" })
    }

    @Test
    fun testSessionRecoveryAndPersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = GameSpaceRepository(context)

        repository.initializeDefaultsIfNeeded()

        val testReport = BenchmarkSessionReport(
            id = "test_session_999",
            sessionLabel = "Unit Test Session",
            gameTitle = "Test Combat Game",
            graphicsSettings = "High Preset",
            durationSeconds = 120,
            startTempC = 31.0f,
            peakTempC = 37.5f,
            startBatteryLevel = 80,
            endBatteryLevel = 76,
            startThermalStatus = "Normal",
            peakThermalStatus = "Normal",
            avgAvailRamGb = 2.5f,
            minAvailRamGb = 2.1f,
            avgPingMs = 35,
            avgJitterMs = 5,
            displayRefreshRateHz = 120f,
            fpsMeasurementSource = "OEM Overlay",
            measuredAvgFps = 58.5f,
            measuredMinFps = 52.0f,
            measuredFrameTimeMs = 17.1f,
            throttlingObserved = false,
            testNotes = "Testing session persistence in Room.",
            conclusion = "Session recovered successfully."
        )

        repository.saveSessionReport(testReport)

        val sessions = repository.sessionsFlow.first()
        val recovered = sessions.find { it.id == "test_session_999" }
        assertNotNull(recovered)
        assertEquals("Unit Test Session", recovered?.sessionLabel)
        assertEquals(120, recovered?.durationSeconds)
        assertEquals(37.5f, recovered?.peakTempC ?: 0f, 0.01f)
        assertEquals(58.5f, recovered?.measuredAvgFps ?: 0f, 0.1f)
    }
}
