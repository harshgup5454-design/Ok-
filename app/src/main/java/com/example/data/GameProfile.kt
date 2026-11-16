package com.example.data

data class GameProfile(
    val id: String,
    val name: String,
    val packageName: String,
    val performanceMode: PerformanceMode = PerformanceMode.PERFORMANCE_ASSISTANT,
    val fpsLock: Int = 30, // 30, 45, 60, 90, 120
    val previousMode: PerformanceMode? = null,
    val previousFpsLock: Int = 60,
    val isInstalled: Boolean = true,
    val isInLibrary: Boolean = true,
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = 0L,
    val customNotes: String = "",
    val recommendedGraphics: String = "Smooth / Low",
    val recommendedFpsCap: String = "30–45 FPS",
    val shadowSetting: String = "Disabled",
    val antiAliasing: String = "Disabled",
    val thermalAdvice: String = "Keep ambient temp cool; remove thick protective cases during extended gameplay.",
    val batteryAdvice: String = "Avoid gaming while fast-charging to prevent compounding battery and SoC thermal loads.",
    val networkAdvice: String = "Prefer 5GHz Wi-Fi or close proximity to Wi-Fi access point to minimize TCP jitter.",
    val deviceLimitations: String = "Standard Android devices without external cooling throttle clock speeds when sustained heat builds up."
)

enum class PerformanceMode(val displayName: String, val description: String, val targetProfileNote: String) {
    PERFORMANCE_ASSISTANT(
        "Performance Assistant",
        "Target 30–32 FPS steady stabilization without thermal throttling",
        "Recommends lowering shader and shadow load so SoC maintains steady clock speeds instead of dropping into severe 18–20 FPS throttling"
    ),
    BALANCED(
        "Balanced",
        "Standard gaming balance between visual fidelity and battery longevity",
        "Standard 60Hz display refresh with moderate graphic presets and balanced power envelope"
    ),
    BATTERY_SAVER(
        "Battery Efficiency",
        "Prolong gaming sessions on low or moderate battery charge",
        "Recommends 30 FPS cap, 40% screen brightness, and disabling background app synchronization"
    ),
    THERMAL_PROTECTION(
        "Thermal Protection",
        "Aggressive heat prevention for hot climates and intensive titles",
        "Alerts as soon as battery temperature approaches 38.5°C to prevent hardware DVFS downclocking"
    )
}

object GameProfilesRepository {
    fun getDefaultProfiles(): List<GameProfile> {
        return listOf(
            GameProfile(
                id = "1",
                name = "BGMI / PUBG Mobile",
                packageName = "com.pubg.imobile",
                performanceMode = PerformanceMode.PERFORMANCE_ASSISTANT,
                fpsLock = 30,
                isInLibrary = true,
                isFavorite = true,
                customNotes = "Graphics: Smooth + Extreme/High. Turn OFF Anti-Aliasing and Auto-Adjust Graphics to prevent sudden mid-match FPS drops.",
                recommendedGraphics = "Smooth (Lowest Shader Complexity)",
                recommendedFpsCap = "High / Ultra (Aim for steady 30–40 FPS)",
                shadowSetting = "Disabled",
                antiAliasing = "Disabled (Saves ~18% GPU rasterization headroom)",
                thermalAdvice = "Take a 3-minute breather between classic matches. If battery temp exceeds 40°C, exit to lobby to let heat dissipate.",
                batteryAdvice = "Playing on battery below 20% triggers aggressive OEM battery saving modes. Keep charge between 30% and 80%.",
                networkAdvice = "Use 5GHz Wi-Fi band. LTE handover can cause 150ms+ ping spikes during close-quarter vehicle engagements.",
                deviceLimitations = "Mid-range SoCs (Snapdragon 680/G88/G99) cannot sustain 60 FPS in dense hot-drops (Pochinki/Bootcamp) due to memory bandwidth limits."
            ),
            GameProfile(
                id = "2",
                name = "Free Fire MAX",
                packageName = "com.dts.freefiremax",
                performanceMode = PerformanceMode.PERFORMANCE_ASSISTANT,
                fpsLock = 30,
                isInLibrary = true,
                isFavorite = true,
                customNotes = "Standard graphics with Shadows OFF and High FPS enabled delivers stable frame-pacing without micro-stutter.",
                recommendedGraphics = "Standard (Avoid Ultra water reflections)",
                recommendedFpsCap = "High (Consistent 30–45 FPS target)",
                shadowSetting = "Off (Critical for avoiding squad fight frame drops)",
                antiAliasing = "Off",
                thermalAdvice = "Free Fire MAX is relatively lightweight, but sustained sessions over 45 minutes can warm budget chipsets.",
                batteryAdvice = "Consume roughly 12–15% battery per hour on 50% brightness with Bluetooth audio off.",
                networkAdvice = "Keep background downloads and Google Play auto-updates paused during ranked battle royale.",
                deviceLimitations = "Shader compilation stutter can occur in the first 30 seconds of landing while map assets load into RAM."
            ),
            GameProfile(
                id = "3",
                name = "Call of Duty: Mobile",
                packageName = "com.activision.callofduty.shooter",
                performanceMode = PerformanceMode.PERFORMANCE_ASSISTANT,
                fpsLock = 60,
                isInLibrary = true,
                isFavorite = true,
                customNotes = "Graphic Quality LOW + Frame Rate MAX gives minimum touch input latency and rock-solid competitive stability.",
                recommendedGraphics = "Low Graphic Quality",
                recommendedFpsCap = "Max / Very High",
                shadowSetting = "Real-Time Shadows OFF",
                antiAliasing = "Off (Turn off Depth of Field & Bloom)",
                thermalAdvice = "Multiplayer matches run cool, but 100-player Battle Royale (Isolated/Blackout) heavily strains thermal headroom.",
                batteryAdvice = "Disable shader preload while on low battery; run 'Shader Preload' in COD settings once while plugged into AC before gaming.",
                networkAdvice = "TCP/UDP jitter is deadly in gunfights. Verify gateway ping is under 50ms with jitter under 10ms.",
                deviceLimitations = "Entry-level 4GB RAM devices will experience asset streaming hitching in BR vehicles as textures swap in memory."
            ),
            GameProfile(
                id = "4",
                name = "Genshin Impact",
                packageName = "com.miHoYo.GenshinImpact",
                performanceMode = PerformanceMode.THERMAL_PROTECTION,
                fpsLock = 30,
                isInLibrary = true,
                isFavorite = false,
                customNotes = "Lowest settings across all options + 30 FPS cap. Setting 60 FPS on budget phones triggers severe thermal throttling within 7 minutes.",
                recommendedGraphics = "Lowest Preset (Render Resolution: Low/Medium)",
                recommendedFpsCap = "30 FPS Lock (Do NOT select 60 FPS on mid-range phones)",
                shadowSetting = "Lowest",
                antiAliasing = "Off or TAA Low",
                thermalAdvice = "Extremely heavy compute load on CPU and GPU. Playing in an air-conditioned room or in front of a fan significantly delays thermal throttling.",
                batteryAdvice = "Genshin draws 4.5W–7W power. Do NOT play while fast-charging—combined battery chemistry heat and SoC heat causes instant 15 FPS lock.",
                networkAdvice = "Single player exploration is forgiving of latency, but Spiral Abyss timing requires consistent server ping.",
                deviceLimitations = "Unity engine complex post-processing and open-world streaming exceed thermal envelopes of non-flagship chipsets without external cooling."
            ),
            GameProfile(
                id = "5",
                name = "Asphalt 9: Legends",
                packageName = "com.gameloft.android.ANGL.GloftA9",
                performanceMode = PerformanceMode.BALANCED,
                fpsLock = 30,
                isInLibrary = true,
                isFavorite = false,
                customNotes = "Performance mode locks resolution scaling to maintain steady 30 FPS physics simulation during nitro boost sequences.",
                recommendedGraphics = "Performance Preset (Default)",
                recommendedFpsCap = "30 FPS (or 60 FPS if supported by GPU driver)",
                shadowSetting = "Low",
                antiAliasing = "Off",
                thermalAdvice = "Burst racing gameplay (2–3 minutes per track) allows intermittent thermal cooldown between races.",
                batteryAdvice = "Disable Motion Blur and High Quality Audio to save ~8% battery per gaming hour.",
                networkAdvice = "Multiplayer ghosting occurs if ping variance exceeds 40ms. Keep Wi-Fi signal strong.",
                deviceLimitations = "Particle effects (car crashes and nitro smoke) cause momentary GPU fill-rate bottlenecks on Mali-G52/G57 class GPUs."
            ),
            GameProfile(
                id = "6",
                name = "Minecraft",
                packageName = "com.mojang.minecraftpe",
                performanceMode = PerformanceMode.BATTERY_SAVER,
                fpsLock = 60,
                isInLibrary = true,
                isFavorite = false,
                customNotes = "Reduce render distance to 8–10 chunks. Turn off Fancy Graphics and Beautiful Skies for 4+ hours of battery life.",
                recommendedGraphics = "Fancy Graphics OFF",
                recommendedFpsCap = "60 FPS",
                shadowSetting = "Smooth Lighting OFF",
                antiAliasing = "1 (Minimum)",
                thermalAdvice = "Chunk generation causes CPU thread spikes; staying in explored areas keeps temperatures remarkably low.",
                batteryAdvice = "Battery drain is linear with render distance. Lowering from 16 to 8 chunks halves battery draw.",
                networkAdvice = "Local realm connection latency is minimal; public servers require sub-80ms ping for accurate block breaking.",
                deviceLimitations = "Large redstone contraptions or mob farms saturate single-core CPU throughput regardless of GPU power."
            )
        )
    }
}
