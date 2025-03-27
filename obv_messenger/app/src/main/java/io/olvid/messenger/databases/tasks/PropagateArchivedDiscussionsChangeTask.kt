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

import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.entity.Discussion


class PropagateArchivedDiscussionsChangeTask(
    private val bytesOwnedIdentity: ByteArray,
    private val discussions: List<Discussion>,
    private val archived: Boolean
) : Runnable {
    override fun run() {
        try {
            val discussionIdentifiers =
                discussions
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
            AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(
                bytesOwnedIdentity,
                ObvSyncAtom.createArchivedDiscussionsChange(discussionIdentifiers, archived)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}