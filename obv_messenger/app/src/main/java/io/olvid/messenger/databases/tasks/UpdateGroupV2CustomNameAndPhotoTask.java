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

package io.olvid.messenger.databases.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group2;

public class UpdateGroupV2CustomNameAndPhotoTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesGroupIdentifier;
    private final String customName;
    private final String absoluteCustomPhotoUrl;
    private final String personalNote;
    private final boolean propagated;

    public UpdateGroupV2CustomNameAndPhotoTask(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, String customName, String absoluteCustomPhotoUrl, String personalNote, boolean propagated) {
        this.bytesGroupIdentifier = bytesGroupIdentifier;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.customName = customName;
        this.absoluteCustomPhotoUrl = absoluteCustomPhotoUrl;
        this.personalNote = personalNote;
        this.propagated = propagated;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Group2 group = db.group2Dao().get(bytesOwnedIdentity, bytesGroupIdentifier);

        if (group != null) {
            boolean changed = false;
            if (!Objects.equals(group.customName, customName)) {
                changed = true;
                group.customName = customName;
                db.group2Dao().updateCustomName(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.customName);

                if (!propagated) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createGroupV2NicknameChange(group.bytesGroupIdentifier, customName));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate group nickname change to other devices");
                        e.printStackTrace();
                    }
                }
            }

            if (!Objects.equals(App.absolutePathFromRelative(group.customPhotoUrl), absoluteCustomPhotoUrl)) {
                changed = true;

                // the photo changed --> delete the old photo if there is one
                if (group.customPhotoUrl != null) {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        new File(App.absolutePathFromRelative(group.customPhotoUrl)).delete();
                    } catch (Exception e) {
                        Logger.d("Failed to delete old group custom photoUrl " + group.customPhotoUrl);
                    }
                }

                if (absoluteCustomPhotoUrl == null || "".equals(absoluteCustomPhotoUrl)) {
                    // custom photo was reset or removed
                    group.customPhotoUrl = absoluteCustomPhotoUrl;
                } else {
                    // we have a new custom photo --> move it to the right place
                    try {
                        int i = 0;
                        String relativeOutputPath;
                        do {
                            relativeOutputPath = AppSingleton.CUSTOM_PHOTOS_DIRECTORY + File.separator + Logger.getUuidString(UUID.randomUUID());
                            i++;
                        } while (i < 10 && new File(App.absolutePathFromRelative(relativeOutputPath)).exists());

                        // move or copy file
                        File oldFile = new File(absoluteCustomPhotoUrl);
                        File newFile = new File(App.absolutePathFromRelative(relativeOutputPath));
                        if (!oldFile.renameTo(newFile)) {
                            // rename failed --> maybe on 2 different partitions
                            // fallback to a copy.
                            try (FileInputStream fileInputStream = new FileInputStream(oldFile); FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                                byte[] buffer = new byte[262_144];
                                int c;
                                while ((c = fileInputStream.read(buffer)) != -1) {
                                    fileOutputStream.write(buffer, 0, c);
                                }
                            }

                            //noinspection ResultOfMethodCallIgnored
                            oldFile.delete();
                        }
                        group.customPhotoUrl = relativeOutputPath;
                    } catch (Exception e) {
                        e.printStackTrace();
                        group.customPhotoUrl = null;
                    }
                }
                db.group2Dao().updateCustomPhotoUrl(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.customPhotoUrl);
            }

            if (!Objects.equals(group.personalNote, personalNote)) {
                group.personalNote = personalNote;
                db.group2Dao().updatePersonalNote(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.personalNote);

                if (!propagated) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createGroupV2PersonalNoteChange(group.bytesGroupIdentifier, personalNote));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate group nickname change to other devices");
                        e.printStackTrace();
                    }
                }
            }

            if (changed) {
                // rename the corresponding one-to-one discussion
                Discussion discussion = db.discussionDao().getByGroupIdentifier(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
                if (discussion != null) {
                    discussion.title = group.getCustomName();
                    discussion.photoUrl = group.getCustomPhotoUrl();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                    ShortcutActivity.updateShortcut(discussion);
                }
            }
        }
    }
}
