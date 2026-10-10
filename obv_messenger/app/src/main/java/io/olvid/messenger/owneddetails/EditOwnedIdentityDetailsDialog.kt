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

package io.olvid.messenger.owneddetails

import android.view.Gravity
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.OwnedIdentityDao.OwnedIdentityPasswordAndSalt
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidPasswordInput
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.PUBLISH
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.Companion.computePINHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Compose replacement for the legacy EditOwnedIdentityDetailsDialogFragment + OwnedIdentityDetailsFragment.
 * Hosts the [EditOwnedIdentityDetailsScreen] form, owns the photo pickers (delegating cropping to
 * [SelectDetailsPhotoActivity]) and the hidden-profile password creation flow. [onPublish] performs the
 * actual engine update (see OwnedIdentityDetailsActivity.publishEditedDetails).
 */
@Composable
fun EditOwnedIdentityDetailsDialog(
    viewModel: OwnedIdentityDetailsViewModel,
    disableHidden: Boolean,
    onPublish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val valid by viewModel.valid.observeAsState()
    var profileHidden by remember { mutableStateOf(viewModel.isProfileHidden) }
    var showPasswordCreation by remember { mutableStateOf(false) }
    var showDiscardPasswordConfirmation by remember { mutableStateOf(false) }
    var showHideProfileConfirmation by remember { mutableStateOf(false) }
    var showUnHideProfileConfirmation by remember { mutableStateOf(false) }

    val photoPicker = rememberDetailsPhotoPicker { viewModel.absolutePhotoUrl = it }

    fun onHiddenClick() {
        if (disableHidden && !viewModel.isProfileHidden) {
            App.toast(
                R.string.toast_message_must_have_one_visible_profile,
                Toast.LENGTH_SHORT,
                Gravity.CENTER
            )
            viewModel.isProfileHidden = false
            profileHidden = false
        } else if (viewModel.isProfileHidden) {
            viewModel.isProfileHidden = false
            profileHidden = false
        } else {
            showPasswordCreation = true
        }
    }

    fun onCancelClicked() {
        if (viewModel.profileHiddenChanged() && viewModel.isProfileHidden) {
            // the user just hid a profile or changed the password of an already hidden profile,
            // ask for confirmation that he wants to discard his password
            showDiscardPasswordConfirmation = true
        } else {
            onDismiss()
        }
    }

    fun onPublishClicked() {
        when {
            viewModel.profileHiddenChanged() && !viewModel.isProfileHidden -> {
                // the user just un-hid a profile, ask for confirmation
                showUnHideProfileConfirmation = true
            }

            viewModel.profileHiddenChanged() && viewModel.isProfileHidden -> {
                // the user added or changed a password to hide a profile
                showHideProfileConfirmation = true
            }

            else -> {
                onDismiss()
                onPublish()
            }
        }
    }

    DialogSecure(
        onDismissRequest = ::onCancelClicked,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        BaseDialogContent(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 400.dp),
            title = stringResource(R.string.dialog_title_edit_identity_details),
            content = {
                EditOwnedIdentityDetailsScreen(
                    viewModel = viewModel,
                    showNicknameAndHidden = true,
                    profileHidden = profileHidden,
                    onPhotoTake = photoPicker.takePhoto,
                    onPhotoChoose = photoPicker.chooseImage,
                    onPhotoRemove = { viewModel.absolutePhotoUrl = null },
                    onChooseImageFromOlvid = viewModel.bytesOwnedIdentity?.let{
                         {
                            photoPicker.chooseFromOlvid(it)
                        }
                    },
                    onHiddenClick = ::onHiddenClick,
                )
            },
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    onClick = ::onCancelClicked,
                )
                Spacer(Modifier.width(8.dp))
                OlvidActionButton(
                    text = stringResource(
                        if (valid == PUBLISH || valid == null || valid == INVALID)
                            R.string.button_label_publish
                        else
                            R.string.button_label_save
                    ),
                    enabled = valid != null && valid != INVALID,
                    onClick = ::onPublishClicked,
                )
            }
        )
    }

    if (showPasswordCreation) {
        HiddenProfilePasswordCreationDialog(
            onDismiss = { showPasswordCreation = false },
            onPasswordSet = { hash, salt ->
                showPasswordCreation = false
                viewModel.setPasswordAndSalt(hash, salt)
                viewModel.isProfileHidden = true
                profileHidden = true
                App.toast(
                    R.string.toast_message_hidden_profile_password_set,
                    Toast.LENGTH_SHORT,
                    Gravity.CENTER
                )
            }
        )
    }

    if (showDiscardPasswordConfirmation) {
        DialogSecure(
            onDismissRequest = { showDiscardPasswordConfirmation = false },
        ) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_cancel_hide_profile),
                message = stringResource(R.string.dialog_message_cancel_hide_profile),
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showDiscardPasswordConfirmation = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_proceed),
                        onClick = onDismiss,
                    )
                }
            )
        }
    }

    if (showHideProfileConfirmation) {
        DialogSecure(
            onDismissRequest = { showHideProfileConfirmation = false },
        ) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_hide_profile),
                message = stringResource(R.string.dialog_message_hide_profile),
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showHideProfileConfirmation = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_proceed),
                        onClick = {
                            onDismiss()
                            onPublish()
                        }
                    )
                }
            )
        }
    }

    if (showUnHideProfileConfirmation) {
        DialogSecure(
            onDismissRequest = { showUnHideProfileConfirmation = false },
        ) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_unhide_profile),
                message = stringResource(R.string.dialog_message_unhide_profile),
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showUnHideProfileConfirmation = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_proceed),
                        onClick = {
                            onDismiss()
                            onPublish()
                        }
                    )
                }
            )
        }
    }
}

/**
 * Compose replacement for HiddenProfilePasswordCreationDialogFragment: lets the user choose a password
 * (with confirmation) for a new hidden profile, rejecting passwords already used by another hidden profile.
 */
@Composable
fun HiddenProfilePasswordCreationDialog(
    onDismiss: () -> Unit,
    onPasswordSet: (hash: ByteArray, salt: ByteArray) -> Unit,
) {
    val first = remember { mutableStateOf("") }
    val second = remember { mutableStateOf("") }
    var existingPasswordsAndSalts by remember {
        mutableStateOf<List<OwnedIdentityPasswordAndSalt>?>(null)
    }

    LaunchedEffect(Unit) {
        existingPasswordsAndSalts = withContext(Dispatchers.IO) {
            AppDatabase.getInstance().ownedIdentityDao().getHiddenIdentityPasswordsAndSalts()
        }
    }

    val tooShort = first.value.isNotEmpty() && first.value.length < 4
    val mismatch = second.value.isNotEmpty() && first.value != second.value
    val canCreate = first.value.length >= 4 && first.value == second.value

    DialogSecure(onDismissRequest = onDismiss) {
        BaseDialogContent(
            title = stringResource(R.string.dialog_title_hidden_profile_password_creation),
            content = {
                Text(
                    text = stringResource(R.string.text_hidden_profile_password_creation_explanation),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                OlvidPasswordInput(
                    modifier = Modifier.fillMaxWidth(),
                    password = first,
                    label = stringResource(R.string.hint_enter_password),
                    isError = tooShort,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                OlvidPasswordInput(
                    modifier = Modifier.fillMaxWidth(),
                    password = second,
                    label = stringResource(R.string.hint_confirm_password),
                    enabled = first.value.length >= 4,
                    isError = mismatch,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                val errorText = when {
                    tooShort -> stringResource(R.string.error_text_password_too_short)
                    mismatch -> stringResource(R.string.error_text_password_mismatch)
                    else -> null
                }
                if (errorText != null) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = errorText,
                        style = OlvidTypography.subtitle1,
                        color = colorResource(R.color.red),
                    )
                }
            },
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(8.dp))
                OlvidActionButton(
                    text = stringResource(R.string.button_label_create_password),
                    enabled = canCreate,
                    onClick = {
                        val password = first.value
                        val collision = existingPasswordsAndSalts?.any { entry ->
                            runCatching {
                                val hash = computePINHash(password, entry.unlock_salt)
                                hash != null && hash.contentEquals(entry.unlock_password)
                            }.getOrDefault(false)
                        } ?: false
                        if (collision) {
                            App.toast(
                                R.string.toast_message_password_already_used_other_profile,
                                Toast.LENGTH_SHORT
                            )
                        } else {
                            val salt = ByteArray(SettingsActivity.PIN_SALT_LENGTH)
                            SecureRandom().nextBytes(salt)
                            val hash = computePINHash(password, salt)
                            if (hash != null) {
                                onPasswordSet(hash, salt)
                            } else {
                                App.toast(
                                    R.string.toast_message_hidden_profile_password_failed,
                                    Toast.LENGTH_SHORT,
                                    Gravity.CENTER
                                )
                            }
                        }
                    },
                )
            }
        )
    }
}
