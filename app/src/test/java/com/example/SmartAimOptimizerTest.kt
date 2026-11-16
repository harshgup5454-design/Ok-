package com.example

import com.example.data.AimOptimizationMode
import com.example.data.SmartAimOptimizerEngine
import org.junit.Assert.*
import org.junit.Test

class SmartAimOptimizerTest {

    @Test
    fun testModePresetsAndCalculationFormulas() {
        // 1. Accuracy Mode Preset for Free Fire MAX
        val accuracyPreset = SmartAimOptimizerEngine.generatePresetProfile("Free Fire MAX", AimOptimizationMode.ACCURACY, 400)
        assertEquals("Free Fire MAX", accuracyPreset.gameName)
        assertEquals(AimOptimizationMode.ACCURACY.displayName, accuracyPreset.sensitivityStyle)
        assertTrue("Accuracy general sensitivity must be positive", accuracyPreset.general > 0)
        assertTrue("Sniper sensitivity should be lower than general", accuracyPreset.sniper < accuracyPreset.general)
        assertTrue(accuracyPreset.mathematicalRationale.contains("Accuracy Focus Formula"))

        // 2. Fast Rotation Mode Preset for Free Fire MAX
        val fastRotPreset = SmartAimOptimizerEngine.generatePresetProfile("Free Fire MAX", AimOptimizationMode.FAST_ROTATION, 400)
        assertEquals("Free Fire MAX", fastRotPreset.gameName)
        assertEquals(AimOptimizationMode.FAST_ROTATION.displayName, fastRotPreset.sensitivityStyle)
        assertTrue("Fast rotation general sensitivity should exceed accuracy sensitivity", fastRotPreset.general > accuracyPreset.general)
        assertTrue(fastRotPreset.mathematicalRationale.contains("Fast Rotation Formula"))

        // 3. Balanced Mode Preset for Free Fire MAX
        val balancedPreset = SmartAimOptimizerEngine.generatePresetProfile("Free Fire MAX", AimOptimizationMode.BALANCED, 400)
        assertEquals(AimOptimizationMode.BALANCED.displayName, balancedPreset.sensitivityStyle)
        assertTrue(balancedPreset.general in (accuracyPreset.general..fastRotPreset.general))

        // 4. Test other supported games (BGMI / PUBG Mobile, COD Mobile)
        val bgmiPreset = SmartAimOptimizerEngine.generatePresetProfile("BGMI / PUBG Mobile", AimOptimizationMode.ACCURACY, 400)
        assertEquals("BGMI / PUBG Mobile", bgmiPreset.gameName)
        assertTrue(bgmiPreset.general in 20..160)
    }

    @Test
    fun testMasterSwitchAndControlState() {
        SmartAimOptimizerEngine.setMasterEnabled(true)
        assertTrue(SmartAimOptimizerEngine.controlState.value.isMasterEnabled)
        assertEquals("Smart Aim & Rotation Optimizer Activated", SmartAimOptimizerEngine.controlState.value.autoAppliedNotification)

        SmartAimOptimizerEngine.setOptimizationMode(AimOptimizationMode.FAST_ROTATION)
        assertEquals(AimOptimizationMode.FAST_ROTATION, SmartAimOptimizerEngine.controlState.value.activeMode)

        SmartAimOptimizerEngine.setSelectedGame("Free Fire MAX")
        assertEquals("Free Fire MAX", SmartAimOptimizerEngine.controlState.value.selectedGame)

        SmartAimOptimizerEngine.setMasterEnabled(false)
        assertFalse(SmartAimOptimizerEngine.controlState.value.isMasterEnabled)
        assertEquals("Optimizer Disengaged", SmartAimOptimizerEngine.controlState.value.autoAppliedNotification)
    }

    @Test
    fun testSessionFeedbackRecording() {
        SmartAimOptimizerEngine.recordSessionFeedback("Free Fire MAX", AimOptimizationMode.FAST_ROTATION, "Excellent drag shot responsiveness.")
        val feedback = SmartAimOptimizerEngine.controlState.value.lastSessionFeedback
        assertTrue(feedback.contains("Free Fire MAX"))
        assertTrue(feedback.contains("Fast Rotation"))
        assertTrue(feedback.contains("Excellent drag shot responsiveness."))
    }
}
