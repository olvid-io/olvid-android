/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.lock_screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.DarkGradientBackground
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.animations.shake
import io.olvid.messenger.settings.SettingsActivity

@Composable
fun LockScreen(
    viewModel: LockScreenViewModel,
    isNeutral: Boolean,
    customMessage: String?,
    setupMode: Boolean,
    onBiometricRequest: () -> Unit,
    onSetupPinRequest: () -> Unit,
) {
    val pinLocked by viewModel.pinLocked.collectAsState()
    val biometryAvailable by viewModel.biometryAvailable.collectAsState()
    val keyWiped by viewModel.keyWiped.collectAsState()

    val isPinAPassword = SettingsActivity.isPINAPassword

    var pinValue by remember { mutableStateOf("") }
    var shakeError by remember { mutableStateOf(false) }

    // Auto-open biometrics once when not locked
    LaunchedEffect(pinLocked, biometryAvailable) {
        if (!pinLocked && biometryAvailable && !setupMode) {
            onBiometricRequest()
        }
        // reset the PIN everytime we get locked out
        if (pinLocked) {
            pinValue = ""
        }
    }

    DarkGradientBackground {
        CompositionLocalProvider(LocalContentColor provides colorResource(id = R.color.alwaysWhite)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(0.15f))

                // Logo or custom message
                when {
                    customMessage != null -> {
                        Text(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            text = customMessage,
                            style = OlvidTypography.h3.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }

                    !isNeutral -> {
                        Image(
                            painter = painterResource(id = R.drawable.olvid),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .padding(horizontal = 16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))

                if (setupMode) {
                    // Setup mode UI
                    SetupModeContent(onSetupPinRequest = onSetupPinRequest)
                } else {
                    // Normal unlock UI
                    UnlockContent(
                        pinValue = pinValue,
                        isPinAPassword = isPinAPassword,
                        pinLocked = pinLocked,
                        lockoutRemainingSeconds = viewModel.lockoutRemainingSeconds,
                        biometryAvailable = biometryAvailable,
                        keyWiped = keyWiped,
                        shakeError = shakeError,
                        onPinChange = { newValue ->
                            pinValue = newValue
                            // reset the shake every time we type a character so it does not shake only once every 2 attempts
                            shakeError = false
                            viewModel.onPinChange(newValue)
                            // ViewModel signals Activity via unlockRequested
                        },
                        onConfirm = {
                            val unlocked = viewModel.onPinChange(pinValue)
                            if (!unlocked && !pinLocked && pinValue.isNotEmpty()) {
                                shakeError = !shakeError
                                pinValue = ""
                            }
                        },
                        onBiometricRequest = onBiometricRequest,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UnlockContent(
    pinValue: String,
    isPinAPassword: Boolean,
    pinLocked: Boolean,
    lockoutRemainingSeconds: State<Int>,
    biometryAvailable: Boolean,
    keyWiped: Boolean,
    shakeError: Boolean,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBiometricRequest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // PIN input row or lockout box
        Crossfade(
            targetState = lockoutRemainingSeconds.value > 0,
        ) { lockedOut ->
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(lockedOut) {
                if (!lockedOut && (!biometryAvailable || keyWiped)) {
                    focusRequester.requestFocus()
                }
            }

            if (lockedOut) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorResource(R.color.alwaysWhite),
                    contentColor = colorResource(R.color.red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.message_too_many_attempts),
                            style = OlvidTypography.body1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.x_seconds,
                                lockoutRemainingSeconds.value
                            ),
                            style = OlvidTypography.h2,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .shake(shakeError)
                        .focusRequester(focusRequester),
                ) {
                    TextField(
                        modifier = Modifier.padding(start = 48.dp, end = 8.dp)
                            .widthIn(max = 200.dp),
                        value = pinValue,
                        onValueChange = onPinChange,
                        enabled = !pinLocked,
                        placeholder = {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                text = stringResource(if (isPinAPassword) R.string.hint_enter_password else R.string.hint_enter_pin),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isPinAPassword) KeyboardType.Password else KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.White.copy(alpha = 0.7f),
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            disabledIndicatorColor = Color.White.copy(alpha = 0.3f),
                            disabledTextColor = Color.White.copy(alpha = 0.5f),
                        ),
                        textStyle = OlvidTypography.h1.copy(textAlign = TextAlign.Center),
                    )

                    IconButton(
                        modifier = Modifier.size(40.dp),
                        enabled = !pinLocked,
                        onClick = onConfirm,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Biometric button or key-wiped warning
        when {
            keyWiped -> {
                Text(
                    text = stringResource(
                        if (isPinAPassword) R.string.message_biometric_enrollment_detected_password
                        else R.string.message_biometric_enrollment_detected_pin
                    ),
                    style = OlvidTypography.body1,
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            biometryAvailable -> {
                IconButton(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colorResource(R.color.alwaysWhite).copy(alpha = 0.15f)),
                    onClick = onBiometricRequest
                ) {
                    Icon(
                        modifier = Modifier.size(64.dp),
                        painter = painterResource(R.drawable.ic_pref_unlock_biometry),
                        tint = colorResource(R.color.olvid_gradient_light),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupModeContent(
    onSetupPinRequest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorResource(R.color.alwaysWhite).copy(alpha = 0.15f),
            contentColor = colorResource(R.color.alwaysWhite),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 16.dp),
                )
                Text(
                    text = stringResource(R.string.lock_screen_setup_title),
                    style = OlvidTypography.h2,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    modifier = Modifier.alpha(.85f),
                    text = stringResource(R.string.lock_screen_setup_explanation),
                    style = OlvidTypography.body1,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OlvidActionButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    text = stringResource(R.string.lock_screen_setup_button),
                    contentColor = colorResource(R.color.olvid_gradient_light),
                    containerColor = colorResource(R.color.alwaysWhite),
                    large = true,
                    allowTwoLines = true,
                    onClick = onSetupPinRequest
                )
            }
        }
    }
}