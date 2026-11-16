package com.example

import com.example.data.MembershipManager
import com.example.data.MembershipTier
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MembershipManagerTest {

    @Before
    fun setup() {
        MembershipManager.resetForTesting()
    }

    @After
    fun tearDown() {
        MembershipManager.resetForTesting()
    }

    @Test
    fun testDefaultTierIsFree() {
        val state = MembershipManager.entitlement.value
        assertEquals(MembershipTier.FREE, state.tier)
        assertFalse(state.isPremium)
        assertEquals(10, state.maxExportHistoryCount)
        assertFalse(state.allowProThemes)
        assertFalse(state.allowExtendedComparison)
        assertFalse(state.allowOverlayTheming)
        assertFalse(state.allowProProfileCustomization)
        assertFalse(state.allowAdvancedSessionReports)
        assertTrue(state.billingStatusMessage.contains("Google Play Billing"))
    }

    @Test
    fun testDevToggleEntitlements() {
        val initialIsPrem = MembershipManager.isPremium()
        if (BuildConfig.DEBUG) {
            MembershipManager.toggleDevPreviewPremium()
            val toggled = MembershipManager.entitlement.value
            assertNotEquals(initialIsPrem, toggled.isPremium)
            assertTrue(toggled.isDevPreviewOverride)

            // Revert back
            MembershipManager.toggleDevPreviewPremium()
            val reverted = MembershipManager.entitlement.value
            assertEquals(initialIsPrem, reverted.isPremium)
            assertFalse(reverted.isDevPreviewOverride)
        }
    }
}
