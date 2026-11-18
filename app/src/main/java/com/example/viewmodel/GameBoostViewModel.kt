package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.smartgaming.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiagnosticItem(
    val title: String,
    val description: String,
    val status: DiagnosticStatus
)

enum class DiagnosticStatus {
    GOOD, WARNING, CRITICAL
}

sealed class SessionLaunchPrompt {
    data class MissingOverlayPermission(
        val gameProfile: GameProfile?,
        val gameTitle: String,
        val profileType: ProfileType
    ) : SessionLaunchPrompt()

    data class GameNotInstalled(
        val gameTitle: String,
        val packageName: String,
        val profileType: ProfileType
    ) : SessionLaunchPrompt()

    data class OverlayStartFailed(
        val message: String
    ) : SessionLaunchPrompt()
}

class GameBoostViewModel(application: Application) : AndroidViewModel(application) {

    private val aimRepository = AimIntelligenceRepository(application)

    private val _deviceStats = MutableStateFlow(DeviceStats())
    val deviceStats: StateFlow<DeviceStats> = _deviceStats.asStateFlow()

    private val _networkStats = MutableStateFlow(NetworkStats())
    val networkStats: StateFlow<NetworkStats> = _networkStats.asStateFlow()

    private val gameSpaceRepository = GameSpaceRepository(application)

    val gameProfiles: StateFlow<List<GameProfile>> = gameSpaceRepository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameProfilesRepository.getDefaultProfiles())

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _activeMonitoredGame = MutableStateFlow<GameProfile?>(null)
    val activeMonitoredGame: StateFlow<GameProfile?> = _activeMonitoredGame.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _diagnosticResults = MutableStateFlow<List<DiagnosticItem>>(emptyList())
    val diagnosticResults: StateFlow<List<DiagnosticItem>> = _diagnosticResults.asStateFlow()

    private val _bottleneckAnalysis = MutableStateFlow<BottleneckAnalysis?>(null)
    val bottleneckAnalysis: StateFlow<BottleneckAnalysis?> = _bottleneckAnalysis.asStateFlow()

    private val _preGameChecklist = MutableStateFlow(GamingPerformanceAssistant.getPreGameChecklist())
    val preGameChecklist: StateFlow<List<PreGameChecklistItem>> = _preGameChecklist.asStateFlow()

    // Benchmark Session State
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkSeconds = MutableStateFlow(0)
    val benchmarkSeconds: StateFlow<Int> = _benchmarkSeconds.asStateFlow()

    val benchmarkSessions: StateFlow<List<BenchmarkSessionReport>> = gameSpaceRepository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Benchmark Lab State
    val benchmarkLabResults: StateFlow<List<BenchmarkLabRunResult>> = gameSpaceRepository.benchmarkLabResultsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLabRunning = MutableStateFlow(false)
    val isLabRunning: StateFlow<Boolean> = _isLabRunning.asStateFlow()

    private val _labProgress = MutableStateFlow(0f)
    val labProgress: StateFlow<Float> = _labProgress.asStateFlow()

    private val _activeLabWorkload = MutableStateFlow("")
    val activeLabWorkload: StateFlow<String> = _activeLabWorkload.asStateFlow()

    private val _labTelemetryTemp = MutableStateFlow(0f)
    val labTelemetryTemp: StateFlow<Float> = _labTelemetryTemp.asStateFlow()

    private val _labTelemetryThermal = MutableStateFlow("Normal")
    val labTelemetryThermal: StateFlow<String> = _labTelemetryThermal.asStateFlow()

    private var labJob: Job? = null

    // Advanced Device Diagnostics Lab State
    private val _storageData = MutableStateFlow<StorageDiagnosticData?>(null)
    val storageData: StateFlow<StorageDiagnosticData?> = _storageData.asStateFlow()

    private val _displayData = MutableStateFlow<DisplayDiagnosticData?>(null)
    val displayData: StateFlow<DisplayDiagnosticData?> = _displayData.asStateFlow()

    private val _batteryData = MutableStateFlow<BatteryDiagnosticData?>(null)
    val batteryData: StateFlow<BatteryDiagnosticData?> = _batteryData.asStateFlow()

    private val _systemData = MutableStateFlow<SystemCompatibilityData?>(null)
    val systemData: StateFlow<SystemCompatibilityData?> = _systemData.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<DeviceDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DeviceDiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _edgeResponseState = MutableStateFlow(EdgeResponseState())
    val edgeResponseState: StateFlow<EdgeResponseState> = _edgeResponseState.asStateFlow()

    private val _lastSwipeResult = MutableStateFlow<SwipeMetricResult?>(null)
    val lastSwipeResult: StateFlow<SwipeMetricResult?> = _lastSwipeResult.asStateFlow()

    // Rule-Based Performance Advisor State
    private val _dismissedRuleIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedRuleIds: StateFlow<Set<String>> = _dismissedRuleIds.asStateFlow()

    val advisorHistory: StateFlow<List<AdvisorRecommendation>> = gameSpaceRepository.advisorRecommendationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeRecommendations = MutableStateFlow<List<AdvisorRecommendation>>(emptyList())
    val activeRecommendations: StateFlow<List<AdvisorRecommendation>> = _activeRecommendations.asStateFlow()

    private val _protectionStatus = MutableStateFlow(GamingProtectionStatus())
    val protectionStatus: StateFlow<GamingProtectionStatus> = _protectionStatus.asStateFlow()

    val overlayConfig: StateFlow<OverlayConfig> = OverlayPreferences.config

    init {
        OverlayPreferences.load(application)
        viewModelScope.launch {
            gameSpaceRepository.initializeDefaultsIfNeeded()
        }
    }

    // Aim & Control Intelligence State (Room Persistent)
    val savedProfiles: StateFlow<List<SensitivityProfile>> = aimRepository.savedProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calibrationHistory: StateFlow<List<RotationCalibrationAttempt>> = aimRepository.calibrationHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trainingHistory: StateFlow<List<AimTrainingSessionStats>> = aimRepository.trainingHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSensitivityProfile(profile: SensitivityProfile) {
        viewModelScope.launch {
            aimRepository.saveProfile(profile)
        }
    }

    fun deleteSensitivityProfile(id: String) {
        viewModelScope.launch {
            aimRepository.deleteProfile(id)
        }
    }

    fun duplicateSensitivityProfile(profile: SensitivityProfile) {
        viewModelScope.launch {
            aimRepository.duplicateProfile(profile)
        }
    }

    fun saveCalibrationAttempt(attempt: RotationCalibrationAttempt) {
        viewModelScope.launch {
            aimRepository.saveCalibrationAttempt(attempt)
        }
    }

    fun saveTrainingSession(stats: AimTrainingSessionStats) {
        viewModelScope.launch {
            aimRepository.saveTrainingSession(stats)
        }
    }

    private var benchmarkJob: Job? = null
    private val currentSnapshots = mutableListOf<TelemetrySnapshot>()
    private var activeSessionLabel = ""
    private var activeGameTitle = ""
    private var activePackageName = ""
    private var activeGraphicsSettings = ""
    private var activeAutopilotMode = "Manual"
    private var activeSessionStartTimeMs = 0L
    private var activeFpsSource = ""
    private var activeObservedAvgFps: Float? = null
    private var activeObservedMinFps: Float? = null
    private var activeTestNotes = ""
    private var startBatteryLevel = 100
    private var startThermalStatus = "Normal"

    fun refreshStats(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stats = DeviceMonitor.getDeviceStats(context)
                _deviceStats.update { stats }

                val protection = GamingProtectionEngine.evaluate(
                    stats = stats,
                    sessionSeconds = _benchmarkSeconds.value,
                    longSessionThresholdMinutes = overlayConfig.value.longSessionReminderMinutes
                )
                _protectionStatus.update { protection }
                SmartGamingManager.updateSessionTelemetry(
                    stats = stats,
                    sessionSeconds = _benchmarkSeconds.value,
                    longSessionReminderMinutes = overlayConfig.value.longSessionReminderMinutes
                )

                val netStats = NetworkMonitor.getNetworkStats(context)
                _networkStats.update { current ->
                    netStats.copy(
                        pingMs = current.pingMs,
                        secondaryPingMs = current.secondaryPingMs,
                        minPingMs = current.minPingMs,
                        maxPingMs = current.maxPingMs,
                        jitterMs = current.jitterMs,
                        dnsResolutionMs = current.dnsResolutionMs,
                        packetLossPercent = current.packetLossPercent,
                        timeoutCount = current.timeoutCount,
                        connectionSuccessRate = current.connectionSuccessRate,
                        stabilityRating = if (netStats.isConnected && current.pingMs > 0) current.stabilityRating else netStats.stabilityRating,
                        downloadSpeedMbps = current.downloadSpeedMbps,
                        uploadSpeedMbps = current.uploadSpeedMbps,
                        throughputNote = current.throughputNote
                    )
                }

                val bottleneck = GamingPerformanceAssistant.analyzeBottlenecks(stats, _networkStats.value)
                _bottleneckAnalysis.update { bottleneck }
                SmartGamingManager.updateNetworkTelemetry(_networkStats.value)
                SmartGamingManager.refreshUsageAccessStatus(context)

                refreshDiagnosticsInternal(context, stats)
                evaluateAdvisorRules()
            } catch (_: Exception) {}
        }
    }

    fun evaluateAdvisorRules() {
        val recs = PerformanceAdvisorEngine.evaluateAllRules(
            stats = _deviceStats.value,
            netStats = _networkStats.value,
            fpsAvailable = false,
            dismissedRuleIds = _dismissedRuleIds.value
        )
        _activeRecommendations.update { recs }

        viewModelScope.launch {
            for (rec in recs) {
                gameSpaceRepository.saveAdvisorRecommendation(rec)
            }
        }
    }

    fun dismissAdvisorRecommendation(ruleId: String) {
        _dismissedRuleIds.update { it + ruleId }
        _activeRecommendations.update { list -> list.filter { it.ruleId != ruleId } }
        viewModelScope.launch {
            gameSpaceRepository.dismissAdvisorRecommendationByRuleId(ruleId)
        }
    }

    fun resetDismissedAdvisorRules() {
        _dismissedRuleIds.update { emptySet() }
        evaluateAdvisorRules()
    }

    fun refreshDiagnostics(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshDiagnosticsInternal(context, _deviceStats.value)
            } catch (_: Exception) {}
        }
    }

    private fun refreshDiagnosticsInternal(context: Context, currentStats: DeviceStats) {
        val storage = DeviceDiagnosticsEngine.analyzeStorage(context)
        val display = DeviceDiagnosticsEngine.analyzeDisplay(context)
        val battery = DeviceDiagnosticsEngine.analyzeBattery(context)
        val system = DeviceDiagnosticsEngine.analyzeSystemCompatibility(context)
        val report = DeviceDiagnosticsEngine.generateDiagnosticReport(
            context = context,
            storage = storage,
            display = display,
            battery = battery,
            system = system,
            deviceStats = currentStats
        )
        _storageData.update { storage }
        _displayData.update { display }
        _batteryData.update { battery }
        _systemData.update { system }
        _diagnosticReport.update { report }
    }

    fun recordSwipeMetric(metric: SwipeMetricResult) {
        _lastSwipeResult.update { metric }
    }

    fun recordEdgeHit(edge: String) {
        _edgeResponseState.update { current ->
            when (edge.lowercase()) {
                "left" -> current.copy(leftEdgeHit = true)
                "right" -> current.copy(rightEdgeHit = true)
                "top" -> current.copy(topEdgeHit = true)
                "bottom" -> current.copy(bottomEdgeHit = true)
                else -> current
            }
        }
    }

    fun resetEdgeTests() {
        _edgeResponseState.update { EdgeResponseState() }
        _lastSwipeResult.update { null }
    }

    fun clearAppCache(context: Context): Long {
        val freed = DeviceDiagnosticsEngine.clearAppCache(context)
        refreshDiagnostics(context)
        return freed
    }

    fun runDiagnosticScan(context: Context) {
        viewModelScope.launch {
            _isScanning.update { true }
            _diagnosticResults.update { emptyList() }
            delay(1200)

            refreshStats(context)
            val burstResult = NetworkMonitor.measurePingStability()
            _networkStats.update {
                it.copy(
                    pingMs = burstResult.avgPing,
                    minPingMs = burstResult.minPing,
                    maxPingMs = burstResult.maxPing,
                    jitterMs = burstResult.jitter,
                    stabilityRating = burstResult.rating
                )
            }

            val stats = _deviceStats.value
            val netStats = _networkStats.value

            val results = mutableListOf<DiagnosticItem>()

            // Real PowerManager Thermal Check
            if (stats.isThermalStatusSupported && stats.powerManagerThermalStatusCode >= 2) {
                results.add(
                    DiagnosticItem(
                        "Thermal Throttling Active (PowerManager)",
                        "OS reports: ${stats.powerManagerThermalStatus}. Kernel DVFS may lower CPU/GPU clock speeds to manage device thermals.",
                        DiagnosticStatus.CRITICAL
                    )
                )
            } else if (stats.isThermalStatusSupported && stats.powerManagerThermalStatusCode == 1) {
                results.add(
                    DiagnosticItem(
                        "Light Thermal Impact (PowerManager)",
                        "OS reports light thermal load. Monitor device heat if playing extended sessions.",
                        DiagnosticStatus.WARNING
                    )
                )
            } else {
                results.add(
                    DiagnosticItem(
                        "Thermal Status: ${stats.powerManagerThermalStatus}",
                        "No thermal throttling signaled by Android PowerManager.",
                        DiagnosticStatus.GOOD
                    )
                )
            }

            // Battery Temperature (Clearly separate from SoC)
            val tempDisplay = if (stats.batteryTemperatureC > 0f) "${stats.batteryTemperatureC}°C" else "N/A"
            val tempDesc = if (stats.batteryTemperatureC > 0f) {
                "Battery sensor reading (${if (stats.isCharging) "Charging" else "On Battery"}). Note: Battery temperature is distinct from CPU/GPU silicon junction temperatures."
            } else {
                "Battery temperature sensor unavailable on this hardware."
            }
            results.add(
                DiagnosticItem(
                    "Battery Temperature: $tempDisplay",
                    tempDesc,
                    if (stats.batteryTemperatureC >= 41.0f) DiagnosticStatus.WARNING else DiagnosticStatus.GOOD
                )
            )

            // RAM pressure check
            if (stats.memoryUsagePercent > 88 || stats.isLowMemory) {
                results.add(
                    DiagnosticItem(
                        "High RAM Pressure",
                        "RAM usage is at ${stats.memoryUsagePercent}% (${stats.availableMemoryGb} GB free). Android LMK may trigger stutter during texture streaming if memory exhausts.",
                        DiagnosticStatus.WARNING
                    )
                )
            } else {
                results.add(
                    DiagnosticItem(
                        "Available RAM Headroom",
                        "${stats.availableMemoryGb} GB free out of ${stats.totalMemoryGb} GB. Memory headroom is sufficient for gaming assets.",
                        DiagnosticStatus.GOOD
                    )
                )
            }

            // Storage check
            if (stats.storageUsagePercent > 92) {
                results.add(
                    DiagnosticItem(
                        "Low Storage Scratch Space",
                        "Storage is ${stats.storageUsagePercent}% full (${stats.availableStorageGb} GB free). Keep at least 3–5 GB for dynamic asset caches.",
                        DiagnosticStatus.WARNING
                    )
                )
            } else {
                results.add(
                    DiagnosticItem(
                        "Storage Cache Headroom",
                        "${stats.availableStorageGb} GB free internal storage.",
                        DiagnosticStatus.GOOD
                    )
                )
            }

            // TCP Latency & Jitter Check
            if (burstResult.avgPing < 0) {
                results.add(
                    DiagnosticItem(
                        "TCP Latency Test Failed",
                        "TCP connection to 8.8.8.8 timed out or device is offline.",
                        DiagnosticStatus.CRITICAL
                    )
                )
            } else if (burstResult.jitter > 25 || burstResult.avgPing > 95) {
                results.add(
                    DiagnosticItem(
                        "Variable TCP Latency (Jitter: ${burstResult.jitter}ms)",
                        "Avg TCP latency: ${burstResult.avgPing}ms. Packet jitter causes multiplayer rubberbanding and desync (does not affect local GPU frame rendering).",
                        DiagnosticStatus.WARNING
                    )
                )
            } else {
                results.add(
                    DiagnosticItem(
                        "TCP Latency: ${burstResult.avgPing}ms (Jitter: ${burstResult.jitter}ms)",
                        "Gateway connection stability rated: ${burstResult.rating}.",
                        DiagnosticStatus.GOOD
                    )
                )
            }

            // Display refresh rate note
            results.add(
                DiagnosticItem(
                    "Display Refresh Rate: ${stats.displayRefreshRate.toInt()} Hz",
                    "Screen panel refresh rate. Note: Display refresh rate is distinct from actual in-game rendering FPS.",
                    DiagnosticStatus.GOOD
                )
            )

            _diagnosticResults.update { results }
            _bottleneckAnalysis.update { GamingPerformanceAssistant.analyzeBottlenecks(stats, netStats) }
            _isScanning.update { false }
        }
    }

    private val _isSpeedTesting = MutableStateFlow(false)
    val isSpeedTesting: StateFlow<Boolean> = _isSpeedTesting.asStateFlow()

    private val _speedTestProgress = MutableStateFlow(0f)
    val speedTestProgress: StateFlow<Float> = _speedTestProgress.asStateFlow()

    val networkHistory: StateFlow<List<NetworkHistoryRecord>> = gameSpaceRepository.networkHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkMonitor.getHistory())

    private var speedTestJob: Job? = null

    fun testNetworkPing() {
        viewModelScope.launch {
            val burst = NetworkMonitor.measureAdvancedDiagnostics()
            _networkStats.update {
                it.copy(
                    pingMs = burst.avgPing,
                    secondaryPingMs = burst.secondaryPing,
                    minPingMs = burst.minPing,
                    maxPingMs = burst.maxPing,
                    jitterMs = burst.jitter,
                    dnsResolutionMs = burst.dnsTimeMs,
                    packetLossPercent = burst.packetLossPercent,
                    timeoutCount = burst.timeoutCount,
                    connectionSuccessRate = burst.successRate,
                    stabilityRating = burst.rating
                )
            }
            SmartGamingManager.updateNetworkTelemetry(_networkStats.value)
            gameSpaceRepository.saveNetworkRecord(
                NetworkHistoryRecord(
                    id = "net_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    connectionType = _networkStats.value.connectionType,
                    avgPingMs = burst.avgPing,
                    jitterMs = burst.jitter,
                    packetLossPercent = burst.packetLossPercent,
                    downloadMbps = _networkStats.value.downloadSpeedMbps,
                    uploadMbps = _networkStats.value.uploadSpeedMbps,
                    rating = burst.rating
                )
            )
        }
    }

    fun startSpeedTest(context: Context? = getApplication()) {
        if (_isSpeedTesting.value) return
        speedTestJob?.cancel()
        _isSpeedTesting.update { true }
        _speedTestProgress.update { 0f }

        speedTestJob = viewModelScope.launch {
            val result = NetworkMonitor.runSpeedTest(
                context = context,
                onProgress = { prog -> _speedTestProgress.update { prog } },
                isActive = { _isSpeedTesting.value }
            )
            _isSpeedTesting.update { false }
            if (!result.isCancelled) {
                _networkStats.update {
                    it.copy(
                        downloadSpeedMbps = result.downloadMbps,
                        uploadSpeedMbps = result.uploadMbps,
                        throughputNote = result.note
                    )
                }
            }
        }
    }

    fun cancelSpeedTest() {
        _isSpeedTesting.update { false }
        speedTestJob?.cancel()
        speedTestJob = null
    }

    fun clearNetworkHistory() {
        viewModelScope.launch {
            gameSpaceRepository.clearNetworkHistory()
        }
    }

    fun toggleChecklistItem(id: String) {
        _preGameChecklist.update { list ->
            list.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
        }
    }

    fun updateGameProfile(profileId: String, mode: PerformanceMode, fps: Int) {
        viewModelScope.launch {
            val current = gameProfiles.value.find { it.id == profileId }
            if (current != null) {
                val changed = current.performanceMode != mode || current.fpsLock != fps
                val updated = if (changed) {
                    current.copy(
                        performanceMode = mode,
                        fpsLock = fps,
                        previousMode = current.performanceMode,
                        previousFpsLock = current.fpsLock
                    )
                } else {
                    current
                }
                gameSpaceRepository.saveProfile(updated)
            }
        }
    }

    fun updateFullGameProfile(updated: GameProfile) {
        viewModelScope.launch {
            val existing = gameProfiles.value.find { it.id == updated.id }
            val withHistory = if (existing != null && (existing.performanceMode != updated.performanceMode || existing.fpsLock != updated.fpsLock)) {
                updated.copy(
                    previousMode = existing.performanceMode,
                    previousFpsLock = existing.fpsLock
                )
            } else {
                updated
            }
            gameSpaceRepository.saveProfile(withHistory)
        }
    }

    fun restorePreviousGameProfile(profileId: String) {
        viewModelScope.launch {
            val current = gameProfiles.value.find { it.id == profileId } ?: return@launch
            val prevMode = current.previousMode ?: return@launch
            val restored = current.copy(
                performanceMode = prevMode,
                fpsLock = current.previousFpsLock,
                previousMode = current.performanceMode,
                previousFpsLock = current.fpsLock
            )
            gameSpaceRepository.saveProfile(restored)
        }
    }

    fun deleteGameProfilePermanently(profileId: String) {
        viewModelScope.launch {
            gameSpaceRepository.deleteProfile(profileId)
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.update { true }
            try {
                val apps = GameSpaceManager.getInstalledLaunchableApps(context)
                _installedApps.update { apps }

                // Sync installed status with profiles
                for (profile in gameProfiles.value) {
                    val isInst = GameSpaceManager.isPackageInstalled(context, profile.packageName)
                    if (profile.isInstalled != isInst) {
                        gameSpaceRepository.saveProfile(profile.copy(isInstalled = isInst))
                    }
                }
            } catch (_: Exception) {
            } finally {
                _isLoadingApps.update { false }
            }
        }
    }

    fun addGameToVault(context: Context, appName: String, packageName: String) {
        viewModelScope.launch {
            val trimmedPkg = packageName.trim()
            val trimmedName = appName.trim().ifBlank { trimmedPkg.substringAfterLast('.') }
            if (trimmedPkg.isBlank()) return@launch
            val existing = gameProfiles.value.find { it.packageName == trimmedPkg }
            if (existing != null) {
                val updated = existing.copy(isInLibrary = true, name = trimmedName)
                gameSpaceRepository.saveProfile(updated)
            } else {
                val newProfile = GameProfile(
                    id = "game_${System.currentTimeMillis()}",
                    name = trimmedName,
                    packageName = trimmedPkg,
                    performanceMode = PerformanceMode.BALANCED,
                    fpsLock = 60,
                    isInstalled = GameSpaceManager.isPackageInstalled(context, trimmedPkg),
                    isInLibrary = true,
                    customNotes = "Added to personal Gaming Space vault.",
                    recommendedGraphics = "Smooth / Medium",
                    recommendedFpsCap = "30–60 FPS",
                    shadowSetting = "Disabled",
                    antiAliasing = "Disabled",
                    thermalAdvice = "Monitor device heat during extended sessions.",
                    batteryAdvice = "Keep screen brightness moderate for prolonged sessions.",
                    networkAdvice = "Use stable 5GHz Wi-Fi or strong cellular connection.",
                    deviceLimitations = "Device performance is bounded by thermal headroom and available RAM."
                )
                gameSpaceRepository.saveProfile(newProfile)
            }
        }
    }

    fun removeGameFromVault(profileId: String) {
        viewModelScope.launch {
            val current = gameProfiles.value.find { it.id == profileId }
            if (current != null) {
                val updated = current.copy(isInLibrary = false)
                gameSpaceRepository.saveProfile(updated)
            }
        }
    }

    fun toggleGameFavorite(profileId: String) {
        viewModelScope.launch {
            val current = gameProfiles.value.find { it.id == profileId }
            if (current != null) {
                val updated = current.copy(isFavorite = !current.isFavorite)
                gameSpaceRepository.saveProfile(updated)
            }
        }
    }

    private val _sessionLaunchPrompt = MutableStateFlow<SessionLaunchPrompt?>(null)
    val sessionLaunchPrompt: StateFlow<SessionLaunchPrompt?> = _sessionLaunchPrompt.asStateFlow()

    fun dismissSessionLaunchPrompt() {
        _sessionLaunchPrompt.update { null }
    }

    /**
     * Unified Dashboard / Gaming Session launcher:
     * 1. Validates selected game (if a specific package is chosen).
     * 2. Audits required Display Over Other Apps permission.
     * 3. Prompts clearly if permission is missing (with option to proceed without overlay).
     * 4. Starts tracked gaming session & SmartGamingManager session.
     * 5. Applies selected GAMEBOOST profile via authorized DeviceControlEngine APIs.
     * 6. Starts GamingOverlayService safely with duplicate protection & theme/opacity preserved.
     * 7. Launches selected game if installed and not already in foreground.
     */
    fun startGamingSessionWithOverlay(
        context: Context,
        selectedProfile: GameProfile?,
        selectedGameTitle: String,
        profileType: ProfileType,
        allowWithoutOverlay: Boolean = false,
        allowWithoutGameLaunch: Boolean = false
    ): Boolean {
        val targetTitle = selectedProfile?.name
            ?: selectedGameTitle.ifBlank { "Global Gaming Mode" }
        val targetPackage = selectedProfile?.packageName.orEmpty()

        // 1. Validate selected game if user picked a concrete package
        if (targetPackage.isNotBlank() && !allowWithoutGameLaunch) {
            val isInstalled = GameSpaceManager.isPackageInstalled(context, targetPackage) &&
                context.packageManager.getLaunchIntentForPackage(targetPackage) != null
            if (!isInstalled) {
                _sessionLaunchPrompt.update {
                    SessionLaunchPrompt.GameNotInstalled(
                        gameTitle = targetTitle,
                        packageName = targetPackage,
                        profileType = profileType
                    )
                }
                return false
            }
        }

        // 2 & 3. Check required Display Over Other Apps permission
        val hasOverlayPerm = PermissionManager.isOverlayPermissionGranted(context)
        if (!hasOverlayPerm && !allowWithoutOverlay) {
            _sessionLaunchPrompt.update {
                SessionLaunchPrompt.MissingOverlayPermission(
                    gameProfile = selectedProfile,
                    gameTitle = targetTitle,
                    profileType = profileType
                )
            }
            return false
        }

        _sessionLaunchPrompt.update { null }

        // 4. Sync target game/profile and start tracked session
        SmartGamingManager.setTargetGame(targetTitle, targetPackage)
        SmartGamingManager.setTargetProfile(profileType)
        _activeMonitoredGame.update { selectedProfile }

        startBenchmarkSession(
            context = context,
            label = "$targetTitle Session",
            gameTitle = targetTitle,
            packageName = targetPackage,
            graphicsSettings = selectedProfile?.recommendedGraphics ?: profileType.displayName,
            autopilotMode = profileType.displayName,
            fpsSource = "PowerManager Thermal Telemetry",
            testNotes = "Gaming session launched via Command Center (${profileType.displayName})."
        )

        // 5. Apply GAMEBOOST profile via authorized APIs
        viewModelScope.launch {
            SmartGamingManager.startSmartGamingSession(
                context = context,
                profileType = profileType,
                database = database,
                wasAutoDetected = false,
                onPermissionRequired = {}
            )
            refreshDeviceControlData(context)
        }

        // 6 & 7. Start GamingOverlayService safely (if overlay permitted)
        if (hasOverlayPerm) {
            val overlayResult = GamingOverlayService.startOverlay(context, autoStartedBySession = true)
            if (!overlayResult.started) {
                _sessionLaunchPrompt.update {
                    SessionLaunchPrompt.OverlayStartFailed(overlayResult.reason)
                }
            }
        }

        // 8. Launch selected game if installed and not already in foreground
        if (targetPackage.isNotBlank() && !allowWithoutGameLaunch) {
            val fgPkg = GameSpaceManager.getRecentForegroundPackage(context)
            if (fgPkg != targetPackage) {
                val launched = GameSpaceManager.launchGame(context, targetPackage)
                if (launched && selectedProfile != null) {
                    viewModelScope.launch {
                        gameSpaceRepository.saveProfile(
                            selectedProfile.copy(lastPlayedTimestamp = System.currentTimeMillis())
                        )
                    }
                }
            }
        }

        return true
    }

    fun launchGame(context: Context, profile: GameProfile, autoTrackSession: Boolean): Boolean {
        val success = GameSpaceManager.launchGame(context, profile.packageName)
        if (success) {
            // Sync target game across Autopilot and Smart Aim Optimizer
            SmartGamingManager.setTargetGame(profile.name, profile.packageName)
            SmartAimOptimizerEngine.setSelectedGame(profile.name)

            val mappedProfileType = DeviceControlEngine.mapGamePerformanceModeToProfile(profile.performanceMode)
            SmartGamingManager.setTargetProfile(mappedProfileType)

            // Load matching saved Smart Aim profile if available
            val matchingAimProfile = savedProfiles.value.firstOrNull {
                it.gameName.equals(profile.name, ignoreCase = true)
            }
            if (matchingAimProfile != null && smartAimControlState.value.isMasterEnabled) {
                SmartAimOptimizerEngine.setActiveProfile(matchingAimProfile)
            }

            viewModelScope.launch {
                val updated = profile.copy(lastPlayedTimestamp = System.currentTimeMillis())
                gameSpaceRepository.saveProfile(updated)

                if (SmartGamingManager.autopilotState.value.isAutopilotEnabled || autoTrackSession) {
                    SmartGamingManager.startSmartGamingSession(context, mappedProfileType, database) {}
                    refreshDeviceControlData(context)
                }
            }
            if (autoTrackSession) {
                if (PermissionManager.isOverlayPermissionGranted(context)) {
                    GamingOverlayService.startOverlay(context, autoStartedBySession = true)
                }
                if (!_isBenchmarking.value) {
                    _activeMonitoredGame.update { profile }
                    startBenchmarkSession(
                        context = context,
                        label = "${profile.name} Session",
                        gameTitle = profile.name,
                        packageName = profile.packageName,
                        graphicsSettings = profile.recommendedGraphics,
                        autopilotMode = if (SmartGamingManager.autopilotState.value.isAutopilotEnabled) mappedProfileType.displayName else "Manual",
                        fpsSource = "OEM / OS Telemetry (${profile.fpsLock} FPS Target)",
                        testNotes = "Launched directly from Gaming Space. Mode: ${profile.performanceMode.displayName}"
                    )
                }
            }
        }
        return success
    }

    // Benchmark Session Recorder
    fun startBenchmarkSession(
        context: Context,
        label: String,
        gameTitle: String,
        packageName: String = "",
        graphicsSettings: String = "Not Specified",
        autopilotMode: String = if (SmartGamingManager.autopilotState.value.isSessionActive) SmartGamingManager.autopilotState.value.activeProfile.displayName else "Manual",
        fpsSource: String = "None (FPS Unavailable)",
        observedAvgFps: Float? = null,
        observedMinFps: Float? = null,
        testNotes: String = ""
    ) {
        if (_isBenchmarking.value) return
        activeSessionLabel = label.ifBlank { "Gaming Session Benchmark" }
        activeGameTitle = gameTitle.ifBlank { "Mobile Game" }
        activePackageName = packageName
        activeGraphicsSettings = graphicsSettings.ifBlank { "Not Specified" }
        activeAutopilotMode = autopilotMode
        activeSessionStartTimeMs = System.currentTimeMillis()
        activeFpsSource = fpsSource
        activeObservedAvgFps = observedAvgFps
        activeObservedMinFps = observedMinFps
        activeTestNotes = testNotes

        val initialStats = DeviceMonitor.getDeviceStats(context)
        startBatteryLevel = initialStats.batteryLevelPercent
        startThermalStatus = initialStats.powerManagerThermalStatus

        currentSnapshots.clear()
        _benchmarkSeconds.update { 0 }
        _isBenchmarking.update { true }

        benchmarkJob = viewModelScope.launch {
            while (_isBenchmarking.value) {
                val cadenceMs = SmartGamingManager.autopilotState.value.monitoringIntervalMs.coerceIn(2000L, 15000L)
                delay(cadenceMs)
                val stats = DeviceMonitor.getDeviceStats(context)
                val net = _networkStats.value
                val elapsed = ((System.currentTimeMillis() - activeSessionStartTimeMs) / 1000L).toInt().coerceAtLeast(_benchmarkSeconds.value + 1)
                _benchmarkSeconds.update { elapsed }

                val protection = GamingProtectionEngine.evaluate(
                    stats = stats,
                    sessionSeconds = elapsed,
                    longSessionThresholdMinutes = overlayConfig.value.longSessionReminderMinutes
                )
                _protectionStatus.update { protection }
                SmartGamingManager.updateSessionTelemetry(
                    stats = stats,
                    sessionSeconds = elapsed,
                    longSessionReminderMinutes = overlayConfig.value.longSessionReminderMinutes
                )

                currentSnapshots.add(
                    TelemetrySnapshot(
                        elapsedSeconds = elapsed,
                        batteryTempC = stats.batteryTemperatureC,
                        thermalStatus = stats.powerManagerThermalStatus,
                        availRamGb = stats.availableMemoryGb,
                        pingMs = net.pingMs,
                        jitterMs = net.jitterMs,
                        refreshRateHz = stats.displayRefreshRate
                    )
                )
            }
        }
    }

    fun stopBenchmarkSession(
        context: Context,
        finalObservedAvgFps: Float? = null,
        finalObservedMinFps: Float? = null
    ) {
        if (!_isBenchmarking.value) return
        _isBenchmarking.update { false }
        benchmarkJob?.cancel()
        benchmarkJob = null
        _activeMonitoredGame.update { null }
        GamingOverlayService.stopAutoStartedOverlay(context)

        val snapshots = currentSnapshots.toList()
        if (snapshots.isEmpty()) {
            viewModelScope.launch {
                if (SmartGamingManager.autopilotState.value.isSessionActive) {
                    SmartGamingManager.stopSmartGamingSession(context, database)
                    refreshDeviceControlData(context)
                }
            }
            return
        }

        val endTime = System.currentTimeMillis()
        val endStats = DeviceMonitor.getDeviceStats(context)
        val startTemp = snapshots.first().batteryTempC
        val peakTemp = snapshots.maxOfOrNull { it.batteryTempC } ?: startTemp
        val avgRam = snapshots.map { it.availRamGb }.average().toFloat()
        val minRam = snapshots.minOfOrNull { it.availRamGb } ?: avgRam
        val avgPing = snapshots.filter { it.pingMs > 0 }.map { it.pingMs }.average().let { if (it.isNaN()) -1 else it.toInt() }
        val avgJitter = snapshots.filter { it.jitterMs >= 0 }.map { it.jitterMs }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val peakStatus = snapshots.maxByOrNull { it.batteryTempC }?.thermalStatus ?: endStats.powerManagerThermalStatus
        val throttling = peakStatus.contains("Severe") || peakStatus.contains("Critical") || peakStatus.contains("Moderate")

        val effectiveAvgFps = finalObservedAvgFps ?: activeObservedAvgFps
        val effectiveMinFps = finalObservedMinFps ?: activeObservedMinFps
        val frameTime = if (effectiveAvgFps != null && effectiveAvgFps > 0f) 1000f / effectiveAvgFps else null

        val peakTempText = if (peakTemp > 0f) "${peakTemp}°C" else "N/A"
        val conclusion = if (throttling) {
            "PowerManager signaled thermal throttling during this session ($peakStatus). Peak battery temp reached $peakTempText. If frame rate drops were observed, reducing GPU graphic settings and testing off-charger may help."
        } else {
            "Telemetry remained within normal operational ranges throughout. Peak battery temp was $peakTempText ($peakStatus) with ${String.format(java.util.Locale.US, "%.1f", minRam)} GB min free RAM."
        }

        val report = BenchmarkSessionReport(
            id = "session_${endTime}",
            sessionLabel = activeSessionLabel,
            gameTitle = activeGameTitle,
            packageName = activePackageName,
            graphicsSettings = activeGraphicsSettings,
            startTimeMs = if (activeSessionStartTimeMs > 0L) activeSessionStartTimeMs else endTime,
            endTimeMs = endTime,
            autopilotMode = activeAutopilotMode,
            durationSeconds = _benchmarkSeconds.value,
            startTempC = startTemp,
            peakTempC = peakTemp,
            startBatteryLevel = startBatteryLevel,
            endBatteryLevel = endStats.batteryLevelPercent,
            startThermalStatus = startThermalStatus,
            peakThermalStatus = peakStatus,
            avgAvailRamGb = String.format(java.util.Locale.US, "%.1f", avgRam).toFloat(),
            minAvailRamGb = String.format(java.util.Locale.US, "%.1f", minRam).toFloat(),
            avgPingMs = avgPing,
            avgJitterMs = avgJitter,
            displayRefreshRateHz = endStats.displayRefreshRate,
            fpsMeasurementSource = activeFpsSource,
            measuredAvgFps = effectiveAvgFps,
            measuredMinFps = effectiveMinFps,
            measuredFrameTimeMs = if (frameTime != null) String.format(java.util.Locale.US, "%.1f", frameTime).toFloat() else null,
            throttlingObserved = throttling,
            testNotes = activeTestNotes,
            conclusion = conclusion,
            snapshots = snapshots
        )

        viewModelScope.launch {
            gameSpaceRepository.saveSessionReport(report)
            if (SmartGamingManager.autopilotState.value.isSessionActive) {
                SmartGamingManager.stopSmartGamingSession(context, database)
                refreshDeviceControlData(context)
            }
            SmartAimOptimizerEngine.recordSessionFeedback(
                game = activeGameTitle,
                mode = smartAimControlState.value.activeMode,
                ratingNote = "Duration ${_benchmarkSeconds.value}s, Peak temp $peakTempText."
            )
        }
    }

    fun runBenchmarkLabWorkload(context: Context, workloadType: WorkloadType) {
        if (_isLabRunning.value) return
        _isLabRunning.update { true }
        _labProgress.update { 0f }
        _activeLabWorkload.update { workloadType.displayName }

        val initialStats = DeviceMonitor.getDeviceStats(context)
        _labTelemetryTemp.update { initialStats.batteryTemperatureC }
        _labTelemetryThermal.update { initialStats.powerManagerThermalStatus }

        labJob = viewModelScope.launch {
            val result = when (workloadType) {
                WorkloadType.CPU_MATH -> BenchmarkLabEngine.runCpuBenchmark(
                    context = context,
                    onProgress = { prog -> _labProgress.update { prog } },
                    onTelemetryUpdate = { temp, status ->
                        _labTelemetryTemp.update { temp }
                        _labTelemetryThermal.update { status }
                    }
                )
                WorkloadType.MEMORY_BANDWIDTH -> BenchmarkLabEngine.runMemoryBenchmark(
                    context = context,
                    onProgress = { prog -> _labProgress.update { prog } },
                    onTelemetryUpdate = { temp, status ->
                        _labTelemetryTemp.update { temp }
                        _labTelemetryThermal.update { status }
                    }
                )
                WorkloadType.GRAPHICS_CANVAS -> BenchmarkLabEngine.runGraphicsCanvasBenchmark(
                    context = context,
                    onProgress = { prog -> _labProgress.update { prog } },
                    onTelemetryUpdate = { temp, status ->
                        _labTelemetryTemp.update { temp }
                        _labTelemetryThermal.update { status }
                    }
                )
            }

            gameSpaceRepository.saveBenchmarkLabResult(result)
            _isLabRunning.update { false }
        }
    }

    fun cancelBenchmarkLab() {
        labJob?.cancel()
        labJob = null
        _isLabRunning.update { false }
    }

    fun clearBenchmarkLabHistory() {
        viewModelScope.launch {
            gameSpaceRepository.clearBenchmarkLabResults()
        }
    }

    // ==========================================
    // ADVANCED DEVICE CONTROL ENGINE STATE
    // ==========================================
    private val database = GameBoostDatabase.getDatabase(application)
    private val deviceControlDao = database.deviceControlDao()

    private val _deviceCapabilities = MutableStateFlow(DeviceCapabilities())
    val deviceCapabilities: StateFlow<DeviceCapabilities> = _deviceCapabilities.asStateFlow()

    private val _currentControlState = MutableStateFlow(SystemControlState())
    val currentControlState: StateFlow<SystemControlState> = _currentControlState.asStateFlow()

    val savedSystemState: StateFlow<SavedSystemStateEntity?> = deviceControlDao.getSavedSystemStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _profileApplyMessage = MutableStateFlow<String?>(null)
    val profileApplyMessage: StateFlow<String?> = _profileApplyMessage.asStateFlow()

    fun refreshDeviceControlData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val caps = DeviceControlEngine.detectCapabilities(context)
            val state = DeviceControlEngine.getCurrentControlState(context)
            _deviceCapabilities.update { caps }
            _currentControlState.update { state }
        }
    }

    fun applyOptimizationProfile(
        context: Context,
        profileType: ProfileType,
        onPermissionRequired: (String) -> Unit
    ) {
        viewModelScope.launch {
            val success = DeviceControlEngine.applyProfile(context, profileType, database, onPermissionRequired)
            refreshDeviceControlData(context)
            if (success) {
                _profileApplyMessage.update { "Applied ${profileType.displayName} successfully." }
            } else {
                _profileApplyMessage.update { "Profile applied partially. Authorization required for some system settings." }
            }
        }
    }

    fun restoreOriginalSystemSettings(context: Context) {
        viewModelScope.launch {
            val success = DeviceControlEngine.restoreOriginalSettings(context, database)
            refreshDeviceControlData(context)
            if (success) {
                _profileApplyMessage.update { "Restored pre-optimization system settings." }
            } else {
                _profileApplyMessage.update { "No saved original settings found to restore." }
            }
        }
    }

    fun updateBrightness(context: Context, mode: Int, level: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            DeviceControlEngine.setBrightness(context, mode, level)
            refreshDeviceControlData(context)
        }
    }

    fun updateScreenTimeout(context: Context, timeoutMs: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            DeviceControlEngine.setScreenTimeout(context, timeoutMs)
            refreshDeviceControlData(context)
        }
    }

    fun updateAutoRotate(context: Context, rotate: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            DeviceControlEngine.setAutoRotate(context, rotate)
            refreshDeviceControlData(context)
        }
    }

    fun updateDndFilter(context: Context, filter: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            DeviceControlEngine.setDndFilter(context, filter)
            refreshDeviceControlData(context)
        }
    }

    fun launchOemGamingSpace(context: Context): Boolean {
        val caps = _deviceCapabilities.value
        val info = caps.oemGamingSpace ?: return false
        return DeviceControlEngine.launchOemGamingSpace(context, info)
    }

    fun clearProfileApplyMessage() {
        _profileApplyMessage.update { null }
    }

    // ==========================================
    // SMART AIM & ROTATION OPTIMIZER STATE
    // ==========================================
    val smartAimControlState: StateFlow<SmartAimControlState> = SmartAimOptimizerEngine.controlState
    val gyroscopeState: StateFlow<GyroscopeTelemetry> = SmartAimOptimizerEngine.gyroscopeState

    private val _aimDeviceSpecs = MutableStateFlow<AimDeviceSpecs?>(null)
    val aimDeviceSpecs: StateFlow<AimDeviceSpecs?> = _aimDeviceSpecs.asStateFlow()

    fun initSmartAimEngine(context: Context) {
        try {
            SmartAimOptimizerEngine.initSensors(context)
            _aimDeviceSpecs.value = SmartAimOptimizerEngine.detectAimDeviceSpecs(context)
        } catch (_: Exception) {}
    }

    fun stopSmartAimSensors() {
        try {
            SmartAimOptimizerEngine.stopSensors()
        } catch (_: Exception) {}
    }

    fun setSmartAimMasterEnabled(enabled: Boolean) {
        SmartAimOptimizerEngine.setMasterEnabled(enabled)
    }

    fun setSmartAimMode(mode: AimOptimizationMode) {
        SmartAimOptimizerEngine.setOptimizationMode(mode)
    }

    fun setSmartAimGame(game: String) {
        SmartAimOptimizerEngine.setSelectedGame(game)
    }

    fun applyAimPreset(game: String, mode: AimOptimizationMode, dpiTier: Int = 400) {
        val preset = SmartAimOptimizerEngine.generatePresetProfile(game, mode, dpiTier)
        SmartAimOptimizerEngine.setActiveProfile(preset)
        viewModelScope.launch {
            aimRepository.saveProfile(preset)
        }
    }

    fun startGyroCalibration() {
        SmartAimOptimizerEngine.startGyroCalibration()
    }

    fun clearAimNotification() {
        SmartAimOptimizerEngine.clearNotification()
    }

    fun selectSensitivityProfile(profile: SensitivityProfile) {
        SmartAimOptimizerEngine.setActiveProfile(profile)
    }

    fun exportSensitivityProfilesJson(): String {
        return SmartAimOptimizerEngine.exportProfilesJson(savedProfiles.value)
    }

    fun importSensitivityProfilesJson(raw: String): Int {
        val parsed = SmartAimOptimizerEngine.importProfilesJson(raw)
        if (parsed.isEmpty()) return 0
        viewModelScope.launch {
            parsed.forEach { aimRepository.saveProfile(it) }
        }
        SmartAimOptimizerEngine.setActiveProfile(parsed.first())
        return parsed.size
    }

    // ==========================================
    // GAMING AUTOPILOT ENGINE INTEGRATION
    // ==========================================
    val autopilotState: StateFlow<AutopilotState> = SmartGamingManager.autopilotState
    val backgroundApps: StateFlow<List<BackgroundAppItem>> = SmartGamingManager.backgroundApps

    fun setAutopilotMasterEnabled(enabled: Boolean) {
        SmartGamingManager.setAutopilotMasterEnabled(enabled)
    }

    fun setAutopilotProfile(profile: ProfileType) {
        SmartGamingManager.setTargetProfile(profile)
        val targetPkg = SmartGamingManager.autopilotState.value.targetGamePackage
        if (targetPkg.isNotBlank()) {
            val matchingGame = gameProfiles.value.firstOrNull { it.packageName == targetPkg }
            if (matchingGame != null && DeviceControlEngine.mapGamePerformanceModeToProfile(matchingGame.performanceMode) != profile) {
                val mappedPerfMode = DeviceControlEngine.mapProfileToGamePerformanceMode(profile)
                viewModelScope.launch {
                    gameSpaceRepository.saveProfile(
                        matchingGame.copy(
                            performanceMode = mappedPerfMode,
                            previousMode = matchingGame.performanceMode,
                            previousFpsLock = matchingGame.fpsLock
                        )
                    )
                }
            }
        }
    }

    fun selectAutopilotGame(profile: GameProfile) {
        SmartGamingManager.setTargetGame(profile.name, profile.packageName)
        SmartGamingManager.setTargetProfile(
            DeviceControlEngine.mapGamePerformanceModeToProfile(profile.performanceMode)
        )
    }

    fun setAutopilotGame(title: String, packageName: String) {
        SmartGamingManager.setTargetGame(title, packageName)
        val matchingGame = gameProfiles.value.firstOrNull { it.packageName == packageName }
        if (matchingGame != null) {
            SmartGamingManager.setTargetProfile(
                DeviceControlEngine.mapGamePerformanceModeToProfile(matchingGame.performanceMode)
            )
        }
    }

    fun startAutopilotSession(context: Context, profile: ProfileType, onPermissionRequired: (String) -> Unit = {}) {
        viewModelScope.launch {
            SmartGamingManager.startSmartGamingSession(context, profile, database, wasAutoDetected = false, onPermissionRequired = onPermissionRequired)
            refreshDeviceControlData(context)
            if (PermissionManager.isOverlayPermissionGranted(context)) {
                GamingOverlayService.startOverlay(context, autoStartedBySession = true)
            }
        }
    }

    fun stopAutopilotSession(context: Context) {
        GamingOverlayService.stopAutoStartedOverlay(context)
        viewModelScope.launch {
            SmartGamingManager.stopSmartGamingSession(context, database)
            refreshDeviceControlData(context)
        }
    }

    suspend fun evaluateForegroundGameDetection(context: Context) {
        SmartGamingManager.evaluateForegroundDetection(
            context = context,
            knownProfiles = gameProfiles.value,
            database = database,
            selfPackage = context.packageName
        )
        refreshDeviceControlData(context)
    }

    fun refreshUsageAccessStatus(context: Context) {
        SmartGamingManager.refreshUsageAccessStatus(context)
    }

    fun refreshBackgroundApps(context: Context) {
        SmartGamingManager.refreshBackgroundProcesses(context)
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        SmartGamingManager.openAppDetailsSettings(context, packageName)
    }

    fun updateOverlayConfig(context: Context, transform: (OverlayConfig) -> OverlayConfig) {
        OverlayPreferences.update(context, transform)
    }
}

