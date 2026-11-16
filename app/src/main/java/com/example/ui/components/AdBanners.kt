package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button as AndroidButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ads.*
import com.example.ui.theme.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Anchored adaptive Google AdMob banner slot.
 * Computes adaptive width directly from measured BoxWithConstraints container bounds so AdView
 * never overflows padded containers, ties pause/resume/destroy to lifecycle, and stays hidden
 * for Pro users, offline state, unconsented state, overlays, active gameplay, and active tools.
 */
@Composable
fun AdMobAdaptiveBannerSlot(
    visibilityContext: AdVisibilityContext,
    adUnitId: String = AdMobConfig.currentBannerAdUnitId,
    adManager: AdManager = GoogleAdMobManager,
    testTagName: String = "admob_banner_slot",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInspectionMode = LocalInspectionMode.current
    val adState by adManager.adState.collectAsState()

    LaunchedEffect(context) {
        adManager.refreshNetworkStatus(context)
    }

    val canShow = remember(adState, visibilityContext) {
        adManager.canDisplayAds(visibilityContext)
    }

    if (!canShow || isInspectionMode) {
        return
    }

    var localLoadStatus by remember(adUnitId, visibilityContext.location) {
        mutableStateOf(AdLoadStatus.LOADING)
    }
    var localError by remember(adUnitId, visibilityContext.location) {
        mutableStateOf<String?>(null)
    }
    var reloadKey by remember { mutableIntStateOf(0) }
    var activeAdView by remember(adUnitId, reloadKey) { mutableStateOf<AdView?>(null) }

    DisposableEffect(lifecycleOwner, activeAdView) {
        val adView = activeAdView
        val observer = LifecycleEventObserver { _, event ->
            if (adView != null) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> try { adView.resume() } catch (_: Throwable) {}
                    Lifecycle.Event.ON_PAUSE -> try { adView.pause() } catch (_: Throwable) {}
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                when (localLoadStatus) {
                    AdLoadStatus.LOADED -> NeonGreen.copy(alpha = 0.45f)
                    AdLoadStatus.NO_FILL -> NeonOrange.copy(alpha = 0.45f)
                    AdLoadStatus.FAILED -> NeonRed.copy(alpha = 0.45f)
                    else -> DarkSurfaceBorder
                },
                RoundedCornerShape(14.dp)
            )
            .testTag(testTagName),
        color = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkSurfaceBorder
                    ) {
                        Text(
                            text = "AD",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = if (adState.isTestMode) "Google AdMob • Test Mode" else "Google AdMob",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }

                Text(
                    text = when (localLoadStatus) {
                        AdLoadStatus.LOADED -> "Loaded"
                        AdLoadStatus.LOADING -> "Requesting..."
                        AdLoadStatus.NO_FILL -> "No Fill"
                        AdLoadStatus.FAILED -> "Load Error"
                        else -> "Standby"
                    },
                    color = when (localLoadStatus) {
                        AdLoadStatus.LOADED -> NeonGreen
                        AdLoadStatus.NO_FILL -> NeonOrange
                        AdLoadStatus.FAILED -> NeonRed
                        else -> TextMuted
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val measuredWidthDp = maxWidth.value.toInt().takeIf { it > 0 }
                val adaptiveWidthDp = adManager.computeAnchoredAdaptiveWidthDp(context, measuredWidthDp)

                key(adUnitId, reloadKey) {
                    AndroidView(
                        factory = { ctx ->
                            try {
                                val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adaptiveWidthDp)
                                AdView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    minimumHeight = adaptiveSize.getHeightInPixels(ctx).coerceAtLeast(1)
                                    this.adUnitId = adUnitId
                                    setAdSize(adaptiveSize)
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            localLoadStatus = AdLoadStatus.LOADED
                                            localError = null
                                            adManager.recordBannerStatus(
                                                location = visibilityContext.location,
                                                status = AdLoadStatus.LOADED
                                            )
                                        }

                                        override fun onAdFailedToLoad(adError: LoadAdError) {
                                            val (classifiedStatus, errStr) = classifyAdError(
                                                adError.code,
                                                adError.message,
                                                adError.domain
                                            )
                                            localLoadStatus = classifiedStatus
                                            localError = errStr
                                            adManager.recordBannerStatus(
                                                location = visibilityContext.location,
                                                status = classifiedStatus,
                                                errorMessage = errStr,
                                                errorCode = adError.code
                                            )
                                        }
                                    }
                                    activeAdView = this
                                    localLoadStatus = AdLoadStatus.LOADING
                                    adManager.recordBannerStatus(
                                        location = visibilityContext.location,
                                        status = AdLoadStatus.LOADING
                                    )
                                    adManager.loadBannerAd(this)
                                }
                            } catch (t: Throwable) {
                                val errStr = "Env note: ${t.message ?: "AdView unavailable"}"
                                localLoadStatus = AdLoadStatus.FAILED
                                localError = errStr
                                adManager.recordBannerStatus(
                                    location = visibilityContext.location,
                                    status = AdLoadStatus.FAILED,
                                    errorMessage = errStr
                                )
                                android.widget.FrameLayout(ctx)
                            }
                        },
                        onRelease = { view ->
                            if (view is AdView) {
                                if (activeAdView === view) activeAdView = null
                                try {
                                    view.destroy()
                                } catch (_: Throwable) {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }

            if (localLoadStatus == AdLoadStatus.LOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                        Text(
                            text = if (adState.isTestMode) "Requesting AdMob test banner..." else "Requesting AdMob banner...",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (localLoadStatus == AdLoadStatus.NO_FILL || localLoadStatus == AdLoadStatus.FAILED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localError ?: "Ad request failed",
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { reloadKey++ },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Retry", fontSize = 10.sp, color = NeonCyan)
                    }
                }
            }
        }
    }
}

/**
 * Real Google AdMob Native Advanced Sponsored Content placement for the Reports screen.
 * Strictly renders only when a genuine NativeAd object is loaded from Google AdMob
 * (or inline status/retry when Test Mode diagnostics are active).
 */
@Composable
fun AdMobNativeSponsoredCard(
    visibilityContext: AdVisibilityContext,
    adUnitId: String = AdMobConfig.currentNativeAdUnitId,
    adManager: AdManager = GoogleAdMobManager,
    testTagName: String = "reports_native_sponsored_card",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInspectionMode = LocalInspectionMode.current
    val adState by adManager.adState.collectAsState()

    val canShow = remember(adState, visibilityContext) {
        adManager.canDisplayAds(visibilityContext)
    }

    if (!canShow || isInspectionMode) {
        return
    }

    var loadedNativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }
    var nativeStatus by remember(adUnitId) { mutableStateOf(AdLoadStatus.IDLE) }
    var nativeError by remember(adUnitId) { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var inFlightKey by remember(adUnitId) { mutableStateOf<String?>(null) }

    var isDisposed = false
    DisposableEffect(adUnitId) {
        onDispose {
            isDisposed = true
            try {
                loadedNativeAd?.destroy()
            } catch (_: Throwable) {}
            loadedNativeAd = null
        }
    }

    LaunchedEffect(canShow, adUnitId, reloadTrigger) {
        if (!canShow || isInspectionMode) {
            try {
                loadedNativeAd?.destroy()
            } catch (_: Throwable) {}
            loadedNativeAd = null
            return@LaunchedEffect
        }

        val requestKey = "$adUnitId#$reloadTrigger"
        if (inFlightKey == requestKey && nativeStatus == AdLoadStatus.LOADING) {
            return@LaunchedEffect
        }
        inFlightKey = requestKey

        try {
            nativeStatus = AdLoadStatus.LOADING
            nativeError = null
            adManager.recordNativeStatus(AdLoadStatus.LOADING)
            val adLoader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { nativeAd ->
                    if (isDisposed) {
                        try { nativeAd.destroy() } catch (_: Throwable) {}
                        return@forNativeAd
                    }
                    try {
                        loadedNativeAd?.destroy()
                    } catch (_: Throwable) {}
                    loadedNativeAd = nativeAd
                    nativeStatus = AdLoadStatus.LOADED
                    nativeError = null
                    adManager.recordNativeStatus(AdLoadStatus.LOADED)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        val (classifiedStatus, msg) = classifyAdError(
                            adError.code,
                            adError.message,
                            adError.domain
                        )
                        nativeStatus = classifiedStatus
                        nativeError = msg
                        adManager.recordNativeStatus(
                            status = classifiedStatus,
                            errorMessage = msg,
                            errorCode = adError.code
                        )
                    }
                })
                .build()
            adLoader.loadAd(adManager.buildAdRequest())
        } catch (t: Throwable) {
            val msg = "Env note: ${t.message ?: "Native AdLoader unavailable"}"
            nativeStatus = AdLoadStatus.FAILED
            nativeError = msg
            adManager.recordNativeStatus(AdLoadStatus.FAILED, msg)
        }
    }

    val activeNativeAd = loadedNativeAd
    if (activeNativeAd == null && !adState.isTestMode) {
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 80.dp)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp))
            .testTag(testTagName),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NeonCyan.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "SPONSORED",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                val modeLabel = if (adState.isTestMode) "AdMob Native (Test Mode)" else "Google AdMob Native"
                Text(
                    text = when {
                        activeNativeAd != null -> "$modeLabel • Loaded"
                        nativeStatus == AdLoadStatus.LOADING -> "$modeLabel • Requesting..."
                        nativeStatus == AdLoadStatus.NO_FILL -> "$modeLabel • No Fill"
                        nativeStatus == AdLoadStatus.FAILED -> "$modeLabel • Failed"
                        else -> modeLabel
                    },
                    color = when {
                        activeNativeAd != null -> NeonGreen
                        nativeStatus == AdLoadStatus.NO_FILL -> NeonOrange
                        nativeStatus == AdLoadStatus.FAILED -> NeonRed
                        else -> TextMuted
                    },
                    fontSize = 10.sp
                )
            }

            if (activeNativeAd != null) {
                AndroidView(
                    factory = { ctx -> buildNativeAdView(ctx) },
                    update = { nativeAdView -> bindNativeAd(nativeAdView, activeNativeAd) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (nativeStatus == AdLoadStatus.LOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                        Text(
                            text = "Loading sponsored gaming recommendation...",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (nativeStatus == AdLoadStatus.NO_FILL || nativeStatus == AdLoadStatus.FAILED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nativeError ?: "Native ad unavailable (No fill)",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { reloadTrigger++ },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Retry", fontSize = 10.5.sp, color = NeonCyan)
                    }
                }
            }
        }
    }
}

private fun buildNativeAdView(context: Context): NativeAdView {
    val root = NativeAdView(context)
    val density = context.resources.displayMetrics.density
    val pad = (8 * density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
    }

    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val iconSize = (40 * density).toInt()
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
            marginEnd = (10 * density).toInt()
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val titleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        setTextColor(AndroidColor.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
    }

    val advertiserView = TextView(context).apply {
        id = View.generateViewId()
        setTextColor(AndroidColor.LTGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    }

    val adChoicesView = AdChoicesView(context).apply {
        id = View.generateViewId()
    }

    titleCol.addView(headlineView)
    titleCol.addView(advertiserView)
    headerRow.addView(iconView)
    headerRow.addView(titleCol)
    headerRow.addView(adChoicesView)

    val mediaView = com.google.android.gms.ads.nativead.MediaView(context).apply {
        id = View.generateViewId()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (130 * density).toInt()
        ).apply {
            topMargin = (6 * density).toInt()
        }
    }

    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        setTextColor(AndroidColor.LTGRAY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, (6 * density).toInt(), 0, (8 * density).toInt())
    }

    val ctaView = AndroidButton(context).apply {
        id = View.generateViewId()
        setTextColor(AndroidColor.BLACK)
        setBackgroundColor(AndroidColor.CYAN)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        isAllCaps = false
    }

    container.addView(headerRow)
    container.addView(mediaView)
    container.addView(bodyView)
    container.addView(ctaView)
    root.addView(container)

    root.iconView = iconView
    root.headlineView = headlineView
    root.advertiserView = advertiserView
    root.adChoicesView = adChoicesView
    root.mediaView = mediaView
    root.bodyView = bodyView
    root.callToActionView = ctaView
    return root
}

private fun bindNativeAd(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.apply {
        text = nativeAd.headline.orEmpty()
        visibility = if (nativeAd.headline.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (nativeAdView.advertiserView as? TextView)?.apply {
        text = nativeAd.advertiser.orEmpty()
        visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    nativeAdView.mediaView?.apply {
        val content = nativeAd.mediaContent
        if (content != null && (content.hasVideoContent() || content.mainImage != null)) {
            mediaContent = content
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }
    (nativeAdView.bodyView as? TextView)?.apply {
        text = nativeAd.body.orEmpty()
        visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (nativeAdView.iconView as? ImageView)?.apply {
        val drawable = nativeAd.icon?.drawable
        if (drawable != null) {
            setImageDrawable(drawable)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }
    (nativeAdView.callToActionView as? AndroidButton)?.apply {
        text = nativeAd.callToAction.orEmpty()
        visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    nativeAdView.setNativeAd(nativeAd)
}
