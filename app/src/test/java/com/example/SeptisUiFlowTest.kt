package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.CountryCodes
import com.example.data.model.PhoneUtils
import com.example.ui.components.WhatsAppComposer
import com.example.ui.screens.AccountSuspendedScreen
import com.example.ui.screens.AdminGateScreen
import com.example.ui.screens.PhoneRegistrationScreen
import com.example.ui.theme.SeptisTheme
import com.example.ui.viewmodel.AdminAuthStage
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
    fun phoneRegistrationScreen_rendersCountryPickerPhoneInputAndAdminGate() {
        var adminTapped = false
        var recoveryTapped = false

        composeRule.setContent {
            SeptisTheme {
                PhoneRegistrationScreen(
                    selectedCountry = CountryCodes.defaultCountry,
                    nationalNumber = "",
                    isBusy = false,
                    errorBanner = null,
                    infoBanner = null,
                    onSelectCountry = {},
                    onPhoneChanged = {},
                    onSendOtpClick = {},
                    onGoogleRecoveryClick = { recoveryTapped = true },
                    onAdminPortalClick = { adminTapped = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Welcome to SEPTIS").assertIsDisplayed()
        composeRule.onNodeWithTag("country_picker_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("phone_number_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("send_otp_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_recovery_button").performScrollTo().performClick()
        assertTrue(recoveryTapped)
        composeRule.onNodeWithTag("open_admin_gate_button").performClick()
        assertTrue(adminTapped)
    }

    @Test
    fun whatsAppComposer_switchesFromMicToSendWhenUserTypes() {
        composeRule.setContent {
            SeptisTheme {
                var text by remember { mutableStateOf("") }
                WhatsAppComposer(
                    text = text,
                    replyTo = null,
                    editingMessage = null,
                    uploadProgress = null,
                    isRecording = false,
                    recordingDurationMs = 0L,
                    liveWaveform = emptyList(),
                    onTextChanged = { text = it },
                    onSendText = {},
                    onSendSticker = {},
                    onCancelReplyOrEdit = {},
                    onPickPhoto = {},
                    onPickVideo = {},
                    onPickDocument = {},
                    onPickAudio = {},
                    onOpenCamera = {},
                    onStartRecording = {},
                    onCancelRecording = {},
                    onFinishAndSendRecording = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_mic_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_message_input").performTextInput("Hello SEPTIS!")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_send_button").assertIsDisplayed()
    }

    @Test
    fun adminGateScreen_enforcesAccessNotices() {
        composeRule.setContent {
            SeptisTheme {
                AdminGateScreen(
                    stage = AdminAuthStage.Denied("Unauthorized admin account"),
                    isBusy = false,
                    errorBanner = null,
                    currentUserEmail = "user@test.com",
                    onBack = {},
                    onGoogleSignInClick = {},
                    onSetupCode = { _, _ -> },
                    onVerifyCode = {},
                    onSignOutClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Unauthorized admin account").assertIsDisplayed()
    }

    @Test
    fun accountSuspendedScreen_enforcesRestrictionNotice() {
        composeRule.setContent {
            SeptisTheme {
                AccountSuspendedScreen(onLogoutClick = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account Restricted").assertIsDisplayed()
    }

    @Test
    fun phoneUtilsAndCrypto_produceDeterministicNormalizedFormats() {
        assertEquals("+923001234567", PhoneUtils.combineToE164("+92", "0300-1234567"))
        assertEquals("+14155550199", PhoneUtils.normalizeToE164("+1 415 555 0199", "+92"))
        assertEquals("p_923001234567", PhoneUtils.toDirectoryKey("+923001234567"))

        val salt = CryptoUtils.generateSaltHex()
        val h1 = CryptoUtils.hashSecret(salt, "SecretCode")
        val h2 = CryptoUtils.hashSecret(salt, "SecretCode")
        assertEquals(h1, h2)
        assertNotEquals(h1, CryptoUtils.hashSecret(salt, "Wrong"))
    }
}
