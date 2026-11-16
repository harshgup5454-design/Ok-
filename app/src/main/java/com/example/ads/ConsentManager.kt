package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Google User Messaging Platform (UMP) consent collection and safe background
 * initialization of the Google Mobile Ads SDK without blocking app startup.
 */
object ConsentManager {

    private val isMobileAdsInitStarted = AtomicBoolean(false)
    private val isMobileAdsInitCompleted = AtomicBoolean(false)
    private val isConsentFlowInFlight = AtomicBoolean(false)
    @Volatile
    private var lastReadyAdapters = 0
    @Volatile
    private var lastTotalAdapters = 0
    @Volatile
    private var lastSdkStatusMessage = "AdMob SDK standby"

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context.applicationContext)
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (_: Throwable) {
            false
        }
    }

    fun gatherConsentAndInitializeAds(
        activity: Activity,
        onConsentUpdated: (canRequestAds: Boolean, isPrivacyOptionsRequired: Boolean, statusMessage: String, error: String?) -> Unit,
        onSdkInitialized: (initialized: Boolean, readyCount: Int, totalCount: Int, sdkMessage: String) -> Unit
    ) {
        val appContext = activity.applicationContext
        if (!isConsentFlowInFlight.compareAndSet(false, true)) {
            return
        }
        try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)

            val paramsBuilder = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)

            if (BuildConfig.DEBUG && AdMobConfig.IS_TEST_MODE) {
                val debugSettingsBuilder = com.google.android.ump.ConsentDebugSettings.Builder(activity)
                    .addTestDeviceHashedId(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR)
                computeDebugEmulatorTestId(appContext)?.let { debugSettingsBuilder.addTestDeviceHashedId(it) }
                paramsBuilder.setConsentDebugSettings(debugSettingsBuilder.build())
            }

            val params = paramsBuilder.build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { loadAndShowError ->
                            isConsentFlowInFlight.set(false)
                            val canRequest = safeCanRequestAds(consentInformation)
                            val privacyRequired = isPrivacyOptionsRequired(appContext)
                            val msg = when {
                                loadAndShowError != null -> "Consent form completed with note: ${loadAndShowError.message}"
                                canRequest -> "Consent status verified via UMP (canRequestAds=true)"
                                else -> "Consent not granted — network ads paused"
                            }
                            onConsentUpdated(canRequest, privacyRequired, msg, loadAndShowError?.message)
                            if (canRequest) {
                                initializeMobileAdsInBackground(appContext, onSdkInitialized)
                            }
                        }
                    } catch (t: Throwable) {
                        isConsentFlowInFlight.set(false)
                        val canRequest = safeCanRequestAds(consentInformation)
                        onConsentUpdated(
                            canRequest,
                            isPrivacyOptionsRequired(appContext),
                            "Consent form notice: ${t.message ?: "offline / test environment"}",
                            t.message
                        )
                        if (canRequest) {
                            initializeMobileAdsInBackground(appContext, onSdkInitialized)
                        }
                    }
                },
                { requestConsentError ->
                    isConsentFlowInFlight.set(false)
                    val canRequest = safeCanRequestAds(consentInformation)
                    val privacyRequired = isPrivacyOptionsRequired(appContext)
                    val msg = "Consent update failed (${requestConsentError.message}); canRequest=$canRequest"
                    onConsentUpdated(
                        canRequest,
                        privacyRequired,
                        msg,
                        requestConsentError.message
                    )
                    if (canRequest) {
                        initializeMobileAdsInBackground(appContext, onSdkInitialized)
                    }
                }
            )

            // Also honor prior-session consent immediately without duplicating init
            if (safeCanRequestAds(consentInformation)) {
                onConsentUpdated(
                    true,
                    isPrivacyOptionsRequired(appContext),
                    "Consent active from prior session",
                    null
                )
                initializeMobileAdsInBackground(appContext, onSdkInitialized)
            }
        } catch (t: Throwable) {
            isConsentFlowInFlight.set(false)
            onConsentUpdated(
                false,
                false,
                "UMP unavailable in current environment",
                t.message
            )
            onSdkInitialized(false, 0, 0, "AdMob SDK unavailable in current environment")
        }
    }

    fun initializeMobileAdsInBackground(
        context: Context,
        onSdkInitialized: (initialized: Boolean, readyCount: Int, totalCount: Int, sdkMessage: String) -> Unit
    ) {
        if (isMobileAdsInitCompleted.get()) {
            onSdkInitialized(true, lastReadyAdapters, lastTotalAdapters, lastSdkStatusMessage)
            return
        }
        if (!isMobileAdsInitStarted.compareAndSet(false, true)) {
            onSdkInitialized(false, lastReadyAdapters, lastTotalAdapters, "AdMob SDK initialization in progress...")
            return
        }

        val appContext = context.applicationContext
        initScope.launch {
            try {
                if (BuildConfig.DEBUG && AdMobConfig.IS_TEST_MODE) {
                    val testIds = buildList {
                        add(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR)
                        computeDebugEmulatorTestId(appContext)?.let { add(it) }
                    }
                    val requestConfig = RequestConfiguration.Builder()
                        .setTestDeviceIds(testIds)
                        .build()
                    MobileAds.setRequestConfiguration(requestConfig)
                }
                MobileAds.initialize(appContext) { initStatus ->
                    val statusMap = initStatus.adapterStatusMap
                    val totalCount = statusMap.size
                    val readyCount = statusMap.values.count { it.initializationState == AdapterStatus.State.READY }
                    val modeText = if (AdMobConfig.IS_TEST_MODE) "Test Mode" else "Production Mode"
                    val msg = "AdMob SDK Ready ($readyCount/$totalCount adapters, $modeText)"
                    lastReadyAdapters = readyCount
                    lastTotalAdapters = totalCount
                    lastSdkStatusMessage = msg
                    isMobileAdsInitCompleted.set(true)
                    onSdkInitialized(true, readyCount, totalCount, msg)
                }
            } catch (t: Throwable) {
                isMobileAdsInitStarted.set(false)
                isMobileAdsInitCompleted.set(false)
                val msg = "AdMob SDK init failed: ${t.message ?: "unavailable"}"
                lastSdkStatusMessage = msg
                onSdkInitialized(false, 0, 0, msg)
            }
        }
    }

    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (String?) -> Unit
    ) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                onDismissed(formError?.message)
            }
        } catch (t: Throwable) {
            onDismissed(t.message ?: "Privacy options form unavailable offline")
        }
    }

    private fun safeCanRequestAds(info: ConsentInformation): Boolean {
        return try {
            info.canRequestAds()
        } catch (_: Throwable) {
            false
        }
    }

    fun resetInitFlag() {
        isMobileAdsInitStarted.set(false)
        isMobileAdsInitCompleted.set(false)
        isConsentFlowInFlight.set(false)
        lastReadyAdapters = 0
        lastTotalAdapters = 0
    }

    fun resetInitFlagForTesting() {
        resetInitFlag()
    }

    private fun computeDebugEmulatorTestId(context: Context): String? {
        if (!BuildConfig.DEBUG || !isRunningOnEmulator()) return null
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId.isNullOrBlank()) return null
            MessageDigest.getInstance("MD5")
                .digest(androidId.toByteArray())
                .joinToString("") { "%02X".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("cutf") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator") ||
            Build.PRODUCT.contains("cuttlefish")
    }

    fun retryConsentAndSdkFlow(
        activity: Activity,
        onConsentUpdated: (canRequestAds: Boolean, isPrivacyOptionsRequired: Boolean, statusMessage: String, error: String?) -> Unit,
        onSdkInitialized: (initialized: Boolean, readyCount: Int, totalCount: Int, sdkMessage: String) -> Unit
    ) {
        resetInitFlag()
        try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity.applicationContext)
            consentInformation.reset()
        } catch (_: Throwable) {}
        gatherConsentAndInitializeAds(activity, onConsentUpdated, onSdkInitialized)
    }
}
