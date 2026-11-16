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
class AimIntelligenceTest {

    @Test
    fun testSensitivityCalculationAndDpiNormalization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AimIntelligenceRepository(context)

        val profile400 = AimIntelligenceRepository.calculateSensitivity("BGMI / PUBG Mobile", "Balanced", 400)
        val profile800 = AimIntelligenceRepository.calculateSensitivity("BGMI / PUBG Mobile", "Balanced", 800)

        // Higher DPI should result in lower sensitivity scalar to maintain physical thumb swipe arc
        assertTrue(profile800.general < profile400.general)
        assertEquals("BGMI / PUBG Mobile", profile800.gameName)
        assertNotNull(profile800.mathematicalRationale)
    }

    @Test
    fun testRoomProfilePersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AimIntelligenceRepository(context)

        val testProfile = SensitivityProfile(
            id = "test_profile_99",
            profileName = "Test Sniper Pro",
            gameName = "Call of Duty: Mobile",
            sensitivityStyle = "Low Sensitivity / Arm Swipes",
            dpiTier = 400,
            general = 50,
            redDot = 45,
            scope2x = 35,
            scope4x = 25,
            sniper = 15,
            freeLook = 90,
            mathematicalRationale = "Test rationale."
        )

        repo.saveProfile(testProfile)
        val profiles = repo.savedProfilesFlow.first()
        val found = profiles.find { it.id == "test_profile_99" }

        assertNotNull(found)
        assertEquals("Test Sniper Pro", found?.profileName)
        assertEquals(50, found?.general)

        repo.deleteProfile("test_profile_99")
    }

    @Test
    fun testRotationVelocityCalculation() {
        val distance = 350f
        val durationMs = 250L
        val speed = (distance / durationMs) * 1000f

        assertEquals(1400f, speed, 0.1f)
        assertTrue(speed > 1000f) // Classified as Fast Swipe / Flick
    }
}
