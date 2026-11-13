package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.UserLoginScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.SeptisTheme
import com.example.ui.viewmodel.AdminAuthStage
import com.example.ui.viewmodel.UserAuthStage
import com.example.util.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class SeptisUiFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeScreen_displaysBothAdminAndUserLoginModes() {
        var adminClicked = false
        var userClicked = false

        composeRule.setContent {
            SeptisTheme {
                WelcomeScreen(
                    isOnline = true,
                    onAdminLoginClick = { adminClicked = true },
                    onUserLoginClick = { userClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("SEPTIS").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_admin_login_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(adminClicked)

        composeRule.onNodeWithTag("welcome_user_login_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(userClicked)
    }

    @Test
    fun adminLoginScreen_unauthorizedUserIsBlockedFromAdminDashboard() {
        composeRule.setContent {
            SeptisTheme {
                AdminLoginScreen(
                    stage = AdminAuthStage.Unauthorized("Backend denied Admin access for this Google account."),
                    isBusy = false,
                    errorBanner = null,
                    currentUserEmail = "ordinary@test.com",
                    onBack = {},
                    onGoogleSignInClick = {},
                    onInitializeCode = { _, _ -> },
                    onVerifyAccessCode = {},
                    onSignOutClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Access Denied").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_unauthorized_signout").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun userLoginScreen_suspendedAccountShowsRestrictionNotice() {
        composeRule.setContent {
            SeptisTheme {
                UserLoginScreen(
                    stage = UserAuthStage.Restricted("suspended", "member@test.com"),
                    isBusy = false,
                    errorBanner = null,
                    onBack = {},
                    onGoogleSignInClick = {},
                    onSignOutClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Account Suspended").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("user_restricted_signout").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cryptoUtils_generatesSaltedSha256Hashes() {
        val salt1 = CryptoUtils.generateSaltHex()
        val salt2 = CryptoUtils.generateSaltHex()
        assertEquals(32, salt1.length)
        assertNotEquals(salt1, salt2)

        val hash1 = CryptoUtils.hashSecret(salt1, "MySecretCode")
        val hash2 = CryptoUtils.hashSecret(salt1, "MySecretCode")
        val hashDifferent = CryptoUtils.hashSecret(salt1, "OtherCode")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashDifferent)
    }
}
