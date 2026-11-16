package com.example

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPermissionType
import com.example.data.PermissionManager
import com.example.data.PermissionStatus
import com.example.ui.screens.OnboardingPreferences
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.theme.GameBoostTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionFlowTest {

    private val freshInstallRule = object : ExternalResource() {
        override fun before() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            OnboardingPreferences.setCompleted(ctx, false)
            PermissionManager.clearNotificationRequestTracking(ctx)
            ShadowSettings.setCanDrawOverlays(false)
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            shadowOf(appOps).setMode(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                ctx.packageName,
                android.app.AppOpsManager.MODE_IGNORED
            )
        }
    }

    private val composeActivityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(freshInstallRule)
        .around(composeActivityRule)

    @After
    fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        OnboardingPreferences.setCompleted(ctx, false)
        PermissionManager.clearNotificationRequestTracking(ctx)
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test
    fun `first launch displays onboarding once and skip enters dashboard without repeating`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("permission_setup_screen").assertIsDisplayed()
        composeActivityRule.onNodeWithText("FIRST-TIME SETUP").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("onboarding_continue_button").assertIsDisplayed()

        // Tap Skip -> marks onboarding complete and shows Dashboard
        composeActivityRule.onNodeWithTag("onboarding_skip_button").performClick()
        composeActivityRule.waitForIdle()

        assertTrue(OnboardingPreferences.isCompleted(ctx))
        composeActivityRule.onNodeWithTag("permission_setup_screen").assertDoesNotExist()
        composeActivityRule.onNodeWithText("GAMEBOOST 3.0").assertIsDisplayed()

        // Subsequent launch must NOT repeat onboarding screen
        val relaunchScenario = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        assertTrue(OnboardingPreferences.isCompleted(relaunchScenario.get()))
        relaunchScenario.pause().stop().destroy()
    }

    @Test
    fun `continue button finishes setup and permission audit reflects granted and denied states`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = shadowOf(ctx as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowSettings.setCanDrawOverlays(true)

        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("permission_recheck_button").performClick()
        composeActivityRule.waitForIdle()

        val audit = PermissionManager.auditPermissions(ctx, composeActivityRule.activity)
        assertEquals(
            PermissionStatus.GRANTED,
            audit.items.first { it.type == AppPermissionType.OVERLAY }.status
        )
        assertEquals(
            PermissionStatus.GRANTED,
            audit.items.first { it.type == AppPermissionType.NOTIFICATIONS }.status
        )
        assertEquals(
            PermissionStatus.NOT_GRANTED,
            audit.items.first { it.type == AppPermissionType.USAGE_ACCESS }.status
        )

        // Explain rationale dialog appears before opening Android Settings for Usage Access (not granted)
        composeActivityRule.onNodeWithTag("permission_setup_list")
            .performScrollToNode(hasTestTag("permission_action_usage_access"))
        composeActivityRule.onNodeWithTag("permission_action_usage_access").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("permission_rationale_dialog").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("permission_rationale_cancel").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("permission_rationale_dialog").assertDoesNotExist()

        // Continue completes onboarding
        composeActivityRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeActivityRule.waitForIdle()
        assertTrue(OnboardingPreferences.isCompleted(ctx))
        composeActivityRule.onNodeWithText("GAMEBOOST 3.0").assertIsDisplayed()
    }

    @Test
    fun `returning from Android Settings refreshes status and detects revocation while open`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ShadowSettings.setCanDrawOverlays(false)
        composeActivityRule.waitForIdle()

        // Initially Not Granted
        composeActivityRule.onNodeWithTag("permission_status_overlay")
            .assertTextEquals("Not Granted")

        // Simulate user granting overlay permission in Android Settings -> ON_RESUME fires
        ShadowSettings.setCanDrawOverlays(true)
        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("permission_status_overlay")
            .assertTextEquals("Granted")

        // Simulate permission revocation while app is open / returning from Settings
        ShadowSettings.setCanDrawOverlays(false)
        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("permission_status_overlay")
            .assertTextEquals("Not Granted")
        assertFalse(PermissionManager.isOverlayPermissionGranted(ctx))
    }

    @Test
    fun `permanently denied notification permission switches action to Open App Settings`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = shadowOf(ctx as android.app.Application)
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        PermissionManager.markNotificationPermissionRequested(ctx)
        // In Robolectric default activity, shouldShowRequestPermissionRationale defaults to false after request
        val audit = PermissionManager.auditPermissions(ctx, composeActivityRule.activity)
        val notifItem = audit.items.first { it.type == AppPermissionType.NOTIFICATIONS }
        assertEquals(PermissionStatus.NOT_GRANTED, notifItem.status)
        assertTrue(notifItem.isPermanentlyDenied)

        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeActivityRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("permission_setup_list")
            .performScrollToNode(hasTestTag("permission_action_notifications"))
        composeActivityRule.onNodeWithTag("permission_action_notifications").performClick()
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("permission_rationale_dialog").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("permission_rationale_confirm")
            .assertTextEquals("Open App Settings")
        composeActivityRule.onNodeWithTag("permission_rationale_cancel").performClick()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionApi30NotApplicableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        OnboardingPreferences.setCompleted(ctx, false)
    }

    @Test
    fun `notifications permission reports Not Applicable below Android 13`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val audit = PermissionManager.auditPermissions(ctx, null, sdkInt = 30)
        val notif = audit.items.first { it.type == AppPermissionType.NOTIFICATIONS }
        assertEquals(PermissionStatus.NOT_APPLICABLE, notif.status)
        assertFalse(notif.isRuntimePermission)

        composeRule.setContent {
            GameBoostTheme {
                PermissionSetupScreen(
                    onFinishSetup = {},
                    isOnboardingMode = true,
                    previewSdkInt = 30
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("permission_setup_list")
            .performScrollToNode(hasTestTag("permission_card_notifications"))
        composeRule.onNodeWithTag("permission_status_notifications")
            .assertTextEquals("Not Applicable")
    }
}
