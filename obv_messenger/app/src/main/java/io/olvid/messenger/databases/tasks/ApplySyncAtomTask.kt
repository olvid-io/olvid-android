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
package io.olvid.messenger.databases.tasks

import io.olvid.engine.Logger
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.engine.engine.types.sync.ObvSyncAtom.DiscussionIdentifier
import io.olvid.engine.engine.types.sync.ObvSyncAtom.MuteNotification
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.settings.SettingsActivity
import java.util.UUID

class ApplySyncAtomTask(private val dialogUuid: UUID, private val bytesOwnedIdentity: ByteArray, private val obvSyncAtom: ObvSyncAtom) : Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()
        db.runInTransaction {
            try {
                when (obvSyncAtom.syncType) {
                    ObvSyncAtom.TYPE_CONTACT_NICKNAME_CHANGE -> {
                        db.contactDao()[bytesOwnedIdentity, obvSyncAtom.bytesContactIdentity]?.let { contact ->
                            UpdateContactCustomDisplayNameAndPhotoTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity, obvSyncAtom.stringValue, App.absolutePathFromRelative(contact.customPhotoUrl), contact.customNameHue, contact.personalNote, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_CONTACT_PERSONAL_NOTE_CHANGE -> {
                        db.contactDao()[bytesOwnedIdentity, obvSyncAtom.bytesContactIdentity]?.let { contact ->
                            UpdateContactCustomDisplayNameAndPhotoTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.customDisplayName, App.absolutePathFromRelative(contact.customPhotoUrl), contact.customNameHue, obvSyncAtom.stringValue, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V1_NICKNAME_CHANGE -> {
                        db.groupDao()[bytesOwnedIdentity, obvSyncAtom.bytesGroupOwnerAndUid]?.let { group ->
                            UpdateGroupCustomNameAndPhotoTask(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, obvSyncAtom.stringValue, App.absolutePathFromRelative(group.customPhotoUrl), group.personalNote, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V1_PERSONAL_NOTE_CHANGE -> {
                        db.groupDao()[bytesOwnedIdentity, obvSyncAtom.bytesGroupOwnerAndUid]?.let { group ->
                            UpdateGroupCustomNameAndPhotoTask(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid, group.customName, App.absolutePathFromRelative(group.customPhotoUrl), obvSyncAtom.stringValue, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V2_NICKNAME_CHANGE -> {
                        db.group2Dao()[bytesOwnedIdentity, obvSyncAtom.bytesGroupIdentifier]?.let { group2 ->
                            UpdateGroupV2CustomNameAndPhotoTask(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier, obvSyncAtom.stringValue, App.absolutePathFromRelative(group2.customPhotoUrl), group2.personalNote, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V2_PERSONAL_NOTE_CHANGE -> {
                        db.group2Dao()[bytesOwnedIdentity, obvSyncAtom.bytesGroupIdentifier]?.let { group2 ->
                            UpdateGroupV2CustomNameAndPhotoTask(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier, group2.customName, App.absolutePathFromRelative(group2.customPhotoUrl), obvSyncAtom.stringValue, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_OWN_PROFILE_NICKNAME_CHANGE -> {
                        db.ownedIdentityDao().updateCustomDisplayName(bytesOwnedIdentity, obvSyncAtom.stringValue)
                        AppSingleton.getEngine().deviceBackupNeeded()
                    }
                    ObvSyncAtom.TYPE_CONTACT_CUSTOM_HUE_CHANGE -> {
                        db.contactDao()[bytesOwnedIdentity, obvSyncAtom.bytesContactIdentity]?.let { contact ->
                            UpdateContactCustomDisplayNameAndPhotoTask(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.customDisplayName, App.absolutePathFromRelative(contact.customPhotoUrl), obvSyncAtom.integerValue, contact.personalNote, true).run()
                        }
                    }
                    ObvSyncAtom.TYPE_CONTACT_SEND_READ_RECEIPT_CHANGE -> {
                        db.discussionDao().getByContact(bytesOwnedIdentity, obvSyncAtom.bytesContactIdentity)?.let { discussion ->
                            db.discussionCustomizationDao()[discussion.id]?.let { discussionCustomization ->
                                db.discussionCustomizationDao().update(discussionCustomization.apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            } ifNull {
                                db.discussionCustomizationDao().insert(DiscussionCustomization(discussion.id).apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            }
                            AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V1_SEND_READ_RECEIPT_CHANGE -> {
                        db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, obvSyncAtom.bytesGroupOwnerAndUid)?.let { discussion ->
                            db.discussionCustomizationDao()[discussion.id]?.let { discussionCustomization ->
                                db.discussionCustomizationDao().update(discussionCustomization.apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            } ifNull {
                                db.discussionCustomizationDao().insert(DiscussionCustomization(discussion.id).apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            }
                            AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                        }
                    }
                    ObvSyncAtom.TYPE_GROUP_V2_SEND_READ_RECEIPT_CHANGE -> {
                        db.discussionDao().getByGroupIdentifier(bytesOwnedIdentity, obvSyncAtom.bytesGroupIdentifier)?.let { discussion ->
                            db.discussionCustomizationDao()[discussion.id]?.let { discussionCustomization ->
                                db.discussionCustomizationDao().update(discussionCustomization.apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            } ifNull {
                                db.discussionCustomizationDao().insert(DiscussionCustomization(discussion.id).apply {
                                    this.prefSendReadReceipt = obvSyncAtom.booleanValue
                                })
                            }
                            AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                        }
                    }
                    ObvSyncAtom.TYPE_PINNED_DISCUSSIONS_CHANGE -> {
                        val pinnedDiscussions = db.discussionDao().getAllPinned(bytesOwnedIdentity)
                        val pinnedDiscussionsMap = HashMap(pinnedDiscussions.associateBy { discussion -> discussion.id })
                        val ordered = obvSyncAtom.booleanValue
                        var pinnedIndex = if (ordered) 1 else (pinnedDiscussions.maxOf { it.pinned } + 1)
                        obvSyncAtom.discussionIdentifiers.forEach { discussionIdentifier ->
                            val discussion = when(discussionIdentifier.type) {
                                DiscussionIdentifier.CONTACT -> {
                                    db.discussionDao().getByContactWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                DiscussionIdentifier.GROUP_V1 -> {
                                    db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                DiscussionIdentifier.GROUP_V2 -> {
                                    db.discussionDao().getByGroupIdentifierWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                else -> null
                            }
                            discussion?.let {
                                // never pin a pre-discussion
                                if (it.status != Discussion.STATUS_PRE_DISCUSSION) {
                                    pinnedDiscussionsMap.remove(it.id)

                                    if (ordered) {
                                        if (it.pinned != pinnedIndex) {
                                            // update ordered index
                                            db.discussionDao().updatePinned(it.id, pinnedIndex)
                                        }
                                        pinnedIndex++
                                    } else {
                                        // when not order, only change pinned for discussions that were not already pinned
                                        // set there pin index to one more than the max pinned
                                        if (it.pinned == 0) {
                                            db.discussionDao().updatePinned(it.id, pinnedIndex)
                                            pinnedIndex++
                                        }
                                    }
                                }
                            }
                        }

                        // unpin any discussion that is not in the received list
                        pinnedDiscussionsMap.values.forEach {
                            db.discussionDao().updatePinned(it.id, 0)
                        }
                        AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                    }
                    ObvSyncAtom.TYPE_SETTING_DEFAULT_SEND_READ_RECEIPTS -> {
                        SettingsActivity.defaultSendReadReceipt = obvSyncAtom.booleanValue
                        AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                    }
                    ObvSyncAtom.TYPE_SETTING_AUTO_JOIN_GROUPS -> {
                        SettingsActivity.autoJoinGroups = SettingsActivity.getAutoJoinGroupsFromString(obvSyncAtom.stringValue)
                        AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                    }
                    ObvSyncAtom.TYPE_BOOKMARKED_MESSAGE_CHANGE -> {
                        obvSyncAtom.messageIdentifier.discussionIdentifier?.let { discussionIdentifier ->
                            val discussion = when(discussionIdentifier.type) {
                                DiscussionIdentifier.CONTACT -> {
                                    db.discussionDao().getByContactWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                DiscussionIdentifier.GROUP_V1 -> {
                                    db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                DiscussionIdentifier.GROUP_V2 -> {
                                    db.discussionDao().getByGroupIdentifierWithAnyStatus(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier)
                                }
                                else -> null
                            }
                            discussion?.let {
                                val message = db.messageDao().getBySenderSequenceNumber(obvSyncAtom.messageIdentifier.senderSequenceNumber, obvSyncAtom.messageIdentifier.senderThreadIdentifier, obvSyncAtom.messageIdentifier.senderIdentifier, discussion.id)
                                message?.let {
                                    db.messageDao().updateBookmarked(obvSyncAtom.booleanValue, message.id)
                                }
                            }
                        }
                    }
                    ObvSyncAtom.TYPE_ARCHIVED_DISCUSSIONS_CHANGE -> {
                        obvSyncAtom.discussionIdentifiers.forEach { discussionIdentifier ->
                            val discussion = when (discussionIdentifier.type) {
                                DiscussionIdentifier.CONTACT -> {
                                    db.discussionDao().getByContactWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                DiscussionIdentifier.GROUP_V1 -> {
                                    db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                DiscussionIdentifier.GROUP_V2 -> {
                                    db.discussionDao().getByGroupIdentifierWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                else -> null
                            }
                            discussion?.let {
                                // never archive a pre-discussion
                                if (it.status != Discussion.STATUS_PRE_DISCUSSION) {
                                    db.discussionDao()
                                        .updateArchived(obvSyncAtom.booleanValue, it.id)
                                    AppSingleton.getEngine().profileBackupNeeded(discussion.bytesOwnedIdentity)
                                }
                            }
                        }
                    }
                    ObvSyncAtom.TYPE_DISCUSSIONS_MUTE_CHANGE -> {
                        obvSyncAtom.discussionIdentifiers.forEach { discussionIdentifier ->
                            val discussion = when (discussionIdentifier.type) {
                                DiscussionIdentifier.CONTACT -> {
                                    db.discussionDao().getByContactWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                DiscussionIdentifier.GROUP_V1 -> {
                                    db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                DiscussionIdentifier.GROUP_V2 -> {
                                    db.discussionDao().getByGroupIdentifierWithAnyStatus(
                                        bytesOwnedIdentity,
                                        discussionIdentifier.bytesDiscussionIdentifier
                                    )
                                }

                                else -> null
                            }

                            discussion?.let {
                                db.discussionCustomizationDao()[discussion.id]?.let { discussionCustomization ->
                                    db.discussionCustomizationDao()
                                        .update(discussionCustomization.apply {
                                            if (obvSyncAtom.muteNotification.muted) {
                                                this.prefMuteNotifications = true
                                                this.prefMuteNotificationsTimestamp =
                                                    obvSyncAtom.muteNotification.muteTimestamp
                                                this.prefMuteNotificationsExceptMentioned =
                                                    obvSyncAtom.muteNotification.exceptMentioned
                                            } else {
                                                // when un-muting do not overwrite previous value of prefMuteNotificationsExceptMentioned
                                                this.prefMuteNotifications = false
                                            }
                                        })
                                } ifNull {
                                    db.discussionCustomizationDao()
                                        .insert(DiscussionCustomization(discussion.id).apply {
                                            if (obvSyncAtom.muteNotification.muted) {
                                                this.prefMuteNotifications = true
                                                this.prefMuteNotificationsTimestamp =
                                                    obvSyncAtom.muteNotification.muteTimestamp
                                                this.prefMuteNotificationsExceptMentioned =
                                                    obvSyncAtom.muteNotification.exceptMentioned
                                            } else {
                                                // when un-muting do not overwrite previous value of prefMuteNotificationsExceptMentioned
                                                this.prefMuteNotifications = false
                                            }
                                        })
                                }

                                AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
                            }
                        }
                    }

                    ObvSyncAtom.TYPE_SETTING_UNARCHIVE_ON_NOTIFICATION -> {
                        SettingsActivity.setUnarchiveDiscussionOnNotification(obvSyncAtom.booleanValue)
                    }
                    else -> {
                        throw Exception("Unknown App sync atom type")
                    }
                }
                AppSingleton.getEngine().deletePersistedDialog(dialogUuid)
            } catch (e: Exception) {
                Logger.e("An error occurred while applying a syncItem. Aborting transaction")
                e.printStackTrace()
                throw RuntimeException()
            }
        }
    }
}

fun DiscussionCustomization.toMuteNotification(): MuteNotification {
    return MuteNotification(
        prefMuteNotifications,
        prefMuteNotificationsTimestamp,
        prefMuteNotificationsExceptMentioned
    )
}

fun Discussion.propagateMuteSettings(discussionCustomization: DiscussionCustomization) {
    try {
        val muteNotification = discussionCustomization.toMuteNotification()
        val discussionIdentifiers = DiscussionIdentifier(
                when (discussionType) {
                    Discussion.TYPE_CONTACT -> ObvSyncAtom.DiscussionIdentifier.CONTACT
                    Discussion.TYPE_GROUP -> ObvSyncAtom.DiscussionIdentifier.GROUP_V1
                    Discussion.TYPE_GROUP_V2 -> ObvSyncAtom.DiscussionIdentifier.GROUP_V2
                    else -> ObvSyncAtom.DiscussionIdentifier.CONTACT // should never happen
                },
                bytesDiscussionIdentifier
            )

        AppSingleton.getEngine()
            .propagateAppSyncAtomToOtherDevicesIfNeeded(
                bytesOwnedIdentity,
                ObvSyncAtom.createDiscussionsMuteChange(
                    listOf(discussionIdentifiers),
                    muteNotification
                )
            )
    } catch (ex: Exception) {
        Logger.w("Failed to propagate mute notifications setting change to other devices")
        Logger.x(ex)
    }
}

fun List<Discussion>.propagateMuteSettings(muted: Boolean, muteTimestamp: Long?, exceptMentioned: Boolean) {
    try {
        val muteNotification = MuteNotification(muted, muteTimestamp, exceptMentioned)
        val map = mutableMapOf<BytesKey, MutableList<DiscussionIdentifier>>()
        forEach { discussion ->
            map.getOrPut(BytesKey(discussion.bytesOwnedIdentity), { mutableListOf() })
                .add(
                    DiscussionIdentifier(
                        when (discussion.discussionType) {
                            Discussion.TYPE_CONTACT -> ObvSyncAtom.DiscussionIdentifier.CONTACT
                            Discussion.TYPE_GROUP -> ObvSyncAtom.DiscussionIdentifier.GROUP_V1
                            Discussion.TYPE_GROUP_V2 -> ObvSyncAtom.DiscussionIdentifier.GROUP_V2
                            else -> ObvSyncAtom.DiscussionIdentifier.CONTACT // should never happen
                        },
                        discussion.bytesDiscussionIdentifier
                    )
                )
        }

        map.forEach { (key, value) ->
            AppSingleton.getEngine()
                .propagateAppSyncAtomToOtherDevicesIfNeeded(
                    key.bytes,
                    ObvSyncAtom.createDiscussionsMuteChange(
                        value,
                        muteNotification
                    )
                )
        }
    } catch (ex: Exception) {
        Logger.w("Failed to propagate mute notifications setting change to other devices")
        Logger.x(ex)
    }
}