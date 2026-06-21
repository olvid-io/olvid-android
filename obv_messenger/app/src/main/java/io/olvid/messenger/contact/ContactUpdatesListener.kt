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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.AppSingleton

private val NOTIFICATIONS_TO_LISTEN_TO = arrayOf(
    EngineNotifications.NEW_CONTACT_PHOTO,
    EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS,
    EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED,
)

@Composable
fun ContactUpdatesListener(
    contactDetailsViewModel: ContactDetailsViewModel,
    onUpdate: (ContactDetailsViewModel.ContactAndInvitation?) -> Unit
) {
    DisposableEffect(LocalContext.current) {
        var registrationNumber: Long? = null
        val contactListener =
            object : EngineNotificationListener {
                override fun callback(
                    notificationName: String,
                    userInfo: HashMap<String, Any>
                ) {
                    when (notificationName) {
                        EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesContactIdentity =
                                userInfo[EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY] as ByteArray?
                            contactDetailsViewModel.contactAndInvitation?.value?.let { contactAndInvitation ->
                                if (contactAndInvitation.contact.bytesContactIdentity.contentEquals(
                                        bytesContactIdentity
                                    ) && contactAndInvitation.contact.bytesOwnedIdentity.contentEquals(
                                        bytesOwnedIdentity
                                    )
                                ) {
                                    onUpdate(contactAndInvitation)
                                }
                            }
                        }

                        EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesContactIdentity =
                                userInfo[EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY] as ByteArray?
                            contactDetailsViewModel.contactAndInvitation?.value?.let { contactAndInvitation ->
                                if (contactAndInvitation.contact.bytesContactIdentity.contentEquals(
                                        bytesContactIdentity
                                    ) && contactAndInvitation.contact.bytesOwnedIdentity.contentEquals(
                                        bytesOwnedIdentity
                                    )
                                ) {
                                    onUpdate(contactAndInvitation)
                                }
                            }
                        }

                        EngineNotifications.NEW_CONTACT_PHOTO -> {
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                            val bytesContactIdentity =
                                userInfo[EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY] as ByteArray?
                            val isTrusted =
                                userInfo[EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY] as Boolean?
                            contactDetailsViewModel.contactAndInvitation?.value?.let { contactAndInvitation ->
                                if (isTrusted != null && !isTrusted
                                    && contactAndInvitation.contact.bytesContactIdentity.contentEquals(
                                        bytesContactIdentity
                                    ) && contactAndInvitation.contact.bytesOwnedIdentity.contentEquals(
                                        bytesOwnedIdentity
                                    )
                                ) {
                                    onUpdate(contactAndInvitation)
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
                .addNotificationListener(it, contactListener)
        }

        onDispose {
            NOTIFICATIONS_TO_LISTEN_TO.forEach {
                AppSingleton.getEngine()
                    .removeNotificationListener(it, contactListener)
            }
        }
    }
}