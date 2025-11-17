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
package io.olvid.messenger.databases.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import io.olvid.engine.datatypes.containers.GroupV2
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.sortContactsByLastName
import io.olvid.messenger.settings.SettingsActivity.Companion.uppercaseLastName

@Entity(
    tableName = Group2PendingMember.TABLE_NAME,
    primaryKeys = [Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER, Group2PendingMember.BYTES_CONTACT_IDENTITY],
    foreignKeys = [
        ForeignKey(
            entity = Group2::class,
            parentColumns = [Group2.BYTES_OWNED_IDENTITY, Group2.BYTES_GROUP_IDENTIFIER],
            childColumns = [Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [Group2PendingMember.BYTES_OWNED_IDENTITY]),
        Index(value = [Group2PendingMember.BYTES_OWNED_IDENTITY, Group2PendingMember.BYTES_GROUP_IDENTIFIER])
    ]
)
data class Group2PendingMember(
    @JvmField @ColumnInfo(name = BYTES_OWNED_IDENTITY) val bytesOwnedIdentity: ByteArray,
    @JvmField @ColumnInfo(name = BYTES_GROUP_IDENTIFIER) val bytesGroupIdentifier: ByteArray,
    @JvmField @ColumnInfo(name = BYTES_CONTACT_IDENTITY) val bytesContactIdentity: ByteArray,

    @JvmField @ColumnInfo(name = DISPLAY_NAME) var displayName: String,
    @JvmField @ColumnInfo(name = FIRST_NAME) var firstName: String?,
    @JvmField @ColumnInfo(name = SORT_DISPLAY_NAME) var sortDisplayName: ByteArray,
    @JvmField @ColumnInfo(name = FULL_SEARCH_DISPLAY_NAME) var fullSearchDisplayName: String,
    @JvmField @ColumnInfo(name = IDENTITY_DETAILS) var identityDetails: String?,
    @JvmField @ColumnInfo(name = CREATION_TIMESTAMP) var creationTimestamp: Long,

    @JvmField @ColumnInfo(name = PERMISSION_ADMIN) var permissionAdmin: Boolean,
    @JvmField @ColumnInfo(name = PERMISSION_REMOTE_DELETE_ANYTHING) var permissionRemoteDeleteAnything: Boolean,
    @JvmField @ColumnInfo(name = PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES) var permissionEditOrRemoteDeleteOwnMessages: Boolean,
    @JvmField @ColumnInfo(name = PERMISSION_CHANGE_SETTINGS) var permissionChangeSettings: Boolean,
    @JvmField @ColumnInfo(name = PERMISSION_SEND_MESSAGE) var permissionSendMessage: Boolean
) {
    companion object {
        const val TABLE_NAME: String = "group2_pending_member_table"

        const val BYTES_OWNED_IDENTITY: String = "bytes_owned_identity"
        const val BYTES_GROUP_IDENTIFIER: String = "bytes_group_identifier"
        const val BYTES_CONTACT_IDENTITY: String = "bytes_contact_identity"

        const val DISPLAY_NAME: String = "display_name"
        const val FIRST_NAME: String = "first_name"
        const val SORT_DISPLAY_NAME: String = "sort_display_name"
        const val FULL_SEARCH_DISPLAY_NAME: String = "full_search_display_name"
        const val IDENTITY_DETAILS: String = "identity_details"
        const val CREATION_TIMESTAMP: String = "creation_timestamp"

        const val PERMISSION_ADMIN: String = "permission_admin"
        const val PERMISSION_REMOTE_DELETE_ANYTHING: String = "permission_remote_delete_anything"
        const val PERMISSION_EDIT_OR_REMOTE_DELETE_OWN_MESSAGES: String = "permission_edit_or_remote_delete_own_messages"
        const val PERMISSION_CHANGE_SETTINGS: String = "permission_change_settings"
        const val PERMISSION_SEND_MESSAGE: String = "permission_send_message"
    }


    @Ignore
    constructor(bytesOwnedIdentity: ByteArray, bytesGroupIdentifier: ByteArray, bytesContactIdentity: ByteArray, serializedIdentityDetails: String, permissions: HashSet<GroupV2.Permission?>, groupUpdateTimestamp: Long) : this(
        bytesOwnedIdentity = bytesOwnedIdentity,
        bytesGroupIdentifier = bytesGroupIdentifier,
        bytesContactIdentity = bytesContactIdentity,
        displayName = "",
        firstName = null,
        sortDisplayName = ByteArray(0),
        fullSearchDisplayName = "",
        identityDetails = null,
        creationTimestamp = groupUpdateTimestamp,
        permissionAdmin = permissions.contains(GroupV2.Permission.GROUP_ADMIN),
        permissionRemoteDeleteAnything = permissions.contains(GroupV2.Permission.REMOTE_DELETE_ANYTHING),
        permissionEditOrRemoteDeleteOwnMessages = permissions.contains(GroupV2.Permission.EDIT_OR_REMOTE_DELETE_OWN_MESSAGES),
        permissionChangeSettings = permissions.contains(GroupV2.Permission.CHANGE_SETTINGS),
        permissionSendMessage = permissions.contains(GroupV2.Permission.SEND_MESSAGE),
    ) {
        this.setIdentityDetailsAndDisplayName(serializedIdentityDetails)
    }

    @Throws(Exception::class)
    fun setIdentityDetailsAndDisplayName(serializedIdentityDetails: String) {
        var jsonIdentityDetails: JsonIdentityDetails? = null
        try {
            jsonIdentityDetails = AppSingleton.getJsonObjectMapper().readValue(serializedIdentityDetails, JsonIdentityDetails::class.java)
        } catch (_: Exception) { }
        if (jsonIdentityDetails == null) {
            this.displayName = ""
            this.sortDisplayName = ByteArray(0)
            this.fullSearchDisplayName = ""
            this.identityDetails = null
            this.firstName = null
        } else {
            this.displayName = jsonIdentityDetails.formatDisplayName(contactDisplayNameFormat, uppercaseLastName)
            this.sortDisplayName = ContactDisplayNameFormatChangedTask.computeSortDisplayName(jsonIdentityDetails, null, sortContactsByLastName)
            this.fullSearchDisplayName = StringUtils.unAccent(jsonIdentityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false))
            this.identityDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonIdentityDetails)
            this.firstName = jsonIdentityDetails.getFirstName()
        }
    }

    fun getNonNullFirstName(): String {
        return firstName ?: displayName
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Group2PendingMember) return false

        if (creationTimestamp != other.creationTimestamp) return false
        if (permissionAdmin != other.permissionAdmin) return false
        if (permissionRemoteDeleteAnything != other.permissionRemoteDeleteAnything) return false
        if (permissionEditOrRemoteDeleteOwnMessages != other.permissionEditOrRemoteDeleteOwnMessages) return false
        if (permissionChangeSettings != other.permissionChangeSettings) return false
        if (permissionSendMessage != other.permissionSendMessage) return false
        if (!bytesOwnedIdentity.contentEquals(other.bytesOwnedIdentity)) return false
        if (!bytesGroupIdentifier.contentEquals(other.bytesGroupIdentifier)) return false
        if (!bytesContactIdentity.contentEquals(other.bytesContactIdentity)) return false
        if (displayName != other.displayName) return false
        if (firstName != other.firstName) return false
        if (!sortDisplayName.contentEquals(other.sortDisplayName)) return false
        if (fullSearchDisplayName != other.fullSearchDisplayName) return false
        if (identityDetails != other.identityDetails) return false

        return true
    }

    override fun hashCode(): Int {
        var result = creationTimestamp.hashCode()
        result = 31 * result + permissionAdmin.hashCode()
        result = 31 * result + permissionRemoteDeleteAnything.hashCode()
        result = 31 * result + permissionEditOrRemoteDeleteOwnMessages.hashCode()
        result = 31 * result + permissionChangeSettings.hashCode()
        result = 31 * result + permissionSendMessage.hashCode()
        result = 31 * result + bytesOwnedIdentity.contentHashCode()
        result = 31 * result + bytesGroupIdentifier.contentHashCode()
        result = 31 * result + bytesContactIdentity.contentHashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + sortDisplayName.contentHashCode()
        result = 31 * result + fullSearchDisplayName.hashCode()
        result = 31 * result + (identityDetails?.hashCode() ?: 0)
        return result
    }
}
