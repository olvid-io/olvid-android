/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.databases.tasks;

import android.util.Pair;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.Reaction;

public class UpdateReactionsTask implements Runnable {
    final long messageId;
    @Nullable
    final String emoji; // emoji is null to remove a previous reaction
    final byte[] bytesIdentity;
    private final long reactionTimestamp;

    // bytesIdentity is null if my own reaction
    public UpdateReactionsTask(long messageId, @Nullable String emoji, byte[] bytesIdentity, long reactionTimestamp) {
        this.messageId = messageId;
        this.emoji = emoji;
        this.bytesIdentity = bytesIdentity;
        this.reactionTimestamp = reactionTimestamp;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(messageId);
        List<Reaction> reactions = db.reactionDao().getAllForMessage(messageId);
        Reaction updatedReaction = null; // will be non-null if this is a reaction update. Will remain null for new reactions

        if (emoji != null && (emoji.length() == 0 || emoji.contains(":") || emoji.contains("|"))) {
            Logger.e("UpdateReactionsTask: Invalid emoji (length 0 or contained ':' or '|')");
            return;
        }

        if (message == null || reactions == null) {
            Logger.e("UpdateReactionsTask: Unable to find message or reactions in database");
            return;
        }

        // if reaction is mine, send notification to other contacts
        if (bytesIdentity == null) {
            boolean success = Message.postReactionMessage(message, emoji);
            if (!success) {
                return;
            }
        }

        // fill reactions hashmap
        HashMap<String, Pair<Integer, Long>> reactionsMap = new HashMap<>();
        String myReaction = null;
        for (Reaction reaction : reactions) {
            if (Arrays.equals(reaction.bytesIdentity, bytesIdentity)) {
                // this is an update to a user (or my own) reaction
                if (reaction.timestamp > reactionTimestamp) {
                    // we already have a newer reaction --> abort the update
                    return;
                }
                reaction.emoji = emoji;
                reaction.timestamp = reactionTimestamp;
                updatedReaction = reaction; // this is just a reference to the updated reaction
            }

            if (reaction.bytesIdentity == null) {
                myReaction = reaction.emoji;
            }
            if (reaction.emoji != null) {
                Pair<Integer, Long> old = reactionsMap.get(reaction.emoji);
                if (old == null) {
                    reactionsMap.put(reaction.emoji, new Pair<>(1, reaction.timestamp));
                } else {
                    reactionsMap.put(reaction.emoji, new Pair<>(old.first + 1, Math.min(old.second, reaction.timestamp)));
                }
            }
        }

        // add the new reaction to the map, unless this is an update (in which case it is already in the map)
        if (updatedReaction == null && emoji != null) {
            if (bytesIdentity == null) {
                myReaction = emoji;
            }
            Pair<Integer, Long> old = reactionsMap.get(emoji);
            if (old == null) {
                reactionsMap.put(emoji, new Pair<>(1, reactionTimestamp));
            } else {
                reactionsMap.put(emoji, new Pair<>(old.first + 1, Math.min(old.second, reactionTimestamp)));
            }
        }

        // sort emojis by count
        List<Map.Entry<String, Pair<Integer, Long>>> entryList = new LinkedList<>(reactionsMap.entrySet());
        Comparator<Map.Entry<String, Pair<Integer, Long>>> comparator = (o1, o2) -> {
            if (!Objects.equals(o1.getValue().first, o2.getValue().first)) {
                return o1.getValue().first - o2.getValue().first;
            } else {
                return (int) (o1.getValue().second - o2.getValue().second);
            }
        };
        Collections.sort(entryList, comparator);

        // serialize data for Message.reactions from hash map
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, Pair<Integer, Long>> entry : entryList) {
            if (myReaction != null && entry.getKey().equals(myReaction)) {
                stringBuilder.append("|");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append(":");
            stringBuilder.append(entry.getValue().first);
            stringBuilder.append(":");
        }
        String messageReactionsString = stringBuilder.toString();

        // insert/update/delete reaction in reaction table
        if (updatedReaction == null) {
            db.reactionDao().insert(new Reaction(messageId, bytesIdentity, emoji, reactionTimestamp));
        } else {
            db.reactionDao().update(updatedReaction);
        }

        // update Message.reaction
        db.messageDao().updateReactions(message.id, messageReactionsString);
    }
}
