/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.settings.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.backupKeyStyle


@Composable
fun BackupKeyTextField(
    backupSeed : MutableState<String?>,
    backupSeedError : MutableState<Boolean>,
    onValidateSeed: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        modifier = Modifier
            .width(270.dp)
            .focusRequester(focusRequester)
            .semantics {
                contentType = ContentType.Password
            },
        value = backupSeed.value ?: "",
        onValueChange = {
            backupSeed.value = it
            backupSeedError.value = false
        },
        minLines = 2,
        placeholder = {
            Text(
                text = stringResource(R.string.hint_backup_key),
                style = backupKeyStyle(),
                color = colorResource(R.color.mediumGrey),
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorResource(R.color.almostBlack),
            unfocusedTextColor = colorResource(R.color.almostBlack),
            focusedBorderColor = colorResource(R.color.almostBlack),
            unfocusedBorderColor = colorResource(R.color.greyTint),
            errorBorderColor = colorResource(R.color.red),
            focusedLabelColor = colorResource(R.color.almostBlack),
            unfocusedLabelColor = colorResource(R.color.greyTint),
            errorLabelColor = colorResource(R.color.red),
            errorSupportingTextColor = colorResource(R.color.red),
        ),
        label = {
            Text(
                text = stringResource(R.string.label_backup_key),
                style = OlvidTypography.subtitle1,
                maxLines = 1,
            )
        },
        textStyle = backupKeyStyle(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = true,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                onValidateSeed.invoke()
            }
        ),
        isError = backupSeedError.value,
        supportingText = {
            AnimatedVisibility(
                visible = backupSeedError.value
            ) {
                Text(
                    text = stringResource(R.string.explanation_backup_seed_format),
                    style = OlvidTypography.subtitle1,
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ReadOnlyBackupKeyTextField(
    backupSeed: String,
) {
    OutlinedTextField(
        modifier = Modifier
            .width(270.dp)
            .semantics {
                contentType = ContentType.NewPassword
            },
        value = backupSeed,
        readOnly = true,
        onValueChange = { },
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorResource(R.color.almostBlack),
            focusedBorderColor = colorResource(R.color.greyTint),
            focusedLabelColor = colorResource(R.color.greyTint),
            unfocusedTextColor = colorResource(R.color.almostBlack),
            unfocusedBorderColor = colorResource(R.color.greyTint),
            unfocusedLabelColor = colorResource(R.color.greyTint),
        ),
        label = {
            Text(
                text = stringResource(R.string.label_backup_key),
                style = OlvidTypography.subtitle1,
                maxLines = 1,
            )
        },
        textStyle = backupKeyStyle(),
        shape = RoundedCornerShape(12.dp),
    )
}