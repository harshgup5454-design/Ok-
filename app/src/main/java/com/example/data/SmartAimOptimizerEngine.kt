package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class AimOptimizationMode(val displayName: String, val tag: String) {
    ACCURACY("Accuracy Focus", "ACCURACY"),
    FAST_ROTATION("Fast Rotation", "FAST_ROTATION"),
    BALANCED("Balanced Control", "BALANCED")
}

data class GyroscopeTelemetry(
    val isAvailable: Boolean = false,
    val sensorName: String = "No Gyroscope Detected",
    val xRadPerSec: Float = 0f,
    val yRadPerSec: Float = 0f,
    val zRadPerSec: Float = 0f,
    val stabilityIndex: Float = 100f,
    val isCalibrating: Boolean = false,
    val calibrationOffsetZ: Float = 0f,
    val calibrated: Boolean = false
)

data class AimDeviceSpecs(
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val refreshRateHz: Float = 60f,
    val touchMaxPointers: Int = 5,
    val hasGyroscope: Boolean = false,
    val gyroSensorName: String = "None",
    val screenResolutionPx: String = "1080x2400",
    val screenDpi: Int = 400
)

data class SmartAimControlState(
    val isMasterEnabled: Boolean = true,
    val activeMode: AimOptimizationMode = AimOptimizationMode.BALANCED,
    val selectedGame: String = "Free Fire MAX",
    val activeProfileId: String? = null,
    val activeProfileName: String = "Free Fire MAX (Balanced)",
    val lastSessionFeedback: String = "Standard sensitivity baseline ready. Practice in GAMEBOOST Aim Lab.",
    val lastSessionTimestamp: Long = 0L,
    val autoAppliedNotification: String? = null
)

object SmartAimOptimizerEngine : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null

    private val _gyroscopeState = MutableStateFlow(GyroscopeTelemetry())
    val gyroscopeState: StateFlow<GyroscopeTelemetry> = _gyroscopeState.asStateFlow()

    private val _controlState = MutableStateFlow(SmartAimControlState())
    val controlState: StateFlow<SmartAimControlState> = _controlState.asStateFlow()

    private var calibrationSamples = mutableListOf<Float>()
    private var isCalibrating = false

    fun initSensors(context: Context) {
        if (sensorManager == null) {
            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager = sm
            gyroSensor = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val hasGyro = gyroSensor != null
            _gyroscopeState.value = _gyroscopeState.value.copy(
                isAvailable = hasGyro,
                sensorName = gyroSensor?.name ?: "No hardware gyroscope found"
            )
            if (hasGyro) {
                sm?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopSensors() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }

    fun detectAimDeviceSpecs(context: Context): AimDeviceSpecs {
        val metrics = DisplayMetrics()
        try {
            val resMetrics = context.resources.displayMetrics
            metrics.setTo(resMetrics)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.maximumWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    metrics.widthPixels = bounds.width()
                    metrics.heightPixels = bounds.height()
                }
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.getRealMetrics(metrics)
            }
        } catch (_: Exception) {}

        val refreshRate = try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
        } catch (_: Exception) { 60f }

        val pm = context.packageManager
        val maxPointers = when {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> 10
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> 5
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> 2
            else -> 1
        }

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        return AimDeviceSpecs(
            deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            refreshRateHz = refreshRate,
            touchMaxPointers = maxPointers,
            hasGyroscope = gyro != null,
            gyroSensorName = gyro?.name ?: "Hardware Gyroscope Unavailable",
            screenResolutionPx = "${metrics.widthPixels} x ${metrics.heightPixels}",
            screenDpi = metrics.densityDpi
        )
    }

    fun setMasterEnabled(enabled: Boolean) {
        _controlState.value = _controlState.value.copy(
            isMasterEnabled = enabled,
            autoAppliedNotification = if (enabled) "Smart Aim & Rotation Optimizer Activated" else "Optimizer Disengaged"
        )
    }

    fun setOptimizationMode(mode: AimOptimizationMode) {
        _controlState.value = _controlState.value.copy(
            activeMode = mode,
            autoAppliedNotification = "Switched to ${mode.displayName}"
        )
    }

    fun setSelectedGame(game: String) {
        _controlState.value = _controlState.value.copy(
            selectedGame = game,
            activeProfileName = "$game (${_controlState.value.activeMode.displayName})"
        )
    }

    fun setActiveProfile(profile: SensitivityProfile) {
        _controlState.value = _controlState.value.copy(
            activeProfileId = profile.id,
            activeProfileName = profile.profileName,
            selectedGame = profile.gameName,
            lastSessionFeedback = "Profile '${profile.profileName}' applied. Manually copy values into ${profile.gameName} sensitivity menu."
        )
    }

    fun startGyroCalibration() {
        if (!_gyroscopeState.value.isAvailable) return
        isCalibrating = true
        calibrationSamples.clear()
        _gyroscopeState.value = _gyroscopeState.value.copy(isCalibrating = true)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val rawMagnitude = sqrt(x * x + y * y + z * z)
            // stability index: lower motion = higher stability
            val stability = (100f - (rawMagnitude * 25f)).coerceIn(10f, 100f)

            if (isCalibrating) {
                calibrationSamples.add(z)
                if (calibrationSamples.size >= 50) {
                    val avgZOffset = calibrationSamples.average().toFloat()
                    isCalibrating = false
                    _gyroscopeState.value = _gyroscopeState.value.copy(
                        isCalibrating = false,
                        calibrationOffsetZ = avgZOffset,
                        calibrated = true
                    )
                }
            }

            val offset = _gyroscopeState.value.calibrationOffsetZ
            _gyroscopeState.value = _gyroscopeState.value.copy(
                xRadPerSec = x,
                yRadPerSec = y,
                zRadPerSec = z - offset,
                stabilityIndex = stability
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun generatePresetProfile(game: String, mode: AimOptimizationMode, dpiTier: Int = 400): SensitivityProfile {
        val baseMultiplier = when (mode) {
            AimOptimizationMode.ACCURACY -> 0.78f // Lower sensitivity for steady micro-adjustments and precision headshots
            AimOptimizationMode.FAST_ROTATION -> 1.45f // High sensitivity for instant 180/360 turns and rapid drag headshots
            AimOptimizationMode.BALANCED -> 1.0f // Well-rounded camera and recoil control
        }

        val dpiScale = dpiTier.coerceIn(240, 960) / 400f
        val netMultiplier = baseMultiplier / dpiScale

        val baseGeneral = when (game) {
            "Free Fire MAX" -> (92 * netMultiplier).toInt().coerceIn(30, 200)
            "BGMI / PUBG Mobile" -> (68 * netMultiplier).toInt().coerceIn(20, 160)
            "Call of Duty: Mobile" -> (75 * netMultiplier).toInt().coerceIn(25, 170)
            else -> (80 * netMultiplier).toInt().coerceIn(25, 160)
        }

        val redDot = when (mode) {
            AimOptimizationMode.ACCURACY -> (baseGeneral * 0.85f).toInt()
            AimOptimizationMode.FAST_ROTATION -> (baseGeneral * 0.95f).toInt()
            AimOptimizationMode.BALANCED -> (baseGeneral * 0.90f).toInt()
        }.coerceIn(10, 190)

        val scope2x = (baseGeneral * 0.78f).toInt().coerceIn(10, 180)
        val scope4x = (baseGeneral * 0.62f).toInt().coerceIn(10, 160)
        val sniper = when (mode) {
            AimOptimizationMode.ACCURACY -> (baseGeneral * 0.38f).toInt() // Ultra steady sniper
            AimOptimizationMode.FAST_ROTATION -> (baseGeneral * 0.58f).toInt()
            AimOptimizationMode.BALANCED -> (baseGeneral * 0.45f).toInt()
        }.coerceIn(5, 120)

        val freeLook = when (mode) {
            AimOptimizationMode.FAST_ROTATION -> (baseGeneral * 1.25f).toInt()
            else -> (baseGeneral * 1.05f).toInt()
        }.coerceIn(30, 200)

        val rationale = when (mode) {
            AimOptimizationMode.ACCURACY -> "Accuracy Focus Formula: Lower general sensitivity ($baseGeneral) and dampened scope factors reduce crosshair jitter during micro-tracking. Must be manually applied in $game settings."
            AimOptimizationMode.FAST_ROTATION -> "Fast Rotation Formula: High velocity camera response ($baseGeneral) enables rapid 180° flick rotations and close-range tracking. Trade-off: Requires finer finger control for distant targets."
            AimOptimizationMode.BALANCED -> "Balanced Formula: Optimal camera tracking and scope recoil recovery ($baseGeneral) suited for all-round engagement ranges."
        }

        return SensitivityProfile(
            id = "sens_${mode.tag.lowercase()}_${System.currentTimeMillis()}",
            profileName = "$game - ${mode.displayName}",
            gameName = game,
            sensitivityStyle = mode.displayName,
            dpiTier = dpiTier,
            general = baseGeneral,
            redDot = redDot,
            scope2x = scope2x,
            scope4x = scope4x,
            sniper = sniper,
            freeLook = freeLook,
            mathematicalRationale = rationale
        )
    }

    fun recordSessionFeedback(game: String, mode: AimOptimizationMode, ratingNote: String) {
        _controlState.value = _controlState.value.copy(
            lastSessionFeedback = "Session completed with $game in ${mode.displayName}. $ratingNote",
            lastSessionTimestamp = System.currentTimeMillis()
        )
    }

    fun clearNotification() {
        _controlState.value = _controlState.value.copy(autoAppliedNotification = null)
    }

    fun exportProfilesJson(profiles: List<SensitivityProfile>): String {
        val array = org.json.JSONArray()
        profiles.forEach { p ->
            val obj = org.json.JSONObject().apply {
                put("profileName", p.profileName)
                put("gameName", p.gameName)
                put("sensitivityStyle", p.sensitivityStyle)
                put("dpiTier", p.dpiTier)
                put("general", p.general)
                put("redDot", p.redDot)
                put("scope2x", p.scope2x)
                put("scope4x", p.scope4x)
                put("sniper", p.sniper)
                put("freeLook", p.freeLook)
                put("mathematicalRationale", p.mathematicalRationale)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun importProfilesJson(raw: String): List<SensitivityProfile> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val array = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else {
                org.json.JSONArray().apply { put(org.json.JSONObject(trimmed)) }
            }
            val result = mutableListOf<SensitivityProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("profileName").ifBlank { continue }
                val game = obj.optString("gameName", "Free Fire MAX")
                result.add(
                    SensitivityProfile(
                        id = "sens_imp_${System.currentTimeMillis()}_$i",
                        profileName = name,
                        gameName = game,
                        sensitivityStyle = obj.optString("sensitivityStyle", "Custom"),
                        dpiTier = obj.optInt("dpiTier", 400).coerceIn(240, 960),
                        general = obj.optInt("general", 90).coerceIn(10, 200),
                        redDot = obj.optInt("redDot", 85).coerceIn(10, 200),
                        scope2x = obj.optInt("scope2x", 75).coerceIn(10, 200),
                        scope4x = obj.optInt("scope4x", 60).coerceIn(10, 200),
                        sniper = obj.optInt("sniper", 45).coerceIn(5, 200),
                        freeLook = obj.optInt("freeLook", 95).coerceIn(10, 200),
                        mathematicalRationale = obj.optString("mathematicalRationale", "Imported sensitivity profile.")
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
