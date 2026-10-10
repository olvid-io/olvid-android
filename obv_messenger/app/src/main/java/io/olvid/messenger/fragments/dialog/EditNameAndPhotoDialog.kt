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

package io.olvid.messenger.fragments.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidSlider
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.avatar.avatarTextColorArgb
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoViewModel.Type
import io.olvid.messenger.owneddetails.rememberDetailsPhotoPicker

/**
 * Compose replacement for the legacy dialog_fragment_edit_name_and_photo layout. Lets the user set a
 * local nickname, personal note, custom photo and (for contacts) a custom name hue. State is held in the
 * shared activity-scoped [EditNameAndPhotoViewModel]; the actual engine update is dispatched by
 * [EditNameAndPhotoViewModel.commit].
 */
@Composable
fun EditNameAndPhotoDialog(
    viewModel: EditNameAndPhotoViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val type = viewModel.type ?: return

    val valid by viewModel.valid.observeAsState(false)
    val initialViewContent by viewModel.initialViewLiveData.observeAsState()
    val customNameHue by viewModel.customNameHueLiveData.observeAsState()

    var name by remember { mutableStateOf(viewModel.getNickname().orEmpty()) }
    var personalNote by remember { mutableStateOf(viewModel.getPersonalNote().orEmpty()) }
    var hueSlider by remember { mutableFloatStateOf((viewModel.getCustomNameHue() ?: 180).toFloat()) }
    var showPhotoMenu by remember { mutableStateOf(false) }

    val photoPicker = rememberDetailsPhotoPicker { viewModel.setAbsolutePhotoUrl(it) }
    val tfColors = olvidDefaultTextFieldColors()

    fun commitAndDismiss() {
        viewModel.commit()
        onDismiss()
    }

    val title = when (type) {
        Type.GROUP, Type.GROUP_V2 -> R.string.dialog_title_rename_group
        Type.CONTACT -> R.string.dialog_title_rename_contact
        Type.LOCKED_DISCUSSION -> R.string.dialog_title_rename_discussion
    }
    val showPersonalNote = type != Type.LOCKED_DISCUSSION
    val showCustomHue = type == Type.CONTACT
    val showReset = type != Type.LOCKED_DISCUSSION

    val nameColor: Color = customNameHue?.let {
        Color(avatarTextColorArgb(context, ByteArray(1), it))
    } ?: colorResource(R.color.almostBlack)

    DialogSecure(onDismissRequest = onDismiss) {
    BaseDialogContent(
        title = stringResource(title),
        content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(
                            modifier = Modifier.requiredSize(64.dp),
                            editable = true,
                            onClick = { showPhotoMenu = true },
                            target = initialViewContent.let { content ->
                                when {
                                    content == null -> AvatarSource.unknown()
                                    content.absolutePhotoUrl != null -> AvatarSource.of(
                                        content.bytesInitial,
                                        null,
                                        content.absolutePhotoUrl,
                                        content.type == Type.LOCKED_DISCUSSION,
                                    )
                                    else -> when (content.type) {
                                        Type.GROUP, Type.GROUP_V2 -> AvatarSource.group(content.bytesInitial)
                                        Type.CONTACT -> AvatarSource.initial(content.bytesInitial, content.initial)
                                        Type.LOCKED_DISCUSSION -> AvatarSource.of(ByteArray(0), "", null, true)
                                        null -> AvatarSource.unknown()
                                    }
                                }
                            },
                        )
                        OlvidDropdownMenu(
                            expanded = showPhotoMenu,
                            onDismissRequest = { showPhotoMenu = false }
                        ) {
                            OlvidDropdownMenuItem(
                                text = stringResource(R.string.menu_action_take_photo),
                                onClick = {
                                    showPhotoMenu = false
                                    photoPicker.takePhoto()
                                }
                            )
                            OlvidDropdownMenuItem(
                                text = stringResource(R.string.menu_action_choose_picture),
                                onClick = {
                                    showPhotoMenu = false
                                    photoPicker.chooseImage()
                                }
                            )
                            OlvidDropdownMenuItem(
                                text = stringResource(R.string.menu_action_choose_image_from_olvid),
                                onClick = {
                                    showPhotoMenu = false
                                    (viewModel.discussion?.bytesOwnedIdentity
                                        ?: viewModel.contact?.bytesOwnedIdentity
                                        ?: viewModel.group2?.bytesOwnedIdentity
                                        ?: viewModel.group?.bytesOwnedIdentity)
                                            ?.let {
                                                photoPicker.chooseFromOlvid(it)
                                            }
                                }
                            )
                            if (initialViewContent?.absolutePhotoUrl != null) {
                                OlvidDropdownMenuItem(
                                    text = stringResource(R.string.menu_action_remove_image),
                                    textColor = colorResource(R.color.red),
                                    onClick = {
                                        showPhotoMenu = false
                                        viewModel.setAbsolutePhotoUrl(null)
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        value = name,
                        onValueChange = {
                            name = it
                            viewModel.setNickname(it)
                        },
                        label = { Text(stringResource(R.string.hint_new_name)) },
                        singleLine = true,
                        colors = tfColors,
                        textStyle = OlvidTypography.body1.copy(color = nameColor),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Words
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitAndDismiss() })
                    )
                }

                if (showCustomHue) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.toggleable(
                                value = customNameHue != null,
                                onValueChange = { checked ->
                                    if (checked) {
                                        viewModel.setCustomNameHue(hueSlider.toInt())
                                    } else {
                                        viewModel.setCustomNameHue(null)
                                    }
                                }
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = customNameHue != null,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colorResource(R.color.olvid_gradient_light),
                                )
                            )
                            Text(
                                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                                text = stringResource(R.string.label_custom_contact_color),
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                            )
                        }
                        OlvidSlider(
                            modifier = Modifier.weight(1f),
                            value = hueSlider,
                            valueRange = 0f..360f,
                            onValueChange = {
                                hueSlider = it
                                viewModel.setCustomNameHue(it.toInt())
                            },
                        )
                    }
                }

                if (showPersonalNote) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        value = personalNote,
                        onValueChange = {
                            personalNote = it
                            viewModel.setPersonalNote(it)
                        },
                        label = { Text(stringResource(R.string.hint_personal_note)) },
                        colors = tfColors,
                        textStyle = OlvidTypography.body1,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            },
            actions = {
                if (showReset) {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_remove_nickname),
                        onClick = {
                            hueSlider = 180f
                            viewModel.reset()
                            name = viewModel.getNickname().orEmpty()
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(8.dp))
                OlvidActionButton(
                    text = stringResource(R.string.button_label_ok),
                    enabled = valid,
                    onClick = { commitAndDismiss() },
                )
            }
        )
    }
}
