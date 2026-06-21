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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.OwnedIdentityDao.OwnedIdentityPasswordAndSalt
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HiddenProfilePasswordDialog(
    onDismiss: () -> Unit,
    onIdentitySelected: (ByteArray) -> Unit,
) {
    var password = remember { mutableStateOf("") }
    var passwordAndSalts by remember { mutableStateOf<List<OwnedIdentityPasswordAndSalt>?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun checkPassword(pwd: String, salts: List<OwnedIdentityPasswordAndSalt>) {
        if (pwd.isEmpty()) return
        for (entry in salts) {
            try {
                val hash = SettingsActivity.computePINHash(pwd, entry.unlock_salt)
                if (hash != null && hash.contentEquals(entry.unlock_password)) {
                    onIdentitySelected(entry.bytes_owned_identity)
                    return
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        val salts = withContext(Dispatchers.IO) {
            AppDatabase.getInstance().ownedIdentityDao().getHiddenIdentityPasswordsAndSalts()
        }
        passwordAndSalts = salts
        // In case the user typed before salts finished loading
        if (password.value.isNotEmpty()) {
            checkPassword(password.value, salts)
        }
    }

    LaunchedEffect(password.value) {
        passwordAndSalts?.let {
            checkPassword(password.value, it)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DialogSecure(onDismissRequest = onDismiss) {
        BaseDialogContent(
            title = stringResource(R.string.dialog_title_open_hidden_profile),
            content = {
                OlvidPasswordInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    password = password,
                )
            },
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    onClick = onDismiss,
                    contentColor = colorResource(R.color.greyTint)
                )
            }
        )
    }
}
