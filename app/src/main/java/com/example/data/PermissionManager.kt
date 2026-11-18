package com.example.data

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class AppPermissionType {
    OVERLAY,
    NOTIFICATIONS,
    WRITE_SETTINGS,
    DND_ACCESS
}

enum class PermissionStatus(val label: String) {
    GRANTED("Granted"),
    NOT_GRANTED("Not Granted"),
    NOT_APPLICABLE("Not Applicable")
}

data class PermissionDescriptor(
    val type: AppPermissionType,
    val title: String,
    val featurePurpose: String,
    val rationale: String,
    val settingsStepHint: String,
    val isRequiredForOverlay: Boolean,
    val isRuntimePermission: Boolean,
    val status: PermissionStatus,
    val isPermanentlyDenied: Boolean = false
)

data class PermissionAuditResult(
    val items: List<PermissionDescriptor>
) {
    val applicableItems: List<PermissionDescriptor>
        get() = items.filter { it.status != PermissionStatus.NOT_APPLICABLE }

    val grantedCount: Int
        get() = applicableItems.count { it.status == PermissionStatus.GRANTED }

    val totalApplicableCount: Int
        get() = applicableItems.size

    val hasOverlayPermission: Boolean
        get() = items.firstOrNull { it.type == AppPermissionType.OVERLAY }?.status != PermissionStatus.NOT_GRANTED

    val hasNotificationPermission: Boolean
        get() = items.firstOrNull { it.type == AppPermissionType.NOTIFICATIONS }?.status != PermissionStatus.NOT_GRANTED

    val hasWriteSettings: Boolean
        get() = items.firstOrNull { it.type == AppPermissionType.WRITE_SETTINGS }?.status != PermissionStatus.NOT_GRANTED

    val hasDndAccess: Boolean
        get() = items.firstOrNull { it.type == AppPermissionType.DND_ACCESS }?.status != PermissionStatus.NOT_GRANTED
}

object PermissionManager {
    private const val PREF_NAME = "gameboost_permission_prefs"
    private const val KEY_NOTIF_REQUESTED = "notif_permission_requested"

    fun auditPermissions(
        context: Context,
        activity: Activity? = null,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): PermissionAuditResult {
        val overlayStatus = getPermissionStatus(context, AppPermissionType.OVERLAY, sdkInt)
        val notifStatus = getPermissionStatus(context, AppPermissionType.NOTIFICATIONS, sdkInt)
        val writeStatus = getPermissionStatus(context, AppPermissionType.WRITE_SETTINGS, sdkInt)
        val dndStatus = getPermissionStatus(context, AppPermissionType.DND_ACCESS, sdkInt)

        val notifPermanentlyDenied = if (
            notifStatus == PermissionStatus.NOT_GRANTED &&
            sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            wasNotificationPermissionRequested(context)
        ) {
            if (activity != null) {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                false
            }
        } else {
            false
        }

        val list = listOf(
            PermissionDescriptor(
                type = AppPermissionType.OVERLAY,
                title = "Display Over Other Apps",
                featurePurpose = "Floating Gaming Sidebar",
                rationale = "Required to display the floating gaming handle and quick telemetry sidebar while you play a game.",
                settingsStepHint = "In Android Settings, locate ZeroX Boost and enable 'Allow display over other apps'.",
                isRequiredForOverlay = true,
                isRuntimePermission = false,
                status = overlayStatus
            ),
            PermissionDescriptor(
                type = AppPermissionType.NOTIFICATIONS,
                title = "Notifications",
                featurePurpose = "Foreground Overlay Status",
                rationale = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                    "Shows the ongoing foreground service notification required by Android while the floating gaming sidebar is running."
                } else {
                    "Notification runtime permission is handled automatically on Android 12 and earlier."
                },
                settingsStepHint = "Allow ZeroX Boost notifications so the persistent sidebar service notification can appear cleanly.",
                isRequiredForOverlay = false,
                isRuntimePermission = sdkInt >= Build.VERSION_CODES.TIRAMISU,
                status = notifStatus,
                isPermanentlyDenied = notifPermanentlyDenied
            ),
            PermissionDescriptor(
                type = AppPermissionType.WRITE_SETTINGS,
                title = "Modify System Settings",
                featurePurpose = "Optional Display & Rotation Profiles",
                rationale = "Optional. Needed only if you want ZeroX Boost profiles to apply screen brightness, screen timeout, and auto-rotate lock, then restore them when sessions end.",
                settingsStepHint = "Enable 'Allow modifying system settings' for ZeroX Boost.",
                isRequiredForOverlay = false,
                isRuntimePermission = false,
                status = writeStatus
            ),
            PermissionDescriptor(
                type = AppPermissionType.DND_ACCESS,
                title = "Do Not Disturb Access",
                featurePurpose = "Optional Call & Alert Quieting",
                rationale = "Optional. Needed only if you want gaming profiles to switch Do Not Disturb mode during a session and restore your previous state afterward.",
                settingsStepHint = "Find ZeroX Boost under Do Not Disturb access and toggle it on.",
                isRequiredForOverlay = false,
                isRuntimePermission = false,
                status = dndStatus
            )
        )
        return PermissionAuditResult(list)
    }

    fun getPermissionStatus(
        context: Context,
        type: AppPermissionType,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): PermissionStatus {
        return when (type) {
            AppPermissionType.OVERLAY -> {
                if (sdkInt < Build.VERSION_CODES.M) {
                    PermissionStatus.NOT_APPLICABLE
                } else {
                    val allowed = try {
                        Settings.canDrawOverlays(context)
                    } catch (_: Exception) {
                        false
                    }
                    if (allowed) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
                }
            }
            AppPermissionType.NOTIFICATIONS -> {
                if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
                    PermissionStatus.NOT_APPLICABLE
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
                }
            }
            AppPermissionType.WRITE_SETTINGS -> {
                if (sdkInt < Build.VERSION_CODES.M) {
                    PermissionStatus.NOT_APPLICABLE
                } else {
                    val allowed = try {
                        Settings.System.canWrite(context)
                    } catch (_: Exception) {
                        false
                    }
                    if (allowed) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
                }
            }
            AppPermissionType.DND_ACCESS -> {
                if (sdkInt < Build.VERSION_CODES.M) {
                    PermissionStatus.NOT_APPLICABLE
                } else {
                    val allowed = try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.isNotificationPolicyAccessGranted == true
                    } catch (_: Exception) {
                        false
                    }
                    if (allowed) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
                }
            }
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean =
        getPermissionStatus(context, AppPermissionType.OVERLAY) != PermissionStatus.NOT_GRANTED

    fun markNotificationPermissionRequested(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIF_REQUESTED, true)
                .apply()
        } catch (_: Exception) {}
    }

    fun wasNotificationPermissionRequested(context: Context): Boolean {
        return try {
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIF_REQUESTED, false)
        } catch (_: Exception) {
            false
        }
    }

    fun clearNotificationRequestTracking(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NOTIF_REQUESTED)
                .apply()
        } catch (_: Exception) {}
    }

    fun buildSettingsIntents(context: Context, type: AppPermissionType): List<Intent> {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = mutableListOf<Intent>()
        when (type) {
            AppPermissionType.OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
                    intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }
            AppPermissionType.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intents.add(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                }
            }
            AppPermissionType.WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intents.add(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri))
                    intents.add(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                }
            }
            AppPermissionType.DND_ACCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intents.add(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        return intents.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    fun openPermissionSettings(context: Context, type: AppPermissionType): Boolean {
        val candidates = buildSettingsIntents(context, type)
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try fallback intent
            }
        }
        return false
    }
}
