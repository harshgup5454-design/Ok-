package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val iconBitmap: ImageBitmap? = null,
    val isGameCategory: Boolean = false
)

object GameSpaceManager {

    private val KNOWN_GAME_PACKAGE_HINTS = setOf(
        "com.pubg.imobile",
        "com.tencent.ig",
        "com.dts.freefiremax",
        "com.dts.freefireth",
        "com.activision.callofduty.shooter",
        "com.miHoYo.GenshinImpact",
        "com.gameloft.android.ANGL.GloftA9",
        "com.mojang.minecraftpe",
        "com.mobile.legends",
        "com.supercell.brawlstars",
        "com.supercell.clashroyale",
        "com.roblox.client",
        "com.riotgames.league.wildrift",
        "com.ea.gp.fifamobile"
    )

    /**
     * Safely queries launchable applications from Android PackageManager without root
     * or sensitive QUERY_ALL_PACKAGES permission, prioritizing ApplicationInfo.CATEGORY_GAME.
     */
    fun getInstalledLaunchableApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val myPackage = context.packageName
        val apps = mutableListOf<InstalledAppItem>()

        for (info in resolveInfoList) {
            val pkg = info.activityInfo.packageName
            if (pkg == myPackage) continue

            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg
            }

            val isGame = try {
                val appInfo = info.activityInfo.applicationInfo
                val categoryMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else false
                @Suppress("DEPRECATION")
                val legacyFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                categoryMatch || legacyFlag || KNOWN_GAME_PACKAGE_HINTS.contains(pkg)
            } catch (_: Exception) {
                KNOWN_GAME_PACKAGE_HINTS.contains(pkg)
            }

            val iconBitmap = try {
                val drawable = info.loadIcon(pm)
                val width = drawable.intrinsicWidth.coerceIn(48, 96)
                val height = drawable.intrinsicHeight.coerceIn(48, 96)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } catch (_: Throwable) {
                null
            }

            apps.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = label,
                    iconBitmap = iconBitmap,
                    isGameCategory = isGame
                )
            )
        }

        return apps.distinctBy { it.packageName }
            .sortedWith(compareByDescending<InstalledAppItem> { it.isGameCategory }.thenBy { it.appName.lowercase() })
    }

    /**
     * Checks if a package is currently installed on the device (subject to Android package visibility).
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves app icon dynamically from package manager if available.
     */
    fun getAppIcon(context: Context, packageName: String): ImageBitmap? {
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val width = drawable.intrinsicWidth.coerceIn(48, 96)
            val height = drawable.intrinsicHeight.coerceIn(48, 96)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Launches the game using its standard Android launch intent.
     */
    fun launchGame(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
