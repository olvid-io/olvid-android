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

import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion


class PropagatePinnedDiscussionsChangeTask(private val bytesOwnedIdentity : ByteArray) : Runnable {
    override fun run() {
        try {
            val discussionIdentifiers =
                AppDatabase.getInstance().discussionDao().getAllPinned(bytesOwnedIdentity)
                    .map { discussion ->
                        ObvSyncAtom.DiscussionIdentifier(
                            when (discussion.discussionType) {
                                Discussion.TYPE_CONTACT -> ObvSyncAtom.DiscussionIdentifier.CONTACT
                                Discussion.TYPE_GROUP -> ObvSyncAtom.DiscussionIdentifier.GROUP_V1
                                Discussion.TYPE_GROUP_V2 -> ObvSyncAtom.DiscussionIdentifier.GROUP_V2
                                else -> ObvSyncAtom.DiscussionIdentifier.CONTACT // should never happen
                            },
                            discussion.bytesDiscussionIdentifier
                        )
                    }
            AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createPinnedDiscussionsChange(discussionIdentifiers, true))
            AppSingleton.getEngine().profileBackupNeeded(bytesOwnedIdentity)
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }
}