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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.OlvidPasswordInput
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity

class LockScreenPINVerificationDialogFragment : DialogFragment() {
    private var onPINEnteredCallback: Runnable? = null

    companion object {
        @JvmStatic
        fun newInstance() = LockScreenPINVerificationDialogFragment()
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

    fun setOnPINEnteredCallBack(callBack: Runnable) {
        onPINEnteredCallback = callBack
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(inflater.context).apply {
            setContent {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    PINVerificationContent(
                        isPassword = SettingsActivity.isPINAPassword,
                        onCancel = { dismiss() },
                        verifyPin = { pin ->
                            SettingsActivity.verifyPIN(pin)
                        },
                        onPINVerified = {
                            dismiss()
                            onPINEnteredCallback?.run()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PINVerificationContent(
    isPassword: Boolean,
    onCancel: () -> Unit,
    verifyPin: (String) -> Boolean,
    onPINVerified: () -> Unit,
) {
    val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(pin.value) {
        if (verifyPin(pin.value)) {
            onPINVerified()
        }
    }

    BaseDialogContent(
        modifier = Modifier
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .widthIn(max = 320.dp),
        title = stringResource(R.string.dialog_title_pin_verification),
        content = {
            Text(
                text = stringResource(R.string.text_pin_verification_explanation),
                style = OlvidTypography.body2,
                color = colorResource(R.color.greyTint),
            )

            Spacer(Modifier.height(16.dp))

            OlvidPasswordInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                password = pin,
                label = stringResource(if (isPassword) R.string.hint_enter_password else R.string.hint_enter_pin),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.NumberPassword,
                ),
            )

        },
        actions = {
            Spacer(Modifier.weight(1f, true))
            OlvidTextButton(
                text = stringResource(R.string.button_label_cancel),
                onClick = onCancel,
            )
        }
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun PINVerificationContentPreview() {
    PINVerificationContent(
        isPassword = false,
        onCancel = {},
        verifyPin = { true },
        onPINVerified = {},
    )
}