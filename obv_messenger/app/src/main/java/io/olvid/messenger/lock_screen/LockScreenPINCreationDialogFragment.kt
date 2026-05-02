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

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidPasswordInput
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity

class LockScreenPINCreationDialogFragment : DialogFragment() {
    private var onPINSetCallback: Runnable? = null

    companion object {
        @JvmStatic
        fun newInstance() = LockScreenPINCreationDialogFragment()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (SettingsActivity.preventScreenCapture(context)) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    fun setOnPINSetCallBack(callBack: Runnable) {
        onPINSetCallback = callBack
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(inflater.context).apply {
            setContent {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    PINCreationContent(
                        isPinAPassword = SettingsActivity.isPINAPassword,
                        onCancel = { dismiss() },
                        onPINSet = { pin, isPassword ->
                            SettingsActivity.savePIN(pin = pin, pinIsAPassword = isPassword)
                            dismiss()
                            App.toast(
                                if (isPassword) R.string.toast_message_new_password_set else R.string.toast_message_new_pin_set,
                                Toast.LENGTH_SHORT
                            )
                            onPINSetCallback?.run()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PINCreationContent(
    isPinAPassword: Boolean,
    onCancel: () -> Unit,
    onPINSet: (String, Boolean) -> Unit,
) {
    var usePassword by remember { mutableStateOf(isPinAPassword) }
    val firstPin = remember { mutableStateOf("") }
    val secondPin = remember { mutableStateOf("") }

    val firstFocusRequester = remember { FocusRequester() }
    val secondFocusRequester = remember { FocusRequester() }

    val firstTooShort = firstPin.value.isNotEmpty() && firstPin.value.length < 4
    val secondEnabled = firstPin.value.length >= 4
    val pinsMismatch = secondEnabled && secondPin.value.isNotEmpty() && firstPin.value != secondPin.value
    val createEnabled = secondEnabled && firstPin.value == secondPin.value

    val errorMessage = when {
        firstTooShort -> stringResource(if (usePassword) R.string.error_text_password_too_short else R.string.error_text_pin_too_short)
        pinsMismatch -> stringResource(if (usePassword) R.string.error_text_password_mismatch else R.string.error_text_pin_mismatch)
        else -> ""
    }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }

    BaseDialogContent(
        modifier = Modifier
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .widthIn(max = 320.dp),
        title = stringResource(R.string.dialog_title_pin_creation),
        content = {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = stringResource(R.string.text_pin_creation_explanation).formatMarkdownToAnnotatedString(),
                style = OlvidTypography.body2,
                color = colorResource(R.color.greyTint),
            )

            OlvidPasswordInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocusRequester),
                label = stringResource(if (usePassword) R.string.hint_enter_password else R.string.hint_enter_pin),
                password = firstPin,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (usePassword) KeyboardType.Password else KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { if (secondEnabled) secondFocusRequester.requestFocus() }
                ),
                isError = firstTooShort
            )

            Spacer(Modifier.height(8.dp))

            OlvidPasswordInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(secondFocusRequester),
                label = stringResource(if (usePassword) R.string.hint_confirm_password else R.string.hint_confirm_pin),
                password = secondPin,
                enabled = secondEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (usePassword) KeyboardType.Password else KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (createEnabled) onPINSet(firstPin.value, usePassword) }
                ),
                isError = pinsMismatch,
            )

            // always rendered to avoid layout shifts
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                text = errorMessage,
                style = OlvidTypography.body2,
                color = colorResource(R.color.red),
            )

            OlvidTextButton(
                modifier = Modifier.align(Alignment.End).height(40.dp),
                text = stringResource(if (usePassword) R.string.button_label_use_pin else R.string.button_label_use_password),
                onClick = {
                    usePassword = !usePassword
                    firstPin.value = ""
                    secondPin.value = ""
                }
            )
        },
        actions = {
            Spacer(Modifier.weight(1f, true))
            OlvidTextButton(
                text = stringResource(R.string.button_label_cancel),
                onClick = onCancel,
            )
            Spacer(Modifier.width(8.dp))
            OlvidActionButton(
                text = stringResource(R.string.button_label_create_pin),
                enabled = createEnabled,
                onClick = {
                    onPINSet(firstPin.value, usePassword)
                },
            )
        }
    )
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun PINCreationContentPreview() {
    PINCreationContent(
        isPinAPassword = false,
        onCancel = {},
        onPINSet = { _, _ ->

        },
    )
}