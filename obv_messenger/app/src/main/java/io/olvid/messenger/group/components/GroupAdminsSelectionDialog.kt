/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.olvid.messenger.R
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.main.contacts.ContactListItem

@Composable
fun GroupAdminsSelectionDialog(
    stateOpened: Boolean,
    changeOpenedState: (opened: Boolean) -> Unit,
    admins: HashSet<Contact>?,
    selectAdmins: (HashSet<Contact>) -> Unit,
    members: List<Contact>?
) {
    if (stateOpened) {
        Dialog(onDismissRequest = {
            changeOpenedState(false)
        }) {
            Box(modifier = Modifier.padding(16.dp)) {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    elevation = 8.dp,
                    color = colorResource(id = R.color.dialogBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            top = 16.dp,
                            bottom = 16.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                modifier = Modifier.size(24.dp),
                                onClick = {
                                    changeOpenedState(false)
                                }) {
                                Icon(
                                    painter = painterResource(id = drawable.ic_arrow_back),
                                    contentDescription = stringResource(
                                        id = string.content_description_back_button
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(id = string.label_group_choose_admins),
                                fontSize = 24.sp,
                                fontWeight = FontWeight(500)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .clickable {
                                    if (admins?.size != members?.size) {
                                        members?.let {
                                            selectAdmins(HashSet(it))
                                        }
                                    } else {
                                        selectAdmins(hashSetOf())
                                    }
                                }
                                .padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = stringResource(id = string.menu_action_select_all),
                                fontSize = 16.sp,
                                fontWeight = FontWeight(500)
                            )
                            Switch(
                                checked = admins?.size == members?.size,
                                onCheckedChange = null,
                                enabled = members?.size != 0,
                                colors = SwitchDefaults.colors(
                                    uncheckedThumbColor = colorResource(id = R.color.alwaysLightGrey)
                                )
                            )
                        }
                        members?.let {
                            LazyColumn {
                                items(it) { contact ->
                                    ContactListItem(
                                        title = AnnotatedString(contact.getCustomDisplayName()),
                                        body = null,
                                        onClick = {
                                            val newAdmins = HashSet(admins ?: emptyList())
                                            if (!newAdmins.remove(contact)) {
                                                newAdmins.add(contact)
                                            }
                                            selectAdmins(newAdmins)
                                        },
                                        initialViewSetup = { it.setContact(contact) },
                                        endContent = {
                                            Switch(
                                                checked = admins?.contains(contact) == true,
                                                onCheckedChange = null,
                                                colors = SwitchDefaults.colors(
                                                    uncheckedThumbColor = colorResource(id = R.color.alwaysLightGrey)
                                                )
                                            )
                                        },
                                        useDialogBackgroundColor = true,
                                        additionalHorizontalPadding = 8.dp,
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