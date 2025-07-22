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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2DetailsAndPhotos
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.group.GroupV2DetailsViewModel

val NOTIFICATIONS_TO_LISTEN_TO = arrayOf(
    EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
    EngineNotifications.GROUP_V2_PHOTO_CHANGED,
    EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED,
    EngineNotifications.GROUP_V2_UPDATE_FAILED
)

@Composable
fun GroupUpdatesListener(
    groupDetailsViewModel: GroupV2DetailsViewModel,
    onUpdate: (ObvGroupV2DetailsAndPhotos?) -> Unit
) {
    DisposableEffect(LocalContext.current) {
        var registrationNumber: Long? = null
        val groupListener =
            object : EngineNotificationListener {
                override fun callback(
                    notificationName: String,
                    userInfo: java.util.HashMap<String, Any>
                ) {
                    when (notificationName) {
                        EngineNotifications.GROUP_V2_CREATED_OR_UPDATED -> {
                            val groupV2 =
                                userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY] as ObvGroupV2?
                            if (groupV2 != null && groupV2.bytesOwnedIdentity.contentEquals(
                                    groupDetailsViewModel.bytesOwnedIdentity
                                )
                                && groupV2.groupIdentifier.bytes.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                            ) {
                                onUpdate(groupV2.detailsAndPhotos)
                            }
                        }

                        EngineNotifications.GROUP_V2_PHOTO_CHANGED -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesGroupIdentifier =
                                userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                            if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                                && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                            ) {
                                onUpdate(null)
                            }
                        }

                        EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesGroupIdentifier =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                            val updating =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY] as Boolean?
                            if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                                && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                            ) {
                                if (updating == true) {
                                    groupDetailsViewModel.loaderState = LoaderState.LOADING
                                } else if (updating == false) {
                                    groupDetailsViewModel.loaderState = LoaderState.SUCCESS
                                    groupDetailsViewModel.publicationFinished()
                                }
                            }
                        }

                        EngineNotifications.GROUP_V2_UPDATE_FAILED -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesGroupIdentifier =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                            val error =
                                userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_ERROR_KEY] as Boolean?
                            if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                                && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                            ) {
                                if (error == true) {
                                    groupDetailsViewModel.loaderState = LoaderState.ERROR
                                    groupDetailsViewModel.publicationFinished()
                                }
                            }
                        }
                    }
                }

                override fun setEngineNotificationListenerRegistrationNumber(number: Long) {
                    registrationNumber = number
                }

                override fun getEngineNotificationListenerRegistrationNumber(): Long {
                    return registrationNumber ?: 0
                }

                override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                    return registrationNumber != null
                }
            }
        NOTIFICATIONS_TO_LISTEN_TO.forEach {
            AppSingleton.getEngine()
                .addNotificationListener(it, groupListener)
        }

        onDispose {
            NOTIFICATIONS_TO_LISTEN_TO.forEach {
                AppSingleton.getEngine()
                    .removeNotificationListener(it, groupListener)
            }
        }
    }
}