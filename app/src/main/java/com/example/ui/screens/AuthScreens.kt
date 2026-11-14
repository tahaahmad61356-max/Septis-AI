package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CountryCode
import com.example.ui.components.CountryCodePickerDialog
import com.example.ui.viewmodel.AdminAuthStage

@Composable
fun PhoneRegistrationScreen(
    selectedCountry: CountryCode,
    nationalNumber: String,
    isBusy: Boolean,
    errorBanner: String?,
    infoBanner: String?,
    onSelectCountry: (CountryCode) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onSendOtpClick: (Activity) -> Unit,
    onGoogleRecoveryClick: () -> Unit,
    onAdminPortalClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showCountryDialog by remember { mutableStateOf(false) }

    if (showCountryDialog) {
        CountryCodePickerDialog(
            onSelect = {
                onSelectCountry(it)
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAdminPortalClick,
                    modifier = Modifier.testTag("open_admin_gate_button")
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Admin")
                }
            }

            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(id = R.drawable.septis_logo),
                contentDescription = "SEPTIS Logo",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Welcome to SEPTIS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Verify your phone number to connect with friends, groups, and real-time voice or video calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
            Spacer(Modifier.height(24.dp))

            if (!errorBanner.isNullOrBlank()) {
                AuthBanner(errorBanner, isError = true)
                Spacer(Modifier.height(12.dp))
            }
            if (!infoBanner.isNullOrBlank()) {
                AuthBanner(infoBanner, isError = false)
                Spacer(Modifier.height(12.dp))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Phone number (Compulsory)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showCountryDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("country_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedCountry.flag}  ${selectedCountry.name} (${selectedCountry.dialCode})")
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick country")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedCountry.dialCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = nationalNumber,
                            onValueChange = onPhoneChanged,
                            label = { Text("Phone number") },
                            placeholder = { Text(selectedCountry.example) },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("phone_number_input")
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { if (activity != null) onSendOtpClick(activity) },
                        enabled = !isBusy && activity != null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("send_otp_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Sms, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Verify with SMS OTP")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onGoogleRecoveryClick,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_recovery_button")
                    ) {
                        Text("Account Recovery / Sign in with Google")
                    }
                }
            }
        }
    }
}

@Composable
fun OtpVerificationScreen(
    phoneE164: String,
    otpCode: String,
    isBusy: Boolean,
    errorBanner: String?,
    infoBanner: String?,
    onOtpChanged: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: (Activity) -> Unit,
    onBackClick: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    BackHandler { onBackClick() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Verify $phoneE164",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter the 6-digit SMS code sent to your phone number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            if (!errorBanner.isNullOrBlank()) {
                AuthBanner(errorBanner, isError = true)
                Spacer(Modifier.height(12.dp))
            }
            if (!infoBanner.isNullOrBlank()) {
                AuthBanner(infoBanner, isError = false)
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = otpCode,
                onValueChange = onOtpChanged,
                label = { Text("6-digit OTP code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .testTag("otp_code_input")
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onVerifyClick,
                enabled = !isBusy && otpCode.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(52.dp)
                    .testTag("verify_otp_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Verify & Continue")
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { if (activity != null) onResendClick(activity) },
                enabled = !isBusy && activity != null
            ) {
                Text("Resend SMS code")
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(
    phoneE164: String,
    displayName: String,
    about: String,
    avatarUri: android.net.Uri?,
    isBusy: Boolean,
    errorBanner: String?,
    onDisplayNameChanged: (String) -> Unit,
    onAboutChanged: (String) -> Unit,
    onAvatarSelected: (android.net.Uri?) -> Unit,
    onSaveProfileClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onAvatarSelected(uri) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Set up your SEPTIS profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Verified phone: $phoneE164",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .testTag("setup_avatar_picker"),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Selected profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = "Add profile photo",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            if (!errorBanner.isNullOrBlank()) {
                AuthBanner(errorBanner, isError = true)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChanged,
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag("profile_name_input")
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = about,
                onValueChange = onAboutChanged,
                label = { Text("About") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag("profile_about_input")
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onSaveProfileClick,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .height(52.dp)
                    .testTag("complete_profile_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Start Messaging")
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onLogoutClick) {
                Text("Use another account")
            }
        }
    }
}

@Composable
fun AdminGateScreen(
    stage: AdminAuthStage,
    isBusy: Boolean,
    errorBanner: String?,
    currentUserEmail: String?,
    onBack: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onSetupCode: (String, String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onSignOutClick: () -> Unit
) {
    BackHandler { onBack() }
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("SEPTIS Admin Gate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))

            if (!errorBanner.isNullOrBlank()) {
                AuthBanner(errorBanner, isError = true)
                Spacer(Modifier.height(12.dp))
            }

            when (stage) {
                AdminAuthStage.NeedsGoogleAuth -> {
                    Text(
                        "Sign in with an authorized administrator Google account to continue.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onGoogleSignInClick,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp).height(50.dp)
                    ) {
                        Text("Sign in with Google (Admin)")
                    }
                }
                AdminAuthStage.SetupAccessCode -> {
                    Text(
                        "Initialize Confidential Admin Access Code (${currentUserEmail.orEmpty()})",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("New Admin Access Code (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Admin Access Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onSetupCode(code, confirm) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp).height(50.dp)
                    ) {
                        Text("Save & Enter Admin Panel")
                    }
                }
                AdminAuthStage.VerifyAccessCode -> {
                    Text(
                        "Enter Confidential Admin Access Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Admin Access Code") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onVerifyCode(code) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp).height(50.dp)
                    ) {
                        Text("Unlock Admin Panel")
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onSignOutClick) { Text("Sign out") }
                }
                is AdminAuthStage.Denied -> {
                    AuthBanner(stage.reason, isError = true)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onGoogleSignInClick) { Text("Switch Google Account") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text("Return") }
                }
            }
        }
    }
}

@Composable
fun AccountSuspendedScreen(
    onLogoutClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Account Restricted",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This SEPTIS phone account is currently suspended or deactivated by an administrator.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onLogoutClick) { Text("Sign out") }
        }
    }
}

@Composable
private fun AuthBanner(message: String, isError: Boolean) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}
