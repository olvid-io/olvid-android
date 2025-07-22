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

package io.olvid.messenger.databases.tasks.new_message

import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.jsons.JsonOneToOneMessageIdentifier


fun getDiscussion(
    db: AppDatabase,
    bytesGroupUid: ByteArray?,
    bytesGroupOwner: ByteArray?,
    bytesGroupIdentifier: ByteArray?,
    oneToOneMessageIdentifier: JsonOneToOneMessageIdentifier?,
    messageSender: MessageSender,
    requiredPermission: GroupV2.Permission?
): Discussion? {
    // handle the special case of messages from another device differently: the message should also be accepted for locked/pre discussions
    if (messageSender.type == MessageSender.Type.OWNED_IDENTITY) {
        return if (bytesGroupIdentifier != null) {
            db.discussionDao().getByGroupIdentifierWithAnyStatus(
                messageSender.bytesOwnedIdentity,
                bytesGroupIdentifier
            )
        } else if (bytesGroupUid != null && bytesGroupOwner != null) {
            db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(
                messageSender.bytesOwnedIdentity,
                bytesGroupOwner + bytesGroupUid
            )
        } else {
            oneToOneMessageIdentifier?.getBytesContactIdentity(messageSender.bytesOwnedIdentity)
                ?.let {
                    db.discussionDao().getByContactWithAnyStatus(
                        messageSender.bytesOwnedIdentity,
                        it
                    )
                }
        }
    }

    if (bytesGroupUid == null && bytesGroupOwner == null && bytesGroupIdentifier == null) {
        if (requiredPermission == GroupV2.Permission.REMOTE_DELETE_ANYTHING) {
            return null
        }
        // we keep this test for now to maintain backward compatibility, but oneToOneMessageIdentifier should always be non-null now
        val bytesContactIdentity =
            if (oneToOneMessageIdentifier != null)
                oneToOneMessageIdentifier.getBytesContactIdentity(messageSender.bytesOwnedIdentity)
            else
                messageSender.senderIdentity

        return bytesContactIdentity?.let {
            db.discussionDao()
                .getByContact(messageSender.bytesOwnedIdentity, bytesContactIdentity)
        }
    } else if (bytesGroupIdentifier != null) {
        // check the send is indeed a member or pending member with appropriate send message permission
        val requiredPermissionFulfilled =
            db.group2MemberDao().get(
                messageSender.bytesOwnedIdentity,
                bytesGroupIdentifier,
                messageSender.senderIdentity
            )?.let { group2Member ->
                when (requiredPermission) {
                    GroupV2.Permission.GROUP_ADMIN -> group2Member.permissionAdmin
                    GroupV2.Permission.REMOTE_DELETE_ANYTHING -> group2Member.permissionRemoteDeleteAnything
                    GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES -> group2Member.permissionEditOrRemoteDeleteOwnMessages
                    GroupV2.Permission.CHANGE_SETTINGS -> group2Member.permissionChangeSettings
                    GroupV2.Permission.SEND_MESSAGE -> group2Member.permissionSendMessage
                    else -> true
                }
            }

                ?: db.group2PendingMemberDao().get(
                    messageSender.bytesOwnedIdentity,
                    bytesGroupIdentifier,
                    messageSender.senderIdentity
                )?.let { group2PendingMember ->
                    when (requiredPermission) {
                        GroupV2.Permission.GROUP_ADMIN -> group2PendingMember.permissionAdmin
                        GroupV2.Permission.REMOTE_DELETE_ANYTHING -> group2PendingMember.permissionRemoteDeleteAnything
                        GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES -> group2PendingMember.permissionEditOrRemoteDeleteOwnMessages
                        GroupV2.Permission.CHANGE_SETTINGS -> group2PendingMember.permissionChangeSettings
                        GroupV2.Permission.SEND_MESSAGE -> group2PendingMember.permissionSendMessage
                        else -> true
                    }
                }

                ?: false

        if (!requiredPermissionFulfilled) {
            Logger.i("Received a group V2 message for an unknown group, from someone not in the group, or from someone without proper permission --> IGNORING IT!")
            return null
        }

        return db.discussionDao()
            .getByGroupIdentifier(messageSender.bytesOwnedIdentity, bytesGroupIdentifier)
    } else {
        if (bytesGroupUid == null || bytesGroupOwner == null) {
            Logger.i("Received a message with one of groupOwner or groupUid null, IGNORING IT!")
            return null
        }
        if (requiredPermission == GroupV2.Permission.REMOTE_DELETE_ANYTHING) {
            return null
        }
        val bytesGroupOwnerAndUid = bytesGroupOwner + bytesGroupUid

        // check the send is indeed a member or pending member with appropriate send message permission
        db.contactGroupJoinDao().get(
            bytesGroupOwnerAndUid,
            messageSender.bytesOwnedIdentity,
            messageSender.senderIdentity
        )
            ?: db.pendingGroupMemberDao().get(
                messageSender.bytesOwnedIdentity,
                bytesGroupOwnerAndUid,
                messageSender.senderIdentity

            ) ?: run {
                Logger.i("Received a message for an unknown group, or from someone not in the group, IGNORING IT!")
                return null
            }

        // these permissions are only fulfilled for the group owner.
        // group.bytesGroupOwnerIdentity could be null (you are the group owner)
        //    --> in that case always reject as message from other owned devices are already handled at the beginning of this method
        if (requiredPermission == GroupV2.Permission.GROUP_ADMIN || requiredPermission == GroupV2.Permission.CHANGE_SETTINGS) {
            val group = db.groupDao()
                .get(messageSender.bytesOwnedIdentity, bytesGroupOwnerAndUid)

            if (group == null || !group.bytesGroupOwnerIdentity.contentEquals(messageSender.senderIdentity)) {
                return null
            }
        }

        return db.discussionDao()
            .getByGroupOwnerAndUid(messageSender.bytesOwnedIdentity, bytesGroupOwnerAndUid)
    }
}
