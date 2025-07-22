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

package io.olvid.messenger.group

import androidx.annotation.StringRes
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.messenger.R
import io.olvid.messenger.group.GroupTypeModel.GroupType.CUSTOM
import io.olvid.messenger.group.GroupTypeModel.GroupType.PRIVATE
import io.olvid.messenger.group.GroupTypeModel.GroupType.READ_ONLY
import io.olvid.messenger.group.GroupTypeModel.GroupType.SIMPLE
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting.NOBODY

open class GroupTypeModel(
    val type: GroupType,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    var readOnlySetting: Boolean = false,
    var remoteDeleteSetting: RemoteDeleteSetting = NOBODY
) {
    enum class GroupType {
        SIMPLE,
        PRIVATE,
        READ_ONLY,
        CUSTOM
    }

    enum class RemoteDeleteSetting(val value: String) {
        NOBODY("nobody"),
        ADMINS("admins"),
        EVERYONE("everyone");

        override fun toString(): String {
            return value
        }

        fun getJsonGroupTypeString() : String {
            return when(this) {
                NOBODY -> JsonGroupType.REMOTE_DELETE_NOBODY
                ADMINS -> JsonGroupType.REMOTE_DELETE_ADMINS
                EVERYONE -> JsonGroupType.REMOTE_DELETE_EVERYONE
            }
        }
    }

    fun areNonAdminsReadOnly(): Boolean {
        return type == READ_ONLY || (type == CUSTOM && readOnlySetting)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GroupTypeModel) {
            return false
        }
        return (type == other.type) &&
                ((type != CUSTOM)
                        || (readOnlySetting == other.readOnlySetting && remoteDeleteSetting == other.remoteDeleteSetting))
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

object SimpleGroup : GroupTypeModel(
    type = SIMPLE,
    title = R.string.label_group_simple_title,
    subtitle = R.string.label_group_simple_subtitle,
)

object PrivateGroup : GroupTypeModel(
    type = PRIVATE,
    title = R.string.label_group_private_title,
    subtitle = R.string.label_group_private_subtitle
)

object ReadOnlyGroup : GroupTypeModel(
    type = READ_ONLY,
    title = R.string.label_group_read_only_title,
    subtitle = R.string.label_group_read_only_subtitle
)

class CustomGroup(
    readOnlySetting: Boolean = false,
    remoteDeleteSetting: RemoteDeleteSetting = NOBODY
) : GroupTypeModel(
    type = CUSTOM,
    title = R.string.label_group_custom_title,
    subtitle = R.string.label_group_custom_subtitle,
    readOnlySetting = readOnlySetting,
    remoteDeleteSetting = remoteDeleteSetting
)


fun GroupTypeModel.toJsonGroupType() : JsonGroupType {
    return when(type) {
        SIMPLE -> JsonGroupType.createSimple()
        PRIVATE -> JsonGroupType.createPrivate()
        READ_ONLY -> JsonGroupType.createReadOnly()
        CUSTOM -> JsonGroupType.createCustom(readOnlySetting, remoteDeleteSetting.getJsonGroupTypeString())
    }
}

fun GroupTypeModel.clone() : GroupTypeModel {
    return when(type) {
        CUSTOM -> CustomGroup(
            readOnlySetting = readOnlySetting,
            remoteDeleteSetting = remoteDeleteSetting
        )
        else -> this
    }
}
