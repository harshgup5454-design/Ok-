package com.example.overlay

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.ui.screens.OnboardingPreferences
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainNavigationAndSettingsResumeTest {

    private val onboardingSetupRule = object : ExternalResource() {
        override fun before() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            OnboardingPreferences.setCompleted(ctx, true)
        }
    }

    private val composeActivityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(onboardingSetupRule)
        .around(composeActivityRule)

    @After
    fun tearDown() {
        ShadowSettings.setCanDrawOverlays(false)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        OnboardingPreferences.setCompleted(ctx, false)
    }

    @Test
    fun `bottom bar has 5 simplified tabs and reaches Games Tools Reports Settings and Pro`() {
        composeActivityRule.waitForIdle()

        // Exactly 5 bottom tabs
        composeActivityRule.onNodeWithTag("nav_tab_dashboard").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("nav_tab_space").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("nav_tab_tools").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("nav_tab_reports").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("nav_tab_premium").assertDoesNotExist()

        // Navigate across all 5 tabs cleanly
        composeActivityRule.onNodeWithTag("nav_tab_space").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("GAMING SPACE").assertIsDisplayed()

        composeActivityRule.onNodeWithTag("nav_tab_tools").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("GAMING TOOLS").assertIsDisplayed()

        composeActivityRule.onNodeWithTag("nav_tab_reports").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("FPS Validation & Telemetry Reports").assertIsDisplayed()

        composeActivityRule.onNodeWithTag("nav_tab_settings").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("Settings & Sidebar").assertIsDisplayed()

        // Reach Permission Center from Settings and return via Back button
        composeActivityRule.onNodeWithTag("settings_permission_center_button").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("permission_center_screen").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("permission_center_back_button").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("Settings & Sidebar").assertIsDisplayed()

        // Navigate to Pro from Settings button (still reachable with 5 tabs)
        composeActivityRule.onNodeWithTag("settings_pro_button").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("settings_lazy_column").assertDoesNotExist()

        // Return to Home via bottom tab
        composeActivityRule.onNodeWithTag("nav_tab_dashboard").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithText("GAMEBOOST 3.0").assertIsDisplayed()
    }

    @Test
    fun `settings refreshes overlay permission immediately on ON_RESUME after granting`() {
        ShadowSettings.setCanDrawOverlays(false)
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("nav_tab_settings").performClick()
        composeActivityRule.waitForIdle()
        composeActivityRule.onNodeWithTag("settings_overlay_grant_button").assertIsDisplayed()
        composeActivityRule.onNodeWithTag("settings_overlay_service_switch").assertDoesNotExist()

        // Simulate user granting SYSTEM_ALERT_WINDOW in Android Settings and returning to app (ON_RESUME)
        ShadowSettings.setCanDrawOverlays(true)
        composeActivityRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        composeActivityRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeActivityRule.waitForIdle()

        composeActivityRule.onNodeWithTag("settings_overlay_grant_button").assertDoesNotExist()
        composeActivityRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_overlay_service_switch"))
        composeActivityRule.onNodeWithTag("settings_overlay_service_switch").assertIsDisplayed()
    }
}
