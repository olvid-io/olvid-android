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

package io.olvid.messenger.databases.tasks

import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.settings.SettingsActivity

/**
 * Toggles the default reaction on a message: applies the discussion's default reaction
 * (falling back to the app-wide default), replacing any other reaction of the user;
 * if the user's current reaction already is the default one, it is removed instead.
 */
class ToggleDefaultReactionTask(private val messageId: Long) : Runnable {
    override fun run() {
        val db = AppDatabase.getInstance()
        val message = db.messageDao().get(messageId) ?: return
        val defaultReaction = db.discussionCustomizationDao().get(message.discussionId)?.prefDefaultReaction
            ?: SettingsActivity.defaultReaction
        val emoji = if (db.reactionDao().getMyReactionForMessage(messageId)?.emoji == defaultReaction) {
            null
        } else {
            defaultReaction
        }
        UpdateReactionsTask(messageId, emoji, null, System.currentTimeMillis(), true).run()
    }
}
