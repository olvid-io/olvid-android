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

package io.olvid.messenger.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors

@Composable
fun OlvidPasswordInput(
    modifier: Modifier = Modifier,
    password: MutableState<String>,
    label: String = stringResource(R.string.hint_password),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    initiallyShowPassword: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var showPassword by remember { mutableStateOf(initiallyShowPassword) }
    OutlinedTextField(
        modifier = modifier,
        value = password.value,
        shape = RoundedCornerShape(12.dp),
        colors = olvidDefaultTextFieldColors(),
        readOnly = readOnly,
        enabled = enabled,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        onValueChange = { password.value = it },
        trailingIcon = {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colorResource(R.color.greyTint)
                ),
                onClick = { showPassword = !showPassword }
            ) {
                Icon(
                    painter = painterResource(if (showPassword) R.drawable.hide_password else R.drawable.show_password),
                    contentDescription = null
                )
            }
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        label = {
            Text(text = label)
        }
    )
}