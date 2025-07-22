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

package io.olvid.messenger.group.components

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import io.olvid.messenger.group.GroupV2DetailsActivity
import io.olvid.messenger.group.OwnedGroupDetailsViewModel
import io.olvid.messenger.main.InitialView

@Composable
fun EditGroupDetailsScreen(
    editGroupDetailsViewModel: OwnedGroupDetailsViewModel,
    isGroupCreation: Boolean = false,
    content: (@Composable () -> Unit)? = null,
    onTakePicture: () -> Unit,
    onValidate: () -> Unit
) {
    val activity = LocalActivity.current as AppCompatActivity
    val initialViewContent by editGroupDetailsViewModel.getInitialViewContent().observeAsState()
    val valid by editGroupDetailsViewModel.valid.observeAsState()

    Box(modifier = Modifier
        .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 64.dp)
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            var photoMenuExpanded by remember { mutableStateOf(false) }
            Column {
                InitialView(
                    modifier = Modifier
                        .size(112.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false,
                                color = colorResource(R.color.blueOrWhiteOverlay)
                            ),
                        ) { photoMenuExpanded = true },
                    editable = true,
                    initialViewSetup = { initialView ->
                        initialViewContent?.absolutePhotoUrl?.let {
                            initialView.setAbsolutePhotoUrl(
                                initialViewContent?.bytesGroupOwnerAndUid,
                                it
                            )
                        } ?: run {
                            initialView.setGroup(initialViewContent?.bytesGroupOwnerAndUid)
                        }
                    })
                OlvidDropdownMenu(
                    expanded = photoMenuExpanded,
                    onDismissRequest = { photoMenuExpanded = false }) {
                    if (initialViewContent?.absolutePhotoUrl != null) {
                        OlvidDropdownMenuItem(
                            text = stringResource(R.string.menu_action_remove_image),
                            onClick = {
                                editGroupDetailsViewModel.setAbsolutePhotoUrl(null)
                                photoMenuExpanded = false
                            })
                    }
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_choose_picture),
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT)
                                .setType("image/*")
                                .addCategory(Intent.CATEGORY_OPENABLE)
                            App.startActivityForResult(
                                activity,
                                intent,
                                GroupV2DetailsActivity.REQUEST_CODE_CHOOSE_IMAGE
                            )
                            photoMenuExpanded = false
                        })
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_take_photo),
                        onClick = {
                            onTakePicture()
                            photoMenuExpanded = false
                        })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = editGroupDetailsViewModel.groupName.value.orEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = olvidDefaultTextFieldColors(),
                onValueChange = { editGroupDetailsViewModel.setGroupName(it) },
                maxLines = 1,
                label = {
                    Text(stringResource(R.string.hint_group_name))
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = editGroupDetailsViewModel.groupDescription.orEmpty(),
                onValueChange = { editGroupDetailsViewModel.groupDescription = it },
                shape = RoundedCornerShape(12.dp),
                colors = olvidDefaultTextFieldColors(),
                minLines = 3,
                maxLines = 3,
                label = {
                    Text(stringResource(R.string.hint_group_description))
                }
            )
            if (isGroupCreation.not()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box {
                    OutlinedTextField(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        value = editGroupDetailsViewModel.personalNote.orEmpty(),
                        onValueChange = { editGroupDetailsViewModel.personalNote = it },
                        shape = RoundedCornerShape(12.dp),
                        colors = olvidDefaultTextFieldColors(),
                        minLines = 3,
                        maxLines = 3,
                        label = {
                            Text(stringResource(R.string.hint_personal_note))
                        })
                }
            }
            content?.let {
                Spacer(modifier = Modifier.height(16.dp))
                it.invoke()
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            visible = (isGroupCreation) || editGroupDetailsViewModel.detailsChanged() || editGroupDetailsViewModel.photoChanged() || editGroupDetailsViewModel.personalNoteChanged(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.whiteOverlay)),
                contentAlignment = Alignment.BottomCenter
            ) {
                OlvidActionButton(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .safeDrawingPadding(),
                    enabled = valid == true,
                    text = if (isGroupCreation)
                        stringResource(R.string.button_label_create_group)
                    else if (editGroupDetailsViewModel.detailsChanged() || editGroupDetailsViewModel.photoChanged())
                        stringResource(R.string.button_label_publish)
                    else
                        stringResource(R.string.button_label_save)
                ) {
                    onValidate()
                }
            }
        }
    }
}