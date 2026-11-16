package com.example.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.BuildConfig
import com.example.data.MembershipManager
import com.example.data.ProtectionSeverity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Official Google AdMob configuration constants supporting both Production and Development Test modes.
 *
 * Official AdMob Account: pub-9880038173911569
 * Android App ID: ca-app-pub-9880038173911569~6505410503
 * Banner Ad Unit ID: ca-app-pub-9880038173911569/4673454081
 * Native Advanced Ad Unit ID: ca-app-pub-9880038173911569/1855719057
 *
 * Banner and Native Advanced unit IDs are strictly locked and must never be swapped.
 */
object AdMobConfig {
    fun resolveDefaultTestMode(isDebugBuild: Boolean = BuildConfig.DEBUG): Boolean = isDebugBuild

    fun resolveTestModeToggle(currentIsTestMode: Boolean, isDebugBuild: Boolean): Boolean =
        if (!isDebugBuild) false else !currentIsTestMode

    // Defaults to false in release builds (Production IDs) and true in debug builds (Sample Test IDs)
    var IS_TEST_MODE = resolveDefaultTestMode()

    // Official Google Sample Test IDs (preserved for development test verification)
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_ADAPTIVE_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_NATIVE_SPONSORED_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    // Official Production AdMob IDs (Banner = ...4673454081, Native Advanced = ...1855719057)
    const val PROD_APP_ID = "ca-app-pub-9880038173911569~6505410503"
    const val PROD_BANNER_UNIT_ID = "ca-app-pub-9880038173911569/4673454081"
    const val PROD_NATIVE_UNIT_ID = "ca-app-pub-9880038173911569/1855719057"

    val currentAppId: String
        get() = if (IS_TEST_MODE) TEST_APP_ID else PROD_APP_ID

    val currentBannerAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_ADAPTIVE_BANNER_UNIT_ID else PROD_BANNER_UNIT_ID

    val currentNativeAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_NATIVE_SPONSORED_UNIT_ID else PROD_NATIVE_UNIT_ID
}

enum class AdScreenLocation {
    HOME_DASHBOARD,
    REPORTS_BANNER,
    REPORTS_NATIVE_SPONSORED,
    GAME_SPACE,
    TOOLS_HUB,
    AIM_TRAINER,
    BENCHMARK_LAB,
    DEVICE_DIAGNOSTICS,
    NETWORK_TOOLS,
    FLOATING_OVERLAY,
    SETTINGS_OR_PERMISSIONS,
    LOADING_OR_DIALOG
}

data class AdVisibilityContext(
    val location: AdScreenLocation,
    val isGamingSessionActive: Boolean = false,
    val isAutopilotSessionActive: Boolean = false,
    val isBenchmarking: Boolean = false,
    val isScanning: Boolean = false,
    val isLabRunning: Boolean = false,
    val isSpeedTesting: Boolean = false,
    val isAimTrainingActive: Boolean = false,
    val isPermissionOrLoadingActive: Boolean = false,
    val hasWarningOrErrorDialog: Boolean = false,
    val thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE
)

enum class AdLoadStatus {
    IDLE,
    LOADING,
    LOADED,
    NO_FILL,
    FAILED,
    SUPPRESSED,
    UNAVAILABLE_OFFLINE
}

data class AdManagerState(
    val isSdkInitialized: Boolean = false,
    val canRequestAds: Boolean = false,
    val isPrivacyOptionsRequired: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val isTestMode: Boolean = AdMobConfig.IS_TEST_MODE,
    val readyAdaptersCount: Int = 0,
    val totalAdaptersCount: Int = 0,
    val consentStatusMessage: String = "Consent check pending",
    val sdkStatusMessage: String = if (AdMobConfig.IS_TEST_MODE) "AdMob SDK standby (Test Mode)" else "AdMob SDK standby (Production Mode)",
    val homeBannerStatus: AdLoadStatus = AdLoadStatus.IDLE,
    val reportsBannerStatus: AdLoadStatus = AdLoadStatus.IDLE,
    val lastBannerLocation: AdScreenLocation? = null,
    val lastBannerStatus: AdLoadStatus = AdLoadStatus.IDLE,
    val nativeSponsoredStatus: AdLoadStatus = AdLoadStatus.IDLE,
    val totalRequestsCount: Int = 0,
    val totalLoadedCount: Int = 0,
    val noFillCount: Int = 0,
    val failedCount: Int = 0,
    val lastErrorCode: Int? = null,
    val lastAdError: String? = null
)

interface AdManager {
    val adState: StateFlow<AdManagerState>

    fun isAdFreeEntitled(): Boolean

    fun canDisplayAds(visibilityContext: AdVisibilityContext): Boolean

    fun startConsentAndSdkFlow(activity: Activity)

    fun retryConsentAndSdkFlow(activity: Activity)

    fun toggleAdMobTestMode(): Boolean

    fun initializeMobileAdsIfPermitted(context: Context)

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (String?) -> Unit)

    fun refreshNetworkStatus(context: Context): Boolean

    fun computeAnchoredAdaptiveWidthDp(context: Context, availableWidthDp: Int? = null): Int

    fun buildAdRequest(): AdRequest

    fun loadBannerAd(adView: AdView)

    fun recordBannerStatus(
        location: AdScreenLocation,
        status: AdLoadStatus,
        errorMessage: String? = null,
        errorCode: Int? = null
    )

    fun recordNativeStatus(
        status: AdLoadStatus,
        errorMessage: String? = null,
        errorCode: Int? = null
    )
}

open class DefaultAdManager(
    private val isProEntitlementActive: () -> Boolean = { MembershipManager.isPremium() }
) : AdManager {

    protected val _adState = MutableStateFlow(AdManagerState())
    override val adState: StateFlow<AdManagerState> = _adState.asStateFlow()

    override fun isAdFreeEntitled(): Boolean = isProEntitlementActive()

    override fun canDisplayAds(visibilityContext: AdVisibilityContext): Boolean {
        if (isAdFreeEntitled()) return false

        val state = _adState.value
        if (!state.canRequestAds) return false
        if (!state.isNetworkAvailable) return false

        val isAllowedLocation = when (visibilityContext.location) {
            AdScreenLocation.HOME_DASHBOARD,
            AdScreenLocation.REPORTS_BANNER,
            AdScreenLocation.REPORTS_NATIVE_SPONSORED,
            AdScreenLocation.GAME_SPACE,
            AdScreenLocation.TOOLS_HUB,
            AdScreenLocation.AIM_TRAINER,
            AdScreenLocation.BENCHMARK_LAB,
            AdScreenLocation.DEVICE_DIAGNOSTICS,
            AdScreenLocation.NETWORK_TOOLS -> true
            AdScreenLocation.FLOATING_OVERLAY,
            AdScreenLocation.SETTINGS_OR_PERMISSIONS,
            AdScreenLocation.LOADING_OR_DIALOG -> false
        }
        if (!isAllowedLocation) return false

        if (visibilityContext.isGamingSessionActive) return false
        if (visibilityContext.isAutopilotSessionActive) return false
        if (visibilityContext.isBenchmarking) return false
        if (visibilityContext.isScanning) return false
        if (visibilityContext.isLabRunning) return false
        if (visibilityContext.isSpeedTesting) return false
        if (visibilityContext.isAimTrainingActive) return false
        if (visibilityContext.isPermissionOrLoadingActive) return false
        if (visibilityContext.hasWarningOrErrorDialog) return false

        if (visibilityContext.thermalSeverity == ProtectionSeverity.WARNING ||
            visibilityContext.thermalSeverity == ProtectionSeverity.CRITICAL
        ) {
            return false
        }

        return true
    }

    override fun startConsentAndSdkFlow(activity: Activity) {
        refreshNetworkStatus(activity.applicationContext)
        ConsentManager.gatherConsentAndInitializeAds(
            activity = activity,
            onConsentUpdated = { canRequest, privacyRequired, statusText, error ->
                _adState.update {
                    it.copy(
                        canRequestAds = canRequest,
                        isPrivacyOptionsRequired = privacyRequired,
                        consentStatusMessage = statusText,
                        lastAdError = error ?: it.lastAdError
                    )
                }
                if (canRequest) {
                    initializeMobileAdsIfPermitted(activity.applicationContext)
                }
            },
            onSdkInitialized = { initialized, readyCount, totalCount, sdkMsg ->
                _adState.update {
                    it.copy(
                        isSdkInitialized = initialized,
                        readyAdaptersCount = readyCount,
                        totalAdaptersCount = totalCount,
                        sdkStatusMessage = sdkMsg
                    )
                }
            }
        )
    }

    override fun retryConsentAndSdkFlow(activity: Activity) {
        refreshNetworkStatus(activity.applicationContext)
        ConsentManager.retryConsentAndSdkFlow(
            activity = activity,
            onConsentUpdated = { canRequest, privacyRequired, statusText, error ->
                _adState.update {
                    it.copy(
                        canRequestAds = canRequest,
                        isPrivacyOptionsRequired = privacyRequired,
                        consentStatusMessage = statusText,
                        lastAdError = error ?: it.lastAdError
                    )
                }
                if (canRequest) {
                    initializeMobileAdsIfPermitted(activity.applicationContext)
                }
            },
            onSdkInitialized = { initialized, readyCount, totalCount, sdkMsg ->
                _adState.update {
                    it.copy(
                        isSdkInitialized = initialized,
                        readyAdaptersCount = readyCount,
                        totalAdaptersCount = totalCount,
                        sdkStatusMessage = sdkMsg
                    )
                }
            }
        )
    }

    override fun toggleAdMobTestMode(): Boolean {
        val nextMode = AdMobConfig.resolveTestModeToggle(
            currentIsTestMode = AdMobConfig.IS_TEST_MODE,
            isDebugBuild = BuildConfig.DEBUG
        )
        AdMobConfig.IS_TEST_MODE = nextMode
        _adState.update {
            val statusMsg = if (AdMobConfig.IS_TEST_MODE) {
                "AdMob Test SDK Ready (Test Units Only)"
            } else {
                "AdMob Production Ready (pub-9880038173911569)"
            }
            it.copy(
                isTestMode = AdMobConfig.IS_TEST_MODE,
                sdkStatusMessage = if (it.isSdkInitialized) statusMsg else it.sdkStatusMessage
            )
        }
        return AdMobConfig.IS_TEST_MODE
    }

    override fun initializeMobileAdsIfPermitted(context: Context) {
        if (!_adState.value.canRequestAds) return
        ConsentManager.initializeMobileAdsInBackground(context.applicationContext) { initialized, readyCount, totalCount, sdkMsg ->
            _adState.update {
                it.copy(
                    isSdkInitialized = initialized,
                    readyAdaptersCount = readyCount,
                    totalAdaptersCount = totalCount,
                    sdkStatusMessage = sdkMsg
                )
            }
        }
    }

    override fun showPrivacyOptionsForm(activity: Activity, onDismissed: (String?) -> Unit) {
        ConsentManager.showPrivacyOptionsForm(activity) { errorMsg ->
            _adState.update {
                it.copy(
                    isPrivacyOptionsRequired = ConsentManager.isPrivacyOptionsRequired(activity),
                    lastAdError = errorMsg
                )
            }
            onDismissed(errorMsg)
        }
    }

    override fun refreshNetworkStatus(context: Context): Boolean {
        val online = isOnline(context)
        _adState.update { current ->
            val readyMsg = if (AdMobConfig.IS_TEST_MODE) {
                "AdMob Test SDK Ready (Test Units Only)"
            } else {
                "AdMob Production Ready (pub-9880038173911569)"
            }
            current.copy(
                isNetworkAvailable = online,
                isTestMode = AdMobConfig.IS_TEST_MODE,
                sdkStatusMessage = if (!online) {
                    "Offline mode — ads hidden without impact to tools"
                } else if (current.isSdkInitialized) {
                    readyMsg
                } else {
                    current.sdkStatusMessage
                }
            )
        }
        return online
    }

    override fun computeAnchoredAdaptiveWidthDp(context: Context, availableWidthDp: Int?): Int {
        if (availableWidthDp != null && availableWidthDp > 0) {
            return availableWidthDp.coerceIn(220, 720)
        }
        return try {
            val metrics = context.resources.displayMetrics
            val widthPx = metrics.widthPixels.coerceAtLeast(320)
            val density = metrics.density.coerceAtLeast(1f)
            ((widthPx / density).toInt() - 48).coerceIn(240, 720)
        } catch (_: Exception) {
            300
        }
    }

    override fun buildAdRequest(): AdRequest = AdRequest.Builder().build()

    override fun loadBannerAd(adView: AdView) {
        adView.loadAd(buildAdRequest())
    }

    override fun recordBannerStatus(
        location: AdScreenLocation,
        status: AdLoadStatus,
        errorMessage: String?,
        errorCode: Int?
    ) {
        _adState.update { current ->
            val newRequests = if (status == AdLoadStatus.LOADING) current.totalRequestsCount + 1 else current.totalRequestsCount
            val newLoaded = if (status == AdLoadStatus.LOADED) current.totalLoadedCount + 1 else current.totalLoadedCount
            val newNoFill = if (status == AdLoadStatus.NO_FILL) current.noFillCount + 1 else current.noFillCount
            val newFailed = if (status == AdLoadStatus.FAILED) current.failedCount + 1 else current.failedCount
            val nextErrorCode = when (status) {
                AdLoadStatus.LOADED -> null
                AdLoadStatus.NO_FILL, AdLoadStatus.FAILED -> errorCode ?: current.lastErrorCode
                else -> current.lastErrorCode
            }
            val nextErrorMsg = when (status) {
                AdLoadStatus.LOADED -> null
                else -> errorMessage ?: current.lastAdError
            }
            current.copy(
                homeBannerStatus = if (location == AdScreenLocation.HOME_DASHBOARD) status else current.homeBannerStatus,
                reportsBannerStatus = if (location == AdScreenLocation.REPORTS_BANNER) status else current.reportsBannerStatus,
                lastBannerLocation = location,
                lastBannerStatus = status,
                totalRequestsCount = newRequests,
                totalLoadedCount = newLoaded,
                noFillCount = newNoFill,
                failedCount = newFailed,
                lastErrorCode = nextErrorCode,
                lastAdError = nextErrorMsg
            )
        }
    }

    override fun recordNativeStatus(
        status: AdLoadStatus,
        errorMessage: String?,
        errorCode: Int?
    ) {
        _adState.update { current ->
            val newRequests = if (status == AdLoadStatus.LOADING) current.totalRequestsCount + 1 else current.totalRequestsCount
            val newLoaded = if (status == AdLoadStatus.LOADED) current.totalLoadedCount + 1 else current.totalLoadedCount
            val newNoFill = if (status == AdLoadStatus.NO_FILL) current.noFillCount + 1 else current.noFillCount
            val newFailed = if (status == AdLoadStatus.FAILED) current.failedCount + 1 else current.failedCount
            val nextErrorCode = when (status) {
                AdLoadStatus.LOADED -> null
                AdLoadStatus.NO_FILL, AdLoadStatus.FAILED -> errorCode ?: current.lastErrorCode
                else -> current.lastErrorCode
            }
            val nextErrorMsg = when (status) {
                AdLoadStatus.LOADED -> null
                else -> errorMessage ?: current.lastAdError
            }
            current.copy(
                nativeSponsoredStatus = status,
                totalRequestsCount = newRequests,
                totalLoadedCount = newLoaded,
                noFillCount = newNoFill,
                failedCount = newFailed,
                lastErrorCode = nextErrorCode,
                lastAdError = nextErrorMsg
            )
        }
    }

    fun updateStateForTesting(transform: (AdManagerState) -> AdManagerState) {
        _adState.update(transform)
    }

    private fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }
}

internal fun classifyAdError(errorCode: Int, message: String, domain: String): Pair<AdLoadStatus, String> {
    return when (errorCode) {
        AdRequest.ERROR_CODE_NO_FILL -> {
            AdLoadStatus.NO_FILL to "Code 3 (NO_FILL): $message — Live AdMob inventory had no ad for this request (or account/app review pending)"
        }
        AdRequest.ERROR_CODE_NETWORK_ERROR -> {
            AdLoadStatus.FAILED to "Code 2 (NETWORK_ERROR): $message ($domain)"
        }
        AdRequest.ERROR_CODE_INVALID_REQUEST -> {
            AdLoadStatus.FAILED to "Code 1 (INVALID_REQUEST): $message ($domain)"
        }
        AdRequest.ERROR_CODE_INTERNAL_ERROR -> {
            AdLoadStatus.FAILED to "Code 0 (INTERNAL_ERROR): $message ($domain)"
        }
        AdRequest.ERROR_CODE_APP_ID_MISSING -> {
            AdLoadStatus.FAILED to "Code 8 (APP_ID_MISSING): $message ($domain)"
        }
        else -> {
            AdLoadStatus.FAILED to "Code $errorCode: $message ($domain)"
        }
    }
}

object GoogleAdMobManager : DefaultAdManager()
