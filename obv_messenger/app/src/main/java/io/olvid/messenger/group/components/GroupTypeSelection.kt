/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.group.GroupTypeModel
import io.olvid.messenger.group.GroupTypeModel.GroupType.CUSTOM
import io.olvid.messenger.group.GroupTypeModel.GroupType.PRIVATE
import io.olvid.messenger.group.GroupTypeModel.GroupType.READ_ONLY
import io.olvid.messenger.group.GroupTypeModel.GroupType.SIMPLE

@Composable
fun GroupTypeSelection(
    groupTypes: List<GroupTypeModel>,
    selectedGroupType: GroupTypeModel,
    selectGroupType: (groupTypeModel: GroupTypeModel) -> Unit,
    isEditingCustomSettings: ((Boolean) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(
            start = 6.dp,
            end = 6.dp,
            top = 16.dp,
            bottom = 8.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = string.label_group_type),
                fontSize = 18.sp,
                lineHeight = 20.sp,
                color = colorResource(
                    id = color.accent
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            groupTypes.forEach { group ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = colorResource(
                                    id = if (selectedGroupType.type == group.type) color.olvid_gradient_light else color.lightGrey
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (selectedGroupType.type == group.type) Modifier.background(
                                color = colorResource(id = color.lightGrey),
                                shape = RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .selectable(
                            selected = selectedGroupType.type == group.type,
                            onClick = { selectGroupType(group) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    RadioButton(
                        modifier = Modifier.size(20.dp),
                        selected = selectedGroupType.type == group.type,
                        onClick = { selectGroupType(group) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colorResource(
                                id = color.olvid_gradient_light
                            )
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.body1
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = group.subtitle,
                                style = MaterialTheme.typography.body2.copy(
                                    color = colorResource(
                                        id = color.greyTint
                                    )
                                )
                            )
                        }
                        if (group.type == CUSTOM) {
                            isEditingCustomSettings?.let {
                                IconButton(
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                                    onClick = { it.invoke(true) }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pencil),
                                        tint = colorResource(
                                            id = color.greyTint
                                        ),
                                        contentDescription = "Edit"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun GroupTypeSelectionPreview() {
    AppCompatTheme {
        val groupTypes = listOf(
            GroupTypeModel(SIMPLE, "Public", "Public"),
            GroupTypeModel(PRIVATE, "Private", "Private"),
            GroupTypeModel(READ_ONLY, "ReadOnly", "ReadOnly"),
            GroupTypeModel(CUSTOM, "Custom", "Custom"),
        )
        var groupType by remember {
            mutableStateOf(groupTypes.first())
        }
        GroupTypeSelection(
            groupTypes = groupTypes,
            selectedGroupType = groupType,
            selectGroupType = { groupCreationModel -> groupType = groupCreationModel })
    }
}