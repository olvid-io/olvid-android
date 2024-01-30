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
package io.olvid.messenger.databases.tasks

import io.olvid.engine.Logger
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.engine.engine.types.sync.ObvSyncAtom.DiscussionIdentifier
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
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
                        }
                    }
                    ObvSyncAtom.TYPE_PINNED_DISCUSSIONS_CHANGE -> {
                        val pinnedDiscussions = db.discussionDao().getAllPinned(bytesOwnedIdentity)
                        val pinnedDiscussionsMap : HashMap<Long, Discussion> = HashMap()
                        pinnedDiscussions.forEach { discussion ->
                            pinnedDiscussionsMap[discussion.id] = discussion
                        }

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
                                    // if the received discussion was not in the pinned map, pin it
                                    pinnedDiscussionsMap.remove(it.id) ifNull {
                                        db.discussionDao().updatePinned(it.id, true)
                                    }
                                }
                            }
                        }

                        // unpin any discussion that is not in the received list
                        pinnedDiscussionsMap.values.forEach {
                            db.discussionDao().updatePinned(it.id, false)
                        }
                    }
                    ObvSyncAtom.TYPE_SETTING_DEFAULT_SEND_READ_RECEIPTS -> {
                        SettingsActivity.setDefaultSendReadReceipt(obvSyncAtom.booleanValue)
                    }
                    ObvSyncAtom.TYPE_SETTING_AUTO_JOIN_GROUPS -> {
                        SettingsActivity.setAutoJoinGroups(SettingsActivity.getAutoJoinGroupsFromString(obvSyncAtom.stringValue))
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