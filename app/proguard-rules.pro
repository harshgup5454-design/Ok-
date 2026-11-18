# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve source line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities, DAOs, and database classes
-keep class com.example.data.*Entity { *; }
-keep class com.example.data.*Dao { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Preserve data models, diagnostics, and engines used across ViewModels & Service
-keep class com.example.data.** { *; }
-keep class com.example.viewmodel.** { *; }
-keep class com.example.smartgaming.** { *; }
-keep class com.example.billing.** { *; }

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }

# Google Mobile Ads & User Messaging Platform (UMP)
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.android.ump.** { public *; }
-dontwarn android.webkit.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Keep foreground service & overlay state classes intact
-keep class com.example.service.GamingOverlayService { *; }
-keep class com.example.ads.** { *; }

