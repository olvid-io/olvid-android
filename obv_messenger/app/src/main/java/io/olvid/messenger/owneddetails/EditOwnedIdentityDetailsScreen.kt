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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.InitialViewContent
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID

/**
 * Presentational Compose form to edit the published details of the owned identity (first/last name,
 * company, position) plus, when [showNicknameAndHidden] is true, the local nickname and the hidden
 * profile checkbox. State lives in the shared [OwnedIdentityDetailsViewModel]; photo and hidden
 * password interactions are delegated to the hosting dialog via the callbacks.
 * [focusFirstName] focuses the empty first name field on open, so typing doesn't go to the nickname.
 */
@Composable
fun EditOwnedIdentityDetailsScreen(
    viewModel: OwnedIdentityDetailsViewModel,
    showNicknameAndHidden: Boolean,
    profileHidden: Boolean,
    onPhotoTake: () -> Unit,
    onPhotoChoose: () -> Unit,
    onPhotoRemove: () -> Unit,
    onChooseImageFromOlvid: ((bytesOwnedIdentity: ByteArray) -> Unit)?,
    onHiddenClick: () -> Unit,
    focusFirstName: Boolean = false,
) {
    val initialViewContent: InitialViewContent? by viewModel.initialViewContent.observeAsState()
    val valid by viewModel.valid.observeAsState()

    // first/last/company/position are bound directly to the (Compose-state-backed) view-model — single
    // source of truth, so async prefills (keycloak) and the locked state are reflected immediately.
    // nickname keeps local state so trailing spaces aren't trimmed while typing (its getter trims).
    var nickname by remember { mutableStateOf(viewModel.nickname.orEmpty()) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    // only surface the "first or last name needed" error once the user has actually typed in a name
    // field (matching the legacy fragment, which skipped the initial validation emission)
    var nameTouched by remember { mutableStateOf(false) }
    val showNameError = nameTouched && (valid == null || valid == INVALID)

    val fieldsEnabled = !viewModel.detailsLocked
    val tfColors = olvidDefaultTextFieldColors()

    val firstNameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusFirstName && fieldsEnabled && viewModel.firstName.isNullOrEmpty() && viewModel.lastName.isNullOrEmpty()) {
            firstNameFocusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        // Header: photo (with edit menu) + nickname/hidden checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(
                    modifier = Modifier.requiredSize(64.dp),
                    editable = !viewModel.pictureLocked,
                    target = initialViewContent.let { content ->
                        AvatarSource.Single(
                            bytes = content?.bytesOwnedIdentity ?: ByteArray(0),
                            initial = content?.initial ?: "",
                            photoUrl = content?.absolutePhotoUrl,
                            keycloakCertified = viewModel.detailsLocked,
                            inactive = viewModel.isIdentityInactive,
                        )
                    },
                    onClick = { if (!viewModel.pictureLocked) showPhotoMenu = true },
                )
                OlvidDropdownMenu(
                    expanded = showPhotoMenu,
                    onDismissRequest = { showPhotoMenu = false }
                ) {
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_take_photo),
                        onClick = {
                            showPhotoMenu = false
                            onPhotoTake()
                        }
                    )
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_choose_picture),
                        onClick = {
                            showPhotoMenu = false
                            onPhotoChoose()
                        }
                    )
                    if (onChooseImageFromOlvid != null) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_choose_image_from_olvid),
                            onClick = {
                                showPhotoMenu = false
                                viewModel.bytesOwnedIdentity?.let {
                                    onChooseImageFromOlvid(it)
                                }
                            }
                        )
                    }
                    if (initialViewContent?.absolutePhotoUrl != null) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_remove_image),
                            textColor = colorResource(R.color.red),
                            onClick = {
                                showPhotoMenu = false
                                onPhotoRemove()
                            }
                        )
                    }
                }
            }

            if (showNicknameAndHidden) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = nickname,
                        shape = RoundedCornerShape(12.dp),
                        onValueChange = {
                            nickname = it
                            viewModel.nickname = it
                        },
                        label = { Text(stringResource(R.string.hint_profile_nickname)) },
                        singleLine = true,
                        colors = tfColors,
                        textStyle = OlvidTypography.body1,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Words
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = profileHidden,
                                onValueChange = { onHiddenClick() }
                            )
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = profileHidden,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = colorResource(R.color.olvid_gradient_light),
                            )
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(R.string.checkbox_label_hidden_profile),
                            style = OlvidTypography.body2,
                            color = colorResource(R.color.greyTint),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(firstNameFocusRequester),
                value = viewModel.firstName.orEmpty(),
                shape = RoundedCornerShape(12.dp),
                enabled = fieldsEnabled,
                onValueChange = {
                    nameTouched = true
                    viewModel.firstName = it
                },
                label = { Text(stringResource(R.string.hint_first_name)) },
                isError = showNameError,
                singleLine = true,
                colors = tfColors,
                textStyle = OlvidTypography.body1,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Words
                )
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = viewModel.lastName.orEmpty(),
                shape = RoundedCornerShape(12.dp),
                enabled = fieldsEnabled,
                onValueChange = {
                    nameTouched = true
                    viewModel.lastName = it
                },
                label = { Text(stringResource(R.string.hint_last_name)) },
                isError = showNameError,
                singleLine = true,
                colors = tfColors,
                textStyle = OlvidTypography.body1,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Words
                )
            )
        }

        if (showNameError) {
            Text(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                text = stringResource(R.string.message_error_first_or_last_name_needed),
                style = OlvidTypography.subtitle1,
                color = colorResource(R.color.red),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = viewModel.position.orEmpty(),
                shape = RoundedCornerShape(12.dp),
                enabled = fieldsEnabled,
                onValueChange = {
                    viewModel.position = it
                },
                label = { Text(stringResource(R.string.hint_position)) },
                singleLine = true,
                colors = tfColors,
                textStyle = OlvidTypography.body1,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = viewModel.company.orEmpty(),
                shape = RoundedCornerShape(12.dp),
                enabled = fieldsEnabled,
                onValueChange = {
                    viewModel.company = it
                },
                label = { Text(stringResource(R.string.hint_company)) },
                singleLine = true,
                colors = tfColors,
                textStyle = OlvidTypography.body1,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Words
                )
            )
        }
    }
}
