package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceAdvisorTest {

    private fun createBaseDeviceStats(): DeviceStats {
        return DeviceStats(
            totalMemoryGb = 8.0f,
            availableMemoryGb = 4.0f,
            memoryUsagePercent = 50,
            isLowMemory = false,
            memoryThresholdGb = 0.5f,
            batteryLevelPercent = 80,
            isCharging = false,
            batteryTemperatureC = 30.0f,
            totalStorageGb = 128.0f,
            availableStorageGb = 64.0f,
            storageUsagePercent = 50,
            powerManagerThermalStatus = "Normal",
            powerManagerThermalStatusCode = 0,
            isThermalStatusSupported = true,
            displayRefreshRate = 60f
        )
    }

    private fun createBaseNetworkStats(): NetworkStats {
        return NetworkStats(
            isConnected = true,
            connectionType = "Wi-Fi",
            pingMs = 25,
            jitterMs = 4,
            packetLossPercent = 0,
            timeoutCount = 0,
            stabilityRating = "Excellent"
        )
    }

    @Test
    fun testRule1_ThermalThrottlingTriggerAndBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // Normal level 0 -> not triggered
        val recsLevel0 = PerformanceAdvisorEngine.evaluateAllRules(baseStats, netStats, fpsAvailable = true)
        assertNull(recsLevel0.find { it.ruleId == PerformanceAdvisorEngine.RULE_THERMAL_THROTTLE })

        // Level 2 (MODERATE) -> triggered
        val statsLevel2 = baseStats.copy(powerManagerThermalStatusCode = 2, powerManagerThermalStatus = "Moderate")
        val recsLevel2 = PerformanceAdvisorEngine.evaluateAllRules(statsLevel2, netStats, fpsAvailable = true)
        val rec = recsLevel2.find { it.ruleId == PerformanceAdvisorEngine.RULE_THERMAL_THROTTLE }
        assertNotNull(rec)
        assertEquals(RuleSeverity.CRITICAL, rec?.severity)
        assertTrue(rec!!.measuredFact.contains("currentThermalStatus = Moderate"))
        assertTrue(rec.possibleCausesAndOutcomes.contains("DVFS"))
        assertTrue(rec.suggestedAction.contains("Lower in-game graphic shader resolution"))
    }

    @Test
    fun testRule2_ChargingHighBatteryTempBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // 39.4°C while charging -> not triggered
        val stats394 = baseStats.copy(isCharging = true, batteryTemperatureC = 39.4f)
        val recs394 = PerformanceAdvisorEngine.evaluateAllRules(stats394, netStats, fpsAvailable = true)
        assertNull(recs394.find { it.ruleId == PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP })

        // 39.5°C while charging -> triggered
        val stats395 = baseStats.copy(isCharging = true, batteryTemperatureC = 39.5f)
        val recs395 = PerformanceAdvisorEngine.evaluateAllRules(stats395, netStats, fpsAvailable = true)
        val rec = recs395.find { it.ruleId == PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP }
        assertNotNull(rec)
        assertEquals(RuleSeverity.WARNING, rec?.severity)
        assertTrue(rec!!.measuredFact.contains("39.5°C"))
        assertTrue(rec.possibleCausesAndOutcomes.contains("3W–5W"))
    }

    @Test
    fun testRule3_ElevatedBatteryTempOnBatteryBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // 40.9°C off charger -> not triggered
        val stats409 = baseStats.copy(isCharging = false, batteryTemperatureC = 40.9f)
        val recs409 = PerformanceAdvisorEngine.evaluateAllRules(stats409, netStats, fpsAvailable = true)
        assertNull(recs409.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_BATTERY_TEMP })

        // 41.0°C off charger -> triggered
        val stats410 = baseStats.copy(isCharging = false, batteryTemperatureC = 41.0f)
        val recs410 = PerformanceAdvisorEngine.evaluateAllRules(stats410, netStats, fpsAvailable = true)
        val rec = recs410.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_BATTERY_TEMP }
        assertNotNull(rec)
        assertTrue(rec!!.measuredFact.contains("41.0°C"))
        assertTrue(rec.suggestedAction.contains("Remove thick protective cases"))
    }

    @Test
    fun testRule4_HighMemoryPressureBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // 87% memory usage, 1.2GB free -> not triggered
        val statsNormal = baseStats.copy(memoryUsagePercent = 87, availableMemoryGb = 1.2f, isLowMemory = false)
        val recsNormal = PerformanceAdvisorEngine.evaluateAllRules(statsNormal, netStats, fpsAvailable = true)
        assertNull(recsNormal.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_MEMORY_PRESSURE })

        // 88% memory usage, 0.8GB free -> triggered
        val statsHigh = baseStats.copy(memoryUsagePercent = 88, availableMemoryGb = 0.8f, isLowMemory = false)
        val recsHigh = PerformanceAdvisorEngine.evaluateAllRules(statsHigh, netStats, fpsAvailable = true)
        val rec = recsHigh.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_MEMORY_PRESSURE }
        assertNotNull(rec)
        assertTrue(rec!!.measuredFact.contains("88%"))
        assertTrue(rec.possibleCausesAndOutcomes.contains("LMK"))
        assertTrue(rec.suggestedAction.contains("GAMEBOOST does not automatically kill background processes"))
    }

    @Test
    fun testRule5_LowStorageBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // 91% storage, 4.0GB free -> not triggered
        val stats91 = baseStats.copy(storageUsagePercent = 91, availableStorageGb = 4.0f)
        val recs91 = PerformanceAdvisorEngine.evaluateAllRules(stats91, netStats, fpsAvailable = true)
        assertNull(recs91.find { it.ruleId == PerformanceAdvisorEngine.RULE_LOW_STORAGE })

        // 92% storage -> triggered
        val stats92 = baseStats.copy(storageUsagePercent = 92, availableStorageGb = 2.5f)
        val recs92 = PerformanceAdvisorEngine.evaluateAllRules(stats92, netStats, fpsAvailable = true)
        val rec = recs92.find { it.ruleId == PerformanceAdvisorEngine.RULE_LOW_STORAGE }
        assertNotNull(rec)
        assertTrue(rec!!.measuredFact.contains("92%"))
        assertTrue(rec.suggestedAction.contains("GAMEBOOST never deletes user files automatically"))
    }

    @Test
    fun testRule6_UnstableNetworkJitterBoundary() {
        val baseStats = createBaseDeviceStats()

        // Jitter 24ms, packetLoss 0 -> not triggered
        val net24 = createBaseNetworkStats().copy(jitterMs = 24, packetLossPercent = 0, timeoutCount = 0)
        val recs24 = PerformanceAdvisorEngine.evaluateAllRules(baseStats, net24, fpsAvailable = true)
        assertNull(recs24.find { it.ruleId == PerformanceAdvisorEngine.RULE_UNSTABLE_NETWORK_JITTER })

        // Jitter 26ms -> triggered
        val net26 = createBaseNetworkStats().copy(jitterMs = 26, packetLossPercent = 0, timeoutCount = 0)
        val recs26 = PerformanceAdvisorEngine.evaluateAllRules(baseStats, net26, fpsAvailable = true)
        val rec = recs26.find { it.ruleId == PerformanceAdvisorEngine.RULE_UNSTABLE_NETWORK_JITTER }
        assertNotNull(rec)
        assertTrue(rec!!.measuredFact.contains("26ms"))
        assertTrue(rec.possibleCausesAndOutcomes.contains("NOT the GPU rendering frame rate"))
    }

    @Test
    fun testRule7_HighRefreshRatePowerTradeoff() {
        val baseStats = createBaseDeviceStats().copy(displayRefreshRate = 120f, batteryLevelPercent = 20, batteryTemperatureC = 39.0f)
        val netStats = createBaseNetworkStats()

        val recs = PerformanceAdvisorEngine.evaluateAllRules(baseStats, netStats, fpsAvailable = true)
        val rec = recs.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_REFRESH_RATE_POWER }
        assertNotNull(rec)
        assertEquals(RuleSeverity.INFO, rec?.severity)
        assertTrue(rec!!.measuredFact.contains("120 Hz"))
    }

    @Test
    fun testRule8_FpsUnavailableSandboxLimitation() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // fpsAvailable = false -> triggered
        val recs = PerformanceAdvisorEngine.evaluateAllRules(baseStats, netStats, fpsAvailable = false)
        val rec = recs.find { it.ruleId == PerformanceAdvisorEngine.RULE_FPS_UNAVAILABLE_SANDBOX }
        assertNotNull(rec)
        assertEquals(RuleSeverity.INFO, rec?.severity)
        assertTrue(rec!!.measuredFact.contains("Unavailable"))
        assertTrue(rec.possibleCausesAndOutcomes.contains("SurfaceFlinger"))
    }

    @Test
    fun testRule9_LowBatteryGamingBoundary() {
        val baseStats = createBaseDeviceStats()
        val netStats = createBaseNetworkStats()

        // 16% battery -> not triggered
        val stats16 = baseStats.copy(isCharging = false, batteryLevelPercent = 16)
        val recs16 = PerformanceAdvisorEngine.evaluateAllRules(stats16, netStats, fpsAvailable = true)
        assertNull(recs16.find { it.ruleId == PerformanceAdvisorEngine.RULE_LOW_BATTERY_GAMING })

        // 15% battery -> triggered
        val stats15 = baseStats.copy(isCharging = false, batteryLevelPercent = 15)
        val recs15 = PerformanceAdvisorEngine.evaluateAllRules(stats15, netStats, fpsAvailable = true)
        val rec = recs15.find { it.ruleId == PerformanceAdvisorEngine.RULE_LOW_BATTERY_GAMING }
        assertNotNull(rec)
        assertTrue(rec!!.measuredFact.contains("15%"))
    }

    @Test
    fun testDismissalSuppression() {
        val baseStats = createBaseDeviceStats().copy(isCharging = true, batteryTemperatureC = 40.0f)
        val netStats = createBaseNetworkStats()

        // Without dismissal -> rule triggered
        val activeRecs = PerformanceAdvisorEngine.evaluateAllRules(baseStats, netStats, fpsAvailable = true)
        assertTrue(activeRecs.any { it.ruleId == PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP })

        // With rule ID in dismissed set -> rule suppressed
        val dismissedSet = setOf(PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP)
        val suppressedRecs = PerformanceAdvisorEngine.evaluateAllRules(baseStats, netStats, fpsAvailable = true, dismissedRuleIds = dismissedSet)
        assertFalse(suppressedRecs.any { it.ruleId == PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP })
    }

    @Test
    fun testAdvisorEntityConversion() {
        val rec = AdvisorRecommendation(
            id = "rec_test_123",
            ruleId = PerformanceAdvisorEngine.RULE_LOW_STORAGE,
            title = "Storage Low",
            measuredFact = "Storage 95% full",
            possibleCausesAndOutcomes = "Less scratch space",
            suggestedAction = "Review settings",
            severity = RuleSeverity.WARNING,
            timestamp = 123456789L,
            isDismissed = false
        )

        val entity = rec.toEntity()
        assertEquals("rec_test_123", entity.id)
        assertEquals("RULE_LOW_STORAGE", entity.ruleId)
        assertEquals("WARNING", entity.severity)

        val convertedBack = AdvisorRecommendation.fromEntity(entity)
        assertEquals(rec.id, convertedBack.id)
        assertEquals(rec.ruleId, convertedBack.ruleId)
        assertEquals(rec.severity, convertedBack.severity)
        assertEquals(rec.measuredFact, convertedBack.measuredFact)
    }

    @Test
    fun testUnavailableBatteryTemperatureProducesNAInReadinessAndComparison() {
        val statsNoTemp = createBaseDeviceStats().copy(batteryTemperatureC = 0f, isCharging = true)
        val netOfflinePing = createBaseNetworkStats().copy(pingMs = -1, jitterMs = 0)

        val readiness = PerformanceAdvisorEngine.evaluatePreGameReadiness(statsNoTemp, netOfflinePing)
        assertEquals("N/A", readiness.thermalSummary)
        val activeRecs = PerformanceAdvisorEngine.evaluateAllRules(statsNoTemp, netOfflinePing, fpsAvailable = false)
        assertNull(activeRecs.find { it.ruleId == PerformanceAdvisorEngine.RULE_CHARGING_HIGH_TEMP })
        assertNull(activeRecs.find { it.ruleId == PerformanceAdvisorEngine.RULE_HIGH_BATTERY_TEMP })

        val reportNoTemp = BenchmarkSessionReport(
            id = "session_1",
            sessionLabel = "Test",
            gameTitle = "Game",
            durationSeconds = 60,
            startTempC = 0f,
            peakTempC = 0f,
            startBatteryLevel = 80,
            endBatteryLevel = 79,
            startThermalStatus = "Normal",
            peakThermalStatus = "Normal",
            avgAvailRamGb = 4.0f,
            minAvailRamGb = 4.0f,
            avgPingMs = -1,
            avgJitterMs = 0,
            displayRefreshRateHz = 60f,
            measuredAvgFps = null,
            fpsMeasurementSource = "Local",
            throttlingObserved = false,
            conclusion = "Done"
        )
        val comparison = PerformanceAdvisorEngine.generateSessionComparisonReport(reportNoTemp)
        assertEquals("N/A", comparison.tempStartVsPeak)
        assertTrue(comparison.networkLatencySummary.contains("N/A"))
    }
}
