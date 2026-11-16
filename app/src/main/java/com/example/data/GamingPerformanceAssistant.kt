package com.example.data

enum class BottleneckType(val title: String, val badgeColorHex: Long) {
    THERMAL_THROTTLE("OS Thermal Throttling Reported", 0xFFEF4444),
    ELEVATED_BATTERY_TEMP("Elevated Battery Temperature", 0xFFF59E0B),
    MEMORY_PRESSURE("High RAM Pressure", 0xFFF59E0B),
    NETWORK_JITTER("Variable TCP Latency", 0xFF8B5CF6),
    STORAGE_CONSTRAINED("Low Storage Space", 0xFFF59E0B),
    OPTIMAL("Device Telemetry Normal", 0xFF10B981)
}

data class BottleneckAnalysis(
    val primaryBottleneck: BottleneckType,
    val summary: String,
    val technicalCause: String,
    val conditionalStrategy: String
)

data class PreGameChecklistItem(
    val id: String,
    val title: String,
    val description: String,
    val whyItMayHelp: String,
    val isRecommended: Boolean = true,
    var isCompleted: Boolean = false
)

data class GameGraphicsAdvice(
    val gameTitle: String,
    val conditionalGoal: String,
    val recommendedGraphicsPreset: String,
    val recommendedFrameRateCap: String,
    val shadowSetting: String,
    val antiAliasing: String,
    val technicalRationale: String
)

object GamingPerformanceAssistant {

    fun analyzeBottlenecks(stats: DeviceStats, netStats: NetworkStats): BottleneckAnalysis {
        // Only report real Thermal Throttling if verified by PowerManager API evidence
        if (stats.isThermalStatusSupported && stats.powerManagerThermalStatusCode >= 2) {
            return BottleneckAnalysis(
                primaryBottleneck = BottleneckType.THERMAL_THROTTLE,
                summary = "Android PowerManager reports: ${stats.powerManagerThermalStatus} (Level ${stats.powerManagerThermalStatusCode}).",
                technicalCause = "The operating system has formally signaled that device thermal limits have reached or exceeded the OEM's moderate throttling threshold. Kernel DVFS may lower CPU/GPU frequencies to cool the hardware.",
                conditionalStrategy = "If a game is dropping frames during thermal load, lowering in-game shadows and resolution can reduce GPU rendering workload. Allowing the phone to cool down between matches may help recover clock speeds. Note: Actual frame consistency depends on your device's SoC cooling design."
            )
        }

        // Check for elevated battery temperature without claiming guaranteed SoC throttling
        if (stats.batteryTemperatureC >= 41.0f) {
            return BottleneckAnalysis(
                primaryBottleneck = BottleneckType.ELEVATED_BATTERY_TEMP,
                summary = "Battery sensor is warm at ${stats.batteryTemperatureC}°C.",
                technicalCause = "Battery temperature is elevated. While battery temperature is distinct from CPU/GPU silicon junction temperatures, sustained high heat across the device chassis often precedes SoC thermal throttling depending on device construction.",
                conditionalStrategy = "On devices where charging introduces additional heat, playing on battery or in a cooler room may help reduce thermal accumulation."
            )
        }

        // Check for Memory Pressure (Low Memory condition)
        if (stats.isLowMemory || (stats.memoryUsagePercent >= 88 && stats.availableMemoryGb < 0.9f)) {
            return BottleneckAnalysis(
                primaryBottleneck = BottleneckType.MEMORY_PRESSURE,
                summary = "RAM headroom is constrained (${stats.memoryUsagePercent}% used, ${stats.availableMemoryGb} GB free).",
                technicalCause = "When free memory approaches the system threshold (${stats.memoryThresholdGb} GB), the Android Low Memory Killer (LMK) and zRAM compression may increase CPU overhead when swapping memory, potentially causing micro-stutter.",
                conditionalStrategy = "Closing heavy background apps (such as video streaming apps and web browsers) before gaming may help ensure adequate RAM headroom for game assets."
            )
        }

        // Check for TCP Latency Jitter
        if (netStats.jitterMs > 30 || (netStats.pingMs > 100 && netStats.pingMs != -1)) {
            return BottleneckAnalysis(
                primaryBottleneck = BottleneckType.NETWORK_JITTER,
                summary = "TCP latency is variable (Latency: ${netStats.pingMs}ms, Jitter: ${netStats.jitterMs}ms).",
                technicalCause = "Packet delay variation (jitter) causes multiplayer network desync and player rubberbanding. Note: Network jitter affects online position synchronization, NOT the GPU/CPU rendering frame rate.",
                conditionalStrategy = "Switching to a 5GHz Wi-Fi band or moving closer to your wireless access point may help stabilize connection packet pacing."
            )
        }

        // Check for Storage
        if (stats.storageUsagePercent >= 93) {
            return BottleneckAnalysis(
                primaryBottleneck = BottleneckType.STORAGE_CONSTRAINED,
                summary = "Internal storage is over 93% full (${stats.availableStorageGb} GB remaining).",
                technicalCause = "Low available storage limits scratch space for game asset caches and dynamic shader compilation.",
                conditionalStrategy = "Freeing up at least 3–5 GB of space can ensure games have sufficient cache headroom."
            )
        }

        return BottleneckAnalysis(
            primaryBottleneck = BottleneckType.OPTIMAL,
            summary = "Hardware telemetry appears within normal baseline ranges.",
            technicalCause = "No active PowerManager thermal throttling or critical memory pressure reported.",
            conditionalStrategy = "Use your game's recommended graphics settings. Note that baseline gaming performance ultimately depends on your device processor capabilities."
        )
    }

    fun getPreGameChecklist(): List<PreGameChecklistItem> {
        return listOf(
            PreGameChecklistItem(
                id = "dnd",
                title = "Enable Do Not Disturb",
                description = "Block incoming notification popups and banners.",
                whyItMayHelp = "Notification popups trigger surface composition overlays, which can introduce momentary touch and visual interruptions."
            ),
            PreGameChecklistItem(
                id = "charger",
                title = "Evaluate Playing Off Charger",
                description = "Play on battery if battery level is sufficient.",
                whyItMayHelp = "On many devices, fast charging adds 3W–5W of heat from the charging circuit, which can increase overall thermal buildup."
            ),
            PreGameChecklistItem(
                id = "refresh_rate",
                title = "Review Screen Refresh Rate",
                description = "Check whether 60Hz or higher refresh rate is appropriate.",
                whyItMayHelp = "If a game runs at 30 or 60 FPS, locking the display to 60Hz can reduce display processor power consumption on some devices."
            ),
            PreGameChecklistItem(
                id = "wifi_5g",
                title = "Prefer 5GHz Wi-Fi Over 2.4GHz",
                description = "Connect to 5GHz Wi-Fi when available.",
                whyItMayHelp = "5GHz Wi-Fi typically has less local channel interference, which can reduce packet delay variation (jitter)."
            ),
            PreGameChecklistItem(
                id = "case",
                title = "Consider Removing Thick Phone Cases",
                description = "Especially in warm ambient environments.",
                whyItMayHelp = "Heavy protective cases may insulate the device, slowing down passive heat dissipation through the rear panel."
            ),
            PreGameChecklistItem(
                id = "brightness",
                title = "Moderate Screen Brightness",
                description = "Avoid 100% maximum brightness indoors.",
                whyItMayHelp = "Display panels at maximum brightness generate heat directly adjacent to internal components."
            )
        )
    }

    fun getGameAdviceList(): List<GameGraphicsAdvice> {
        return listOf(
            GameGraphicsAdvice(
                gameTitle = "BGMI / PUBG Mobile",
                conditionalGoal = "Potential Stabilization: Aim for smoother frame pacing around 30 FPS",
                recommendedGraphicsPreset = "Smooth",
                recommendedFrameRateCap = "High / Ultra (or Extreme on capable hardware)",
                shadowSetting = "Disabled",
                antiAliasing = "Disabled",
                technicalRationale = "Selecting 'Smooth' reduces fragment shader complexity. If GPU workload is the primary bottleneck, disabling shadows and anti-aliasing lowers GPU load, which may help delay heat buildup. Results vary by device chipset."
            ),
            GameGraphicsAdvice(
                gameTitle = "Free Fire MAX",
                conditionalGoal = "Potential Stabilization: Reduce stutter during squad combat",
                recommendedGraphicsPreset = "Standard or Smooth",
                recommendedFrameRateCap = "High",
                shadowSetting = "OFF",
                antiAliasing = "OFF",
                technicalRationale = "Special visual and skin effects increase GPU draw calls. Lowering to Standard with Shadows OFF may help entry-level devices maintain more consistent frame pacing."
            ),
            GameGraphicsAdvice(
                gameTitle = "Call of Duty: Mobile",
                conditionalGoal = "Potential Stabilization: Consistent touch response in multiplayer",
                recommendedGraphicsPreset = "Low",
                recommendedFrameRateCap = "Max or High",
                shadowSetting = "Real-time Shadows OFF",
                antiAliasing = "OFF",
                technicalRationale = "Prioritizing low graphic settings over reflections reduces render times per frame, which may help maintain frame consistency during heavy combat."
            ),
            GameGraphicsAdvice(
                gameTitle = "Genshin Impact",
                conditionalGoal = "Potential Stabilization: Reduce severe throttling drops in open world",
                recommendedGraphicsPreset = "Lowest",
                recommendedFrameRateCap = "30 FPS",
                shadowSetting = "Lowest",
                antiAliasing = "OFF",
                technicalRationale = "Genshin Impact places heavy demands on mobile GPUs. Setting a 30 FPS cap and Lowest preset reduces GPU load. On budget chipsets, attempting 60 FPS typically causes early thermal throttling."
            ),
            GameGraphicsAdvice(
                gameTitle = "Asphalt 9: Legends",
                conditionalGoal = "Potential Stabilization: Smooth racing physics",
                recommendedGraphicsPreset = "Performance",
                recommendedFrameRateCap = "30 FPS",
                shadowSetting = "Low",
                antiAliasing = "Default",
                technicalRationale = "Dynamic resolution scaling in Performance mode helps balance visual fidelity and rendering workload."
            )
        )
    }
}
