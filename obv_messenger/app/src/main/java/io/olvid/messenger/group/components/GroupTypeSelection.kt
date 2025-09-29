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

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.CustomGroup
import io.olvid.messenger.group.GroupTypeModel
import io.olvid.messenger.group.GroupTypeModel.GroupType.CUSTOM
import io.olvid.messenger.group.PrivateGroup
import io.olvid.messenger.group.ReadOnlyGroup
import io.olvid.messenger.group.SimpleGroup

@Composable
fun GroupTypeSelection(
    groupTypes: List<GroupTypeModel>,
    selectedGroupType: GroupTypeModel,
    selectGroupType: (groupTypeModel: GroupTypeModel) -> Unit,
    content: @Composable () -> Unit = {},
    updateReadOnly: ((Boolean) -> Unit)? = null,
    updateRemoteDelete: ((GroupTypeModel.RemoteDeleteSetting) -> Unit)? = null,
    showTitle: Boolean = true,
    isGroupCreation: Boolean = false,
    validationLabel: String,
    getPermissionsChanges: () -> String,
    initialGroupType: GroupTypeModel?,
    onValidate: (() -> Unit)? = null,
    onSelectAdmins: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    Box {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            content()
            if (showTitle) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 6.dp),
                    text = stringResource(R.string.label_group_type),
                    style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 64.dp)
                    .safeDrawingPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupTypes.forEach { group ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = colorResource(
                                        id = if (selectedGroupType.type == group.type) R.color.olvid_gradient_light else R.color.lightGrey
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (selectedGroupType.type == group.type) Modifier.background(
                                    color = colorResource(id = R.color.lighterGrey),
                                    shape = RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .selectable(
                                selected = selectedGroupType.type == group.type,
                                onClick = { selectGroupType(group) },
                                role = Role.RadioButton,
                                interactionSource = interactionSource,
                                indication = null,
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        RadioButton(
                            modifier = Modifier.size(20.dp),
                            selected = selectedGroupType.type == group.type,
                            onClick = { selectGroupType(group) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(
                                    id = R.color.olvid_gradient_light
                                )
                            ),
                            interactionSource = interactionSource
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(group.title),
                                        style = OlvidTypography.body1
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(group.subtitle),
                                    style = OlvidTypography.body2.copy(
                                        color = colorResource(
                                            id = R.color.greyTint
                                        )
                                    )
                                )
                                AnimatedVisibility(
                                    visible = group.type == CUSTOM && selectedGroupType.type == CUSTOM
                                ) {
                                    GroupCustomSettings(
                                        groupType = selectedGroupType,
                                        updateReadOnly = {
                                            updateReadOnly?.invoke(it)
                                        },
                                        updateRemoteDelete = {
                                            updateRemoteDelete?.invoke(it)
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
        onValidate?.let {
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = isGroupCreation || selectedGroupType != initialGroupType,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.whiteOverlay)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fromSimpleToAdmin = !isGroupCreation && initialGroupType?.type == SimpleGroup.type && selectedGroupType.type != SimpleGroup.type

                    OlvidActionButton(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .safeDrawingPadding(),
                        text = if (fromSimpleToAdmin) stringResource(id = R.string.label_group_choose_admins) else validationLabel,
                    ) {
                        if (fromSimpleToAdmin) {
                            onSelectAdmins?.invoke()
                        } else {
                            val changes = getPermissionsChanges()
                            if (changes.isEmpty().not()) {
                                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                                    .setTitle(context.getString(R.string.dialog_permissions_change_title))
                                    .setMessage(changes.formatMarkdown())
                                    .setPositiveButton(context.getString(R.string.button_label_ok)) { _, _ ->
                                        onValidate()
                                    }
                                    .setNegativeButton(
                                        context.getString(R.string.button_label_cancel),
                                        null
                                    )
                                    .show()
                            } else {
                                onValidate()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupTypeSelectionPreview() {
    val groupTypes = listOf(
        SimpleGroup,
        PrivateGroup,
        ReadOnlyGroup,
        CustomGroup(),
    )

    Box(Modifier
        .background(colorResource(R.color.almostWhite))
        .consumeWindowInsets(WindowInsets.safeDrawing)) {
        GroupTypeSelection(
            groupTypes = groupTypes,
            selectedGroupType = PrivateGroup,
            validationLabel = "Validate",
            initialGroupType = SimpleGroup,
            getPermissionsChanges = { "" },
            selectGroupType = { },
            onValidate = {},
            onSelectAdmins = {},
        )
    }
}