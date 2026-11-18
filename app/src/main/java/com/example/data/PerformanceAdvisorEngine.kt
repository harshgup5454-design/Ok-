package com.example.data

import kotlin.math.roundToInt

enum class RuleSeverity(val label: String, val colorHex: Long) {
    INFO("Information", 0xFF00FFCC),
    WARNING("Performance Risk", 0xFFF59E0B),
    CRITICAL("Critical System Condition", 0xFFEF4444)
}

data class AdvisorRecommendation(
    val id: String,
    val ruleId: String,
    val title: String,
    val measuredFact: String,
    val possibleCausesAndOutcomes: String,
    val suggestedAction: String,
    val severity: RuleSeverity,
    val timestamp: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
    val confidenceLabel: String = "[FACT: Measured Android Sensor]"
) {
    fun toEntity(): AdvisorRecommendationEntity = AdvisorRecommendationEntity(
        id = id,
        ruleId = ruleId,
        title = title,
        measuredFact = measuredFact,
        possibleCausesAndOutcomes = possibleCausesAndOutcomes,
        suggestedAction = suggestedAction,
        severity = severity.name,
        timestamp = timestamp,
        isDismissed = isDismissed
    )

    companion object {
        fun fromEntity(entity: AdvisorRecommendationEntity): AdvisorRecommendation = AdvisorRecommendation(
            id = entity.id,
            ruleId = entity.ruleId,
            title = entity.title,
            measuredFact = entity.measuredFact,
            possibleCausesAndOutcomes = entity.possibleCausesAndOutcomes,
            suggestedAction = entity.suggestedAction,
            severity = try { RuleSeverity.valueOf(entity.severity) } catch (_: Exception) { RuleSeverity.WARNING },
            timestamp = entity.timestamp,
            isDismissed = entity.isDismissed
        )
    }
}

data class PreGameCheckItem(
    val category: String,
    val title: String,
    val statusText: String,
    val isPassed: Boolean,
    val confidenceLabel: String
)

data class PreGameReadinessCheck(
    val scorePercent: Int,
    val readinessLabel: String,
    val ramSummary: String,
    val thermalSummary: String,
    val batterySummary: String,
    val networkSummary: String,
    val items: List<PreGameCheckItem>
)

data class GameSpecificGuide(
    val gameTitle: String,
    val recommendedGraphics: String,
    val targetFpsSetting: String,
    val shadowSetting: String,
    val antiAliasingSetting: String,
    val thermalStrategy: String,
    val batteryStrategy: String,
    val networkStrategy: String,
    val realisticLimitations: String,
    val confidenceLabel: String = "[FACT: Official In-Game Settings Guide]"
)

data class SessionComparisonDetails(
    val sessionLabel: String,
    val gameTitle: String,
    val durationFormatted: String,
    val batteryStartVsEnd: String,
    val batteryDrainPercent: Int,
    val tempStartVsPeak: String,
    val tempDeltaC: Float,
    val thermalStatusTransition: String,
    val throttlingObserved: Boolean,
    val ramUsageSummary: String,
    val networkLatencySummary: String,
    val evaluationVerdict: String,
    val confidenceTag: String = "[FACT: Recorded Session Telemetry]"
)

object PerformanceAdvisorEngine {

    const val RULE_THERMAL_THROTTLE = "RULE_THERMAL_THROTTLE"
    const val RULE_CHARGING_HIGH_TEMP = "RULE_CHARGING_HIGH_TEMP"
    const val RULE_HIGH_BATTERY_TEMP = "RULE_HIGH_BATTERY_TEMP"
    const val RULE_HIGH_MEMORY_PRESSURE = "RULE_HIGH_MEMORY_PRESSURE"
    const val RULE_LOW_STORAGE = "RULE_LOW_STORAGE"
    const val RULE_UNSTABLE_NETWORK_JITTER = "RULE_UNSTABLE_NETWORK_JITTER"
    const val RULE_HIGH_REFRESH_RATE_POWER = "RULE_HIGH_REFRESH_RATE_POWER"
    const val RULE_FPS_UNAVAILABLE_SANDBOX = "RULE_FPS_UNAVAILABLE_SANDBOX"
    const val RULE_LOW_BATTERY_GAMING = "RULE_LOW_BATTERY_GAMING"

    /**
     * Deterministic, rule-based performance evaluator.
     * Strictly offline, non-AI, transparent, and non-intrusive.
     */
    fun evaluateAllRules(
        stats: DeviceStats,
        netStats: NetworkStats,
        fpsAvailable: Boolean = false,
        dismissedRuleIds: Set<String> = emptySet()
    ): List<AdvisorRecommendation> {
        val list = mutableListOf<AdvisorRecommendation>()

        // 1. RULE: OS Thermal Throttling
        if (stats.isThermalStatusSupported && stats.powerManagerThermalStatusCode >= 2) {
            if (!dismissedRuleIds.contains(RULE_THERMAL_THROTTLE)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_THERMAL_THROTTLE}_${System.currentTimeMillis()}",
                        ruleId = RULE_THERMAL_THROTTLE,
                        title = "OS Thermal Throttling Active",
                        measuredFact = "Android PowerManager currentThermalStatus = ${stats.powerManagerThermalStatus} (Level ${stats.powerManagerThermalStatusCode}).",
                        possibleCausesAndOutcomes = "The operating system kernel has formally registered thermal load exceeding moderate threshold (Level ${stats.powerManagerThermalStatusCode}). Dynamic Voltage and Frequency Scaling (DVFS) may lower CPU and GPU clock speeds to cool silicon junctions, which can reduce frame rendering speeds.",
                        suggestedAction = "Lower in-game graphic shader resolution or target frame rate, and allow the device chassis to cool in a well-ventilated space before starting another intensive match.",
                        severity = RuleSeverity.CRITICAL,
                        confidenceLabel = "[FACT: Android PowerManager API]"
                    )
                )
            }
        }

        // 2. RULE: Charging High Battery Temperature
        if (stats.isCharging && stats.batteryTemperatureC >= 39.5f) {
            if (!dismissedRuleIds.contains(RULE_CHARGING_HIGH_TEMP)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_CHARGING_HIGH_TEMP}_${System.currentTimeMillis()}",
                        ruleId = RULE_CHARGING_HIGH_TEMP,
                        title = "Battery Charging Thermal Accumulation",
                        measuredFact = "Battery temperature measured at ${stats.batteryTemperatureC}°C while device is actively charging.",
                        possibleCausesAndOutcomes = "Fast-charging circuitry generates 3W–5W of supplementary thermal energy adjacent to internal battery chemistry. Combined with GPU/CPU rendering workload, this accelerates thermal accumulation and increases thermal throttling probability.",
                        suggestedAction = "Disconnect charger during active gaming sessions or allow battery to charge while the screen is off before resuming intensive gameplay.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: Android BatteryManager Sensor]"
                    )
                )
            }
        }

        // 3. RULE: Elevated Battery Temperature on Battery Power
        if (!stats.isCharging && stats.batteryTemperatureC >= 41.0f) {
            if (!dismissedRuleIds.contains(RULE_HIGH_BATTERY_TEMP)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_HIGH_BATTERY_TEMP}_${System.currentTimeMillis()}",
                        ruleId = RULE_HIGH_BATTERY_TEMP,
                        title = "Elevated Battery Temperature",
                        measuredFact = "Battery temperature sensor reading is ${stats.batteryTemperatureC}°C on battery power.",
                        possibleCausesAndOutcomes = "Sustained gaming GPU compute and screen display power consumption have elevated internal chassis temperature. While battery temperature is separate from CPU/GPU junction temperature, heat trapped inside protective cases often leads to overall system thermal throttling.",
                        suggestedAction = "Remove thick protective cases, reduce screen brightness, and take a 3-minute pause between matches to let heat dissipate.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: Android BatteryManager Sensor]"
                    )
                )
            }
        }

        // 4. RULE: High Memory Pressure
        if (stats.isLowMemory || (stats.memoryUsagePercent >= 88 && stats.availableMemoryGb < 1.0f)) {
            if (!dismissedRuleIds.contains(RULE_HIGH_MEMORY_PRESSURE)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_HIGH_MEMORY_PRESSURE}_${System.currentTimeMillis()}",
                        ruleId = RULE_HIGH_MEMORY_PRESSURE,
                        title = "RAM Headroom Constrained",
                        measuredFact = "System memory usage is at ${stats.memoryUsagePercent}% (${stats.availableMemoryGb} GB free out of ${stats.totalMemoryGb} GB total; system low-mem threshold: ${stats.memoryThresholdGb} GB).",
                        possibleCausesAndOutcomes = "When available memory drops near system low-memory thresholds, the Android Low Memory Killer (LMK) daemon compresses zRAM or reclaims cached pages. This background memory swapping can induce momentary frame drops during game asset texture streaming.",
                        suggestedAction = "Manually close non-essential background applications (such as social media, web browsers, or video streams) before starting high-fidelity games. ZeroX Boost does not automatically kill background processes.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: Android ActivityManager MemoryInfo]"
                    )
                )
            }
        }

        // 5. RULE: Constrained Storage
        if (stats.storageUsagePercent >= 92 || stats.availableStorageGb < 3.0f) {
            if (!dismissedRuleIds.contains(RULE_LOW_STORAGE)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_LOW_STORAGE}_${System.currentTimeMillis()}",
                        ruleId = RULE_LOW_STORAGE,
                        title = "Internal Storage Scratch Space Low",
                        measuredFact = "Internal storage is ${stats.storageUsagePercent}% full (${stats.availableStorageGb} GB free out of ${stats.totalStorageGb} GB total).",
                        possibleCausesAndOutcomes = "Low disk space reduces available scratch space for game patch downloads, dynamic shader compilation caches, and swap files, which may lead to asset loading delays.",
                        suggestedAction = "Review and uninstall unused applications or large media files via Android System Settings > Storage. ZeroX Boost never deletes user files automatically.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: Android StatFs Storage API]"
                    )
                )
            }
        }

        // 6. RULE: Network Jitter / Packet Loss
        if (netStats.isConnected && (netStats.jitterMs > 25 || netStats.packetLossPercent > 10 || netStats.timeoutCount > 0)) {
            if (!dismissedRuleIds.contains(RULE_UNSTABLE_NETWORK_JITTER)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_UNSTABLE_NETWORK_JITTER}_${System.currentTimeMillis()}",
                        ruleId = RULE_UNSTABLE_NETWORK_JITTER,
                        title = "Multiplayer Network Jitter / Packet Loss",
                        measuredFact = "TCP gateway latency test measured jitter of ${netStats.jitterMs}ms, ${netStats.packetLossPercent}% packet loss, and ${netStats.timeoutCount} timeouts (Gateway Ping: ${netStats.pingMs}ms).",
                        possibleCausesAndOutcomes = "Wireless channel congestion, 2.4GHz Wi-Fi interference, or cellular handovers cause delay variance (jitter). Note: Network jitter affects online multiplayer position synchronization and hit registration, NOT the GPU rendering frame rate.",
                        suggestedAction = "Switch to a 5GHz Wi-Fi network, move closer to your router, or pause background downloads on other connected network devices.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: TCP Gateway Socket Measurement]"
                    )
                )
            }
        }

        // 7. RULE: High Refresh Rate Power Tradeoff
        if (stats.displayRefreshRate >= 90f && ((stats.batteryTemperatureC > 0f && stats.batteryTemperatureC >= 38.5f) || stats.batteryLevelPercent <= 25)) {
            if (!dismissedRuleIds.contains(RULE_HIGH_REFRESH_RATE_POWER)) {
                val tempText = if (stats.batteryTemperatureC > 0f) "${stats.batteryTemperatureC}°C" else "N/A"
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_HIGH_REFRESH_RATE_POWER}_${System.currentTimeMillis()}",
                        ruleId = RULE_HIGH_REFRESH_RATE_POWER,
                        title = "High Display Refresh Rate Power Tradeoff",
                        measuredFact = "Display panel is operating at ${stats.displayRefreshRate.toInt()} Hz while battery is at ${stats.batteryLevelPercent}% (Battery Temp: $tempText).",
                        possibleCausesAndOutcomes = "Operating the display panel at high refresh rates (90Hz/120Hz) increases display controller power draw and thermal dissipation. If the game engine is capped at 30 or 60 FPS, high panel refresh rates consume battery without visual benefit.",
                        suggestedAction = "Consider setting display refresh rate to 60Hz in Android Display Settings to conserve power and reduce display panel heat.",
                        severity = RuleSeverity.INFO,
                        confidenceLabel = "[ESTIMATE: Hardware Power Efficiency Tradeoff]"
                    )
                )
            }
        }

        // 8. RULE: Third-Party Game FPS Sandbox Limitation
        if (!fpsAvailable) {
            if (!dismissedRuleIds.contains(RULE_FPS_UNAVAILABLE_SANDBOX)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_FPS_UNAVAILABLE_SANDBOX}_${System.currentTimeMillis()}",
                        ruleId = RULE_FPS_UNAVAILABLE_SANDBOX,
                        title = "Third-Party Live FPS Sandbox Limitation",
                        measuredFact = "Live frame rate (FPS) measurement for third-party games is marked as Unavailable on standard Android sandbox.",
                        possibleCausesAndOutcomes = "Android security architecture isolates third-party app processes. Without system root privileges or vendor-specific OEM accessibility permissions, ordinary Android apps cannot inject overlay hooks or inspect other games' SurfaceFlinger rendering pipelines.",
                        suggestedAction = "Use your device OEM Game Center overlay if available, or observe visual frame smoothness while monitoring hardware thermal status in ZeroX Boost.",
                        severity = RuleSeverity.INFO,
                        confidenceLabel = "[FACT: Android Security Architecture Sandbox]"
                    )
                )
            }
        }

        // 9. RULE: Low Battery Level
        if (!stats.isCharging && stats.batteryLevelPercent <= 15) {
            if (!dismissedRuleIds.contains(RULE_LOW_BATTERY_GAMING)) {
                list.add(
                    AdvisorRecommendation(
                        id = "rec_${RULE_LOW_BATTERY_GAMING}_${System.currentTimeMillis()}",
                        ruleId = RULE_LOW_BATTERY_GAMING,
                        title = "Low Battery Power Throttling Risk",
                        measuredFact = "Battery level is at ${stats.batteryLevelPercent}% on battery power.",
                        possibleCausesAndOutcomes = "Many Android OEM power management systems trigger aggressive low-battery CPU/GPU downclocking when battery drops below 15% to prevent sudden voltage sag.",
                        suggestedAction = "Connect to charger or save game progress to prevent unexpected device shutdown.",
                        severity = RuleSeverity.WARNING,
                        confidenceLabel = "[FACT: Android BatteryManager API]"
                    )
                )
            }
        }

        return list
    }

    /**
     * Generates a pre-game readiness evaluation score (0-100%) and structured checklist items.
     */
    fun evaluatePreGameReadiness(stats: DeviceStats, netStats: NetworkStats): PreGameReadinessCheck {
        var score = 100
        val items = mutableListOf<PreGameCheckItem>()

        // 1. Memory Check
        val ramPassed = stats.memoryUsagePercent < 85 && stats.availableMemoryGb >= 1.2f
        if (!ramPassed) score -= 20
        items.add(
            PreGameCheckItem(
                category = "RAM Memory",
                title = "Available Memory Headroom",
                statusText = "${stats.availableMemoryGb} GB free (${stats.memoryUsagePercent}% used)",
                isPassed = ramPassed,
                confidenceLabel = "[FACT: ActivityManager]"
            )
        )

        // 2. Thermal Check
        val hasBatteryTemp = stats.batteryTemperatureC > 0f
        val batteryTempLabel = if (hasBatteryTemp) "${stats.batteryTemperatureC}°C" else "N/A"
        val thermalPassed = stats.powerManagerThermalStatusCode < 2 && (!hasBatteryTemp || stats.batteryTemperatureC < 39.5f)
        if (!thermalPassed) score -= 25
        items.add(
            PreGameCheckItem(
                category = "Thermals",
                title = "Device Thermal Condition",
                statusText = "Battery $batteryTempLabel • OS State: ${stats.powerManagerThermalStatus}",
                isPassed = thermalPassed,
                confidenceLabel = "[FACT: PowerManager & BatteryManager]"
            )
        )

        // 3. Battery & Power Check
        val batteryPassed = stats.batteryLevelPercent > 20 && !(stats.isCharging && hasBatteryTemp && stats.batteryTemperatureC >= 38.5f)
        if (!batteryPassed) score -= 20
        items.add(
            PreGameCheckItem(
                category = "Battery Power",
                title = "Power & Charging Status",
                statusText = "Battery ${stats.batteryLevelPercent}% • ${if (stats.isCharging) "Charging (Heat Risk)" else "On Battery"}",
                isPassed = batteryPassed,
                confidenceLabel = "[FACT: BatteryManager]"
            )
        )

        // 4. Network Gateway Check
        val netPassed = !netStats.isConnected || (netStats.pingMs < 60 && netStats.jitterMs <= 20 && netStats.packetLossPercent == 0)
        if (!netPassed) score -= 25
        items.add(
            PreGameCheckItem(
                category = "Network",
                title = "Local Gateway TCP Socket Ping",
                statusText = if (netStats.isConnected) "${netStats.pingMs}ms Ping • Jitter ${netStats.jitterMs}ms (${netStats.connectionType})" else "Offline",
                isPassed = netPassed,
                confidenceLabel = "[FACT: Local Gateway TCP Socket]"
            )
        )

        val finalScore = score.coerceIn(0, 100)
        val label = when {
            finalScore >= 90 -> "EXCELLENT READINESS"
            finalScore >= 75 -> "GOOD READINESS"
            finalScore >= 55 -> "MODERATE ATTENTION NEEDED"
            else -> "CRITICAL RISK (COOL DEVICE FIRST)"
        }

        return PreGameReadinessCheck(
            scorePercent = finalScore,
            readinessLabel = label,
            ramSummary = "${stats.availableMemoryGb} GB Free",
            thermalSummary = batteryTempLabel,
            batterySummary = "${stats.batteryLevelPercent}%",
            networkSummary = if (netStats.isConnected) "${netStats.pingMs}ms" else "Offline",
            items = items
        )
    }

    /**
     * Legitimate, user-adjustable graphics recommendations for popular Android games.
     */
    fun getGameSpecificGuide(gameTitle: String): GameSpecificGuide {
        val lower = gameTitle.lowercase()
        return when {
            lower.contains("free fire") -> GameSpecificGuide(
                gameTitle = "Garena Free Fire / Free Fire MAX",
                recommendedGraphics = "Smooth (or Standard for High-End Devices)",
                targetFpsSetting = "High FPS Mode (60 FPS cap in Free Fire display settings)",
                shadowSetting = "OFF",
                antiAliasingSetting = "Normal / OFF",
                thermalStrategy = "Free Fire engine operates at lower shader complexity when Graphics is set to Smooth, reducing thermal dissipation by ~30%.",
                batteryStrategy = "Disable High Resolution in Free Fire display options to reduce GPU memory bandwidth and save battery.",
                networkStrategy = "Free Fire multiplayer tick rate benefits from 5GHz Wi-Fi to reduce TCP packet retransmission jitter.",
                realisticLimitations = "Adjusting settings inside Free Fire options reduces GPU workload. ZeroX Boost does not alter game files or bypass in-game frame rate caps.",
                confidenceLabel = "[FACT: Garena Free Fire User Settings Standard]"
            )

            lower.contains("pubg") || lower.contains("bgmi") -> GameSpecificGuide(
                gameTitle = "PUBG Mobile / BGMI",
                recommendedGraphics = "Smooth Preset (Prioritizes Frame Rate over Shaders)",
                targetFpsSetting = "Extreme / 90 FPS (Highest unlocked option in PUBG graphics menu)",
                shadowSetting = "Disabled",
                antiAliasingSetting = "Disabled / 2X",
                thermalStrategy = "Unreal Engine 4 in PUBG runs significantly cooler on Smooth graphics, preventing thermal throttling mid-match.",
                batteryStrategy = "Set Color Style to Classic instead of Realistic to reduce dynamic post-processing calculations.",
                networkStrategy = "Use 5GHz Wi-Fi or LTE. High network jitter causes rubberbanding and hit-registration delays.",
                realisticLimitations = "FPS is bound by your device GPU capability and PUBG in-game graphics selection. ZeroX Boost cannot force 90 FPS if your phone lacks official support.",
                confidenceLabel = "[FACT: Krafton UE4 Performance Guidelines]"
            )

            lower.contains("call of duty") || lower.contains("cod") -> GameSpecificGuide(
                gameTitle = "Call of Duty: Mobile (CODM)",
                recommendedGraphics = "Low or Medium Graphics Quality",
                targetFpsSetting = "Max / Ultra Frame Rate",
                shadowSetting = "Real-time Shadows OFF",
                antiAliasingSetting = "OFF / Bloom OFF",
                thermalStrategy = "Lower graphics quality frees up GPU capacity so the phone can sustain Max Frame Rate without thermal throttling.",
                batteryStrategy = "Turn off Ragdoll physics and Depth of Field to extend battery runtime during battle royale matches.",
                networkStrategy = "CODM requires stable TCP gateway connections. Ensure zero background downloads are active.",
                realisticLimitations = "CODM settings must be toggled manually in CODM Audio & Graphics menu.",
                confidenceLabel = "[FACT: Activision CODM Graphics Standard]"
            )

            lower.contains("genshin") -> GameSpecificGuide(
                gameTitle = "Genshin Impact",
                recommendedGraphics = "Lowest or Low Preset",
                targetFpsSetting = "30 FPS (For Stability) or 60 FPS (If Cooling Fan Attached)",
                shadowSetting = "Lowest",
                antiAliasingSetting = "SMAA / Off",
                thermalStrategy = "Genshin Impact is extremely heavy on mobile GPUs. Setting 30 FPS target prevents rapid thermal throttling on uncooled phones.",
                batteryStrategy = "Reduce Shadow Quality and Volumetric Fog to halve GPU power draw.",
                networkStrategy = "Single-player combat uses local calculation; network ping affects inventory sync and coop mode.",
                realisticLimitations = "No Android optimizer app can force Genshin to run at 60 FPS on low-end hardware. Thermal throttling will occur if device gets hot.",
                confidenceLabel = "[FACT: HoYoverse Engine Thermal Guidance]"
            )

            lower.contains("mobile legends") || lower.contains("mlbb") -> GameSpecificGuide(
                gameTitle = "Mobile Legends: Bang Bang",
                recommendedGraphics = "Smooth / High",
                targetFpsSetting = "High Frame Rate (HFR Mode ON)",
                shadowSetting = "OFF",
                antiAliasingSetting = "HD Mode OFF (If thermal throttle occurs)",
                thermalStrategy = "Disabling HD Mode reduces texture buffer memory, keeping device cool during teamfights.",
                batteryStrategy = "HFR Mode consumes slightly more power but ensures smooth skill casting.",
                networkStrategy = "Enable Speed Mode in MLBB network settings for dual Wi-Fi/4G packet redundancy.",
                realisticLimitations = "HFR Mode availability depends on Moonton's device whitelist and local GPU capabilities.",
                confidenceLabel = "[FACT: Moonton MLBB Engine Standard]"
            )

            else -> GameSpecificGuide(
                gameTitle = gameTitle,
                recommendedGraphics = "Balanced / Smooth Preset",
                targetFpsSetting = "60 FPS or Max In-Game Unlocked Setting",
                shadowSetting = "Low / Off",
                antiAliasingSetting = "Off / FXAA",
                thermalStrategy = "Lowering shader complexity in game menus is the most effective method to sustain stable frame rates.",
                batteryStrategy = "Cap display refresh rate to match game engine target to avoid wasting battery.",
                networkStrategy = "Ensure a low-jitter Wi-Fi connection for responsive multiplayer sync.",
                realisticLimitations = "Settings must be adjusted inside the game application settings menu.",
                confidenceLabel = "[ESTIMATE: Standard Android Gaming Configuration]"
            )
        }
    }

    /**
     * Generates a before-and-after session comparison report.
     */
    fun generateSessionComparisonReport(session: BenchmarkSessionReport): SessionComparisonDetails {
        val durationMin = session.durationSeconds / 60
        val durationSec = session.durationSeconds % 60
        val durationStr = "${durationMin}m ${durationSec}s"

        val hasValidTemps = session.startTempC > 0f && session.peakTempC > 0f
        val tempDelta = if (hasValidTemps) session.peakTempC - session.startTempC else 0f
        val batteryDrain = session.startBatteryLevel - session.endBatteryLevel

        val verdict = when {
            session.throttlingObserved -> "Thermal Throttling Observed: Device reached thermal limits during gameplay. Lowering in-game graphics or disconnecting charger will prevent frame drops."
            !hasValidTemps -> "Session Completed Safely: OS thermal API remained monitored (${session.peakThermalStatus}). Battery temperature sensor unavailable on this environment."
            tempDelta >= 4.0f -> "Moderate Heating: Battery temperature rose +${String.format(java.util.Locale.US, "%.1f", tempDelta)}°C. Device remained below critical throttling threshold."
            else -> "Optimal Thermal Stability: Temperature stayed cool (+${String.format(java.util.Locale.US, "%.1f", tempDelta)}°C delta). Excellent gaming session profile."
        }

        return SessionComparisonDetails(
            sessionLabel = session.sessionLabel,
            gameTitle = session.gameTitle,
            durationFormatted = durationStr,
            batteryStartVsEnd = "${session.startBatteryLevel}% → ${session.endBatteryLevel}%",
            batteryDrainPercent = batteryDrain.coerceAtLeast(0),
            tempStartVsPeak = if (hasValidTemps) "${session.startTempC}°C → ${session.peakTempC}°C" else "N/A",
            tempDeltaC = tempDelta,
            thermalStatusTransition = "${session.startThermalStatus} → ${session.peakThermalStatus}",
            throttlingObserved = session.throttlingObserved,
            ramUsageSummary = "Avg Free: ${session.avgAvailRamGb} GB (Min: ${session.minAvailRamGb} GB)",
            networkLatencySummary = if (session.avgPingMs > 0) "Gateway Ping: ${session.avgPingMs}ms • Jitter: ${session.avgJitterMs}ms" else "Gateway Ping: N/A",
            evaluationVerdict = verdict,
            confidenceTag = "[FACT: Recorded Session Telemetry]"
        )
    }
}
