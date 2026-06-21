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

package io.olvid.messenger.contact

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.group.components.GroupMemberAction
import io.olvid.messenger.group.components.MembersRow
import io.olvid.messenger.group.components.MembersScreenContainer
import io.olvid.messenger.group.components.toGroupMember

@Composable
fun ContactIntroductionScreen(
    bytesOwnedIdentity: ByteArray,
    bytesContactIdentity: ByteArray,
    displayName: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    val contacts by
    AppSingleton.getCurrentIdentityLiveData()
        .switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().contactDao()
                .getAllOneToOneForOwnedIdentityWithChannelExcludingOne(
                    ownedIdentity.bytesOwnedIdentity,
                    bytesContactIdentity
                )
        }.observeAsState()

    Box(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .fillMaxSize()
    ) {
        MembersScreenContainer(
            members = contacts.orEmpty().map { it.toGroupMember() },
            keycloakCertified = false,
            nonAdminsReadOnly = false,
            groupMemberAction = GroupMemberAction(
                actionPluralRes = R.plurals.label_introduce_contacts,
                actionColor = colorResource(R.color.olvid_gradient_light),
                startGravity = false,
                onActionClick = { selectedMembers ->
                    val bytesNewMemberIdentities = selectedMembers
                        .map { it.bytesIdentity }
                        .toTypedArray()
                    val introducedDisplayNames = StringUtils.joinContactDisplayNames(
                        selectedMembers.map {
                            it.contact?.getCustomDisplayName()
                        }.toTypedArray()
                    )

                    val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_contact_introduction)
                        .setPositiveButton(R.string.button_label_ok) { _, _ ->
                            try {
                                AppSingleton.getEngine().startContactMutualIntroductionProtocol(
                                    bytesOwnedIdentity,
                                    bytesContactIdentity,
                                    bytesNewMemberIdentities
                                )
                                App.toast(
                                    R.string.toast_message_contacts_introduction_started,
                                    Toast.LENGTH_SHORT
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onDone()
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    if (bytesNewMemberIdentities.size == 1) {
                        builder.setMessage(
                            context.getString(
                                R.string.dialog_message_contact_introduction,
                                displayName,
                                introducedDisplayNames
                            )
                        )
                    } else {
                        builder.setMessage(
                            context.getString(
                                R.string.dialog_message_contact_introduction_multiple,
                                displayName,
                                bytesNewMemberIdentities.size,
                                introducedDisplayNames
                            )
                        )
                    }
                    builder.create().show()
                }
            )
        ) { groupMembersViewModel ->
            MembersRow(
                modifier = Modifier.padding(bottom = 16.dp),
                members = groupMembersViewModel.allMembers.filter { it.selected },
                explanation = stringResource(R.string.explanation_choose_contact_introduction, displayName)
            ) {
                groupMembersViewModel.toggleMemberSelection(it.bytesIdentity)
            }
        }
    }
}
