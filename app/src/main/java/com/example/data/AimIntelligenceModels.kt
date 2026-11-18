package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SensitivityProfile(
    val id: String = "sens_${System.currentTimeMillis()}",
    val profileName: String,
    val gameName: String,
    val sensitivityStyle: String, // "Low Sensitivity / Arm Swipes", "Balanced", "High Sensitivity / Thumb Swipes"
    val dpiTier: Int = 400,
    val general: Int,
    val redDot: Int,
    val scope2x: Int,
    val scope4x: Int,
    val sniper: Int,
    val freeLook: Int,
    val mathematicalRationale: String
)

data class RotationCalibrationAttempt(
    val id: String,
    val timestamp: Long,
    val swipeDistancePx: Float,
    val durationMs: Long,
    val avgSpeedPxPerSec: Float,
    val consistencyScore: Float, // 0-100%
    val recommendedStyle: String,
    val recommendedGeneralSensitivity: Int,
    val advice: String
)

data class AimTrainingSessionStats(
    val id: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val difficulty: String, // Easy, Medium, Hard, Expert
    val targetSize: String, // Small, Medium, Large
    val targetMovement: String, // Stationary, Moving (Drift), Pulse
    val targetsSpawned: Int,
    val targetsHit: Int,
    val accuracyPercent: Float,
    val avgReactionTimeMs: Int,
    val bestStreak: Int,
    val missCount: Int,
    val score: Int
)

data class HudAdvice(
    val title: String,
    val recommendation: String,
    val technicalRationale: String
)

class AimIntelligenceRepository(context: Context) {
    private val dao = GameBoostDatabase.getDatabase(context).aimIntelligenceDao()

    val savedProfilesFlow: Flow<List<SensitivityProfile>> = dao.getAllProfilesFlow().map { list ->
        if (list.isEmpty()) {
            val defaultEntity = SensitivityProfileEntity(
                id = "default_1",
                profileName = "Balanced BGMI Pro",
                gameName = "BGMI / PUBG Mobile",
                sensitivityStyle = "Balanced",
                dpiTier = 400,
                general = 65,
                redDot = 58,
                scope2x = 48,
                scope4x = 35,
                sniper = 25,
                freeLook = 85,
                mathematicalRationale = "Optimized for standard 400 DPI thumb sweep with moderate recoil pull-down stability."
            )
            listOf(defaultEntity.toDomain())
        } else {
            list.map { it.toDomain() }
        }
    }

    val calibrationHistoryFlow: Flow<List<RotationCalibrationAttempt>> = dao.getAllCalibrationsFlow().map { list ->
        list.map { it.toDomain() }
    }

    val trainingHistoryFlow: Flow<List<AimTrainingSessionStats>> = dao.getAllTrainingSessionsFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveProfile(profile: SensitivityProfile) {
        dao.insertProfile(profile.toEntity())
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteProfile(id)
    }

    suspend fun duplicateProfile(profile: SensitivityProfile): SensitivityProfile {
        val copy = profile.copy(
            id = "sens_${System.currentTimeMillis()}",
            profileName = "${profile.profileName} (Copy)"
        )
        dao.insertProfile(copy.toEntity())
        return copy
    }

    suspend fun saveCalibrationAttempt(attempt: RotationCalibrationAttempt) {
        dao.insertCalibration(attempt.toEntity())
    }

    suspend fun saveTrainingSession(stats: AimTrainingSessionStats) {
        dao.insertTrainingSession(stats.toEntity())
    }

    companion object {
        fun calculateSensitivity(gameName: String, style: String, dpiTier: Int): SensitivityProfile {
            val multiplier = when (style) {
                "Low Sensitivity / Arm Swipes" -> 0.8f
                "High Sensitivity / Thumb Swipes" -> 1.35f
                else -> 1.0f
            }

            // DPI Scaling Formula: normalizes device DPI relative to standard 400 DPI baseline
            val dpiFactor = dpiTier.coerceIn(240, 960) / 400f
            val adjustedMultiplier = multiplier / dpiFactor

            val baseGen = when (gameName) {
                "BGMI / PUBG Mobile" -> (65 * adjustedMultiplier).toInt().coerceIn(15, 130)
                "Free Fire MAX" -> (85 * adjustedMultiplier).toInt().coerceIn(25, 150)
                "Call of Duty: Mobile" -> (70 * adjustedMultiplier).toInt().coerceIn(20, 140)
                else -> (75 * adjustedMultiplier).toInt().coerceIn(20, 130)
            }

            val redDot = (baseGen * 0.9f).toInt().coerceIn(10, 140)
            val s2x = (baseGen * 0.75f).toInt().coerceIn(10, 120)
            val s4x = (baseGen * 0.6f).toInt().coerceIn(5, 100)
            val sniper = (baseGen * 0.45f).toInt().coerceIn(5, 80)
            val freeLook = (baseGen * 1.1f).toInt().coerceIn(30, 160)

            val rationale = "Calculated via transparent heuristic formula: Base camera scalar ($dpiTier DPI hardware tier) multiplied by movement style coefficient ($multiplier) for $gameName. NOTE: ZeroX Boost touch recommendations are mathematical baselines. Always verify and manually test sensitivity in your in-game training room."

            return SensitivityProfile(
                profileName = "$gameName ($style)",
                gameName = gameName,
                sensitivityStyle = style,
                dpiTier = dpiTier,
                general = baseGen,
                redDot = redDot,
                scope2x = s2x,
                scope4x = s4x,
                sniper = sniper,
                freeLook = freeLook,
                mathematicalRationale = rationale
            )
        }

        fun getHudAdviceList(): List<HudAdvice> {
            return listOf(
                HudAdvice(
                    title = "Fire Button Placement & Size",
                    recommendation = "Place primary fire button on the right thumb resting zone, scaled to 150–180% size with 40% opacity.",
                    technicalRationale = "Prevents mis-fires during tense recoil control pull-downs and ensures reliable touch contact area."
                ),
                HudAdvice(
                    title = "Scope & ADS Button Positioning",
                    recommendation = "Position ADS (Aim Down Sights) on the upper-left quadrant for index-finger (claw) players, or right thumb near jump/crouch.",
                    technicalRationale = "Enables simultaneous movement, scoping, and firing without thumb travel delay."
                ),
                HudAdvice(
                    title = "Movement Joystick Deadzone",
                    recommendation = "Keep movement joystick radius medium-compact to reduce sprint locking delay.",
                    technicalRationale = "Minimizes initial touch registration deadzone when initiating sudden strafe maneuvers."
                ),
                HudAdvice(
                    title = "Crouch, Prone & Jump Controls",
                    recommendation = "Cluster jump, crouch, and prone buttons in an ergonomic triangle in the lower-right or upper-right screen area.",
                    technicalRationale = "Facilitates fluid drop-shots and bunny-hopping without lifting aiming thumb."
                )
            )
        }
    }

    // Mapping extensions
    private fun SensitivityProfileEntity.toDomain() = SensitivityProfile(
        id = id,
        profileName = profileName,
        gameName = gameName,
        sensitivityStyle = sensitivityStyle,
        dpiTier = dpiTier,
        general = general,
        redDot = redDot,
        scope2x = scope2x,
        scope4x = scope4x,
        sniper = sniper,
        freeLook = freeLook,
        mathematicalRationale = mathematicalRationale
    )

    private fun SensitivityProfile.toEntity() = SensitivityProfileEntity(
        id = id,
        profileName = profileName,
        gameName = gameName,
        sensitivityStyle = sensitivityStyle,
        dpiTier = dpiTier,
        general = general,
        redDot = redDot,
        scope2x = scope2x,
        scope4x = scope4x,
        sniper = sniper,
        freeLook = freeLook,
        mathematicalRationale = mathematicalRationale
    )

    private fun RotationCalibrationEntity.toDomain() = RotationCalibrationAttempt(
        id = id,
        timestamp = timestamp,
        swipeDistancePx = swipeDistancePx,
        durationMs = durationMs,
        avgSpeedPxPerSec = avgSpeedPxPerSec,
        consistencyScore = consistencyScore,
        recommendedStyle = recommendedStyle,
        recommendedGeneralSensitivity = recommendedGeneralSensitivity,
        advice = advice
    )

    private fun RotationCalibrationAttempt.toEntity() = RotationCalibrationEntity(
        id = id,
        timestamp = timestamp,
        swipeDistancePx = swipeDistancePx,
        durationMs = durationMs,
        avgSpeedPxPerSec = avgSpeedPxPerSec,
        consistencyScore = consistencyScore,
        recommendedStyle = recommendedStyle,
        recommendedGeneralSensitivity = recommendedGeneralSensitivity,
        advice = advice
    )

    private fun AimTrainingSessionEntity.toDomain() = AimTrainingSessionStats(
        id = id,
        timestamp = timestamp,
        durationSeconds = durationSeconds,
        difficulty = difficulty,
        targetSize = targetSize,
        targetMovement = targetMovement,
        targetsSpawned = targetsSpawned,
        targetsHit = targetsHit,
        accuracyPercent = accuracyPercent,
        avgReactionTimeMs = avgReactionTimeMs,
        bestStreak = bestStreak,
        missCount = missCount,
        score = score
    )

    private fun AimTrainingSessionStats.toEntity() = AimTrainingSessionEntity(
        id = id,
        timestamp = timestamp,
        durationSeconds = durationSeconds,
        difficulty = difficulty,
        targetSize = targetSize,
        targetMovement = targetMovement,
        targetsSpawned = targetsSpawned,
        targetsHit = targetsHit,
        accuracyPercent = accuracyPercent,
        avgReactionTimeMs = avgReactionTimeMs,
        bestStreak = bestStreak,
        missCount = missCount,
        score = score
    )
}
