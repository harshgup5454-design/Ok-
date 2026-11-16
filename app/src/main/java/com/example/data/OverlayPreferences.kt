package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayConfig(
    val triggerPosition: String = "Center", // Top, Center, Bottom
    val triggerWidthDp: Int = 24,           // 16, 24, 32
    val panelOpacity: Float = 0.94f,        // 0.50f .. 1.0f
    val accentTheme: String = "Cyan",       // Cyan, Purple, Green, OLED
    val defaultCompactMode: Boolean = false,
    val autoDetectForegroundGame: Boolean = false,
    val longSessionReminderMinutes: Int = 45
)

object OverlayPreferences {
    private const val PREFS_NAME = "gameboost_overlay_prefs"
    private const val KEY_POS = "trigger_position"
    private const val KEY_WIDTH = "trigger_width_dp"
    private const val KEY_OPACITY = "panel_opacity"
    private const val KEY_ACCENT = "accent_theme"
    private const val KEY_COMPACT = "default_compact_mode"
    private const val KEY_AUTO_DETECT = "auto_detect_foreground_game"
    private const val KEY_SESSION_REMINDER = "long_session_reminder_min"

    private val _config = MutableStateFlow(OverlayConfig())
    val config: StateFlow<OverlayConfig> = _config.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _config.value = OverlayConfig(
                triggerPosition = prefs.getString(KEY_POS, "Center") ?: "Center",
                triggerWidthDp = prefs.getInt(KEY_WIDTH, 24).coerceIn(16, 36),
                panelOpacity = prefs.getFloat(KEY_OPACITY, 0.94f).coerceIn(0.5f, 1.0f),
                accentTheme = prefs.getString(KEY_ACCENT, "Cyan") ?: "Cyan",
                defaultCompactMode = prefs.getBoolean(KEY_COMPACT, false),
                autoDetectForegroundGame = prefs.getBoolean(KEY_AUTO_DETECT, false),
                longSessionReminderMinutes = prefs.getInt(KEY_SESSION_REMINDER, 45).coerceIn(15, 180)
            )
            loaded = true
        } catch (_: Exception) {}
    }

    fun update(context: Context, transform: (OverlayConfig) -> OverlayConfig) {
        load(context)
        val updated = transform(_config.value)
        _config.value = updated
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_POS, updated.triggerPosition)
                .putInt(KEY_WIDTH, updated.triggerWidthDp)
                .putFloat(KEY_OPACITY, updated.panelOpacity)
                .putString(KEY_ACCENT, updated.accentTheme)
                .putBoolean(KEY_COMPACT, updated.defaultCompactMode)
                .putBoolean(KEY_AUTO_DETECT, updated.autoDetectForegroundGame)
                .putInt(KEY_SESSION_REMINDER, updated.longSessionReminderMinutes)
                .apply()
        } catch (_: Exception) {}
    }
}
