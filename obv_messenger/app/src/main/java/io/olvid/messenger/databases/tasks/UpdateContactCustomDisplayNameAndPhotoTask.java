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
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;

public class UpdateContactCustomDisplayNameAndPhotoTask implements Runnable {
    private final byte[] bytesContactIdentity;
    private final byte[] bytesOwnedIdentity;
    private final String customDisplayName;
    private final String absoluteCustomPhotoUrl;
    private final Integer customNameHue;
    private final String personalNote;
    private final boolean propagated;

    public UpdateContactCustomDisplayNameAndPhotoTask(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, String customDisplayName, String absoluteCustomPhotoUrl, Integer customNameHue, String personalNote, boolean propagated) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.customDisplayName = customDisplayName;
        this.absoluteCustomPhotoUrl = absoluteCustomPhotoUrl;
        this.customNameHue = customNameHue;
        this.personalNote = personalNote;
        this.propagated = propagated;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null) {
            boolean changed = false;
            if (!Objects.equals(contact.customNameHue, customNameHue)) {
                contact.customNameHue = customNameHue;
                db.contactDao().updateCustomNameHue(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.customNameHue);
                AppSingleton.updateCachedCustomHue(contact.bytesContactIdentity, contact.customNameHue);

                if (!propagated) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createContactCustomHueChange(contact.bytesContactIdentity, customNameHue));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate contact custom hue change to other devices");
                        e.printStackTrace();
                    }
                }
            }

            if (!Objects.equals(contact.personalNote, personalNote)) {
                contact.personalNote = personalNote;
                db.contactDao().updatePersonalNote(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.personalNote);

                if (!propagated) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createContactPersonalNoteChange(contact.bytesContactIdentity, personalNote));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate contact personal note change to other devices");
                        e.printStackTrace();
                    }
                }
            }

            if (!Objects.equals(contact.customDisplayName, customDisplayName)) {
                changed = true;
                contact.setCustomDisplayName(customDisplayName);
                db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.firstName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);
                if (Arrays.equals(AppSingleton.getBytesCurrentIdentity(), contact.bytesOwnedIdentity)) {
                    AppSingleton.updateCachedCustomDisplayName(contact.bytesContactIdentity, contact.getCustomDisplayName(), contact.getFirstNameOrCustom());
                }

                if (!propagated) {
                    try {
                        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(bytesOwnedIdentity, ObvSyncAtom.createContactNicknameChange(contact.bytesContactIdentity, customDisplayName));
                    } catch (Exception e) {
                        Logger.w("Failed to propagate contact nickname change to other devices");
                        e.printStackTrace();
                    }
                }

                new UpdateAllGroupMembersNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity).run();
            }

            if (!Objects.equals(App.absolutePathFromRelative(contact.customPhotoUrl), absoluteCustomPhotoUrl)) {
                changed = true;

                // the photo changed --> delete the old photo if there is one
                if (contact.customPhotoUrl != null) {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        new File(App.absolutePathFromRelative(contact.customPhotoUrl)).delete();
                    } catch (Exception e) {
                        Logger.d("Failed to delete old group custom photoUrl " + contact.customPhotoUrl);
                    }
                }

                if (absoluteCustomPhotoUrl == null || "".equals(absoluteCustomPhotoUrl)) {
                    // custom photo was reset or removed
                    contact.customPhotoUrl = absoluteCustomPhotoUrl;
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
                        contact.customPhotoUrl = relativeOutputPath;
                    } catch (Exception e) {
                        e.printStackTrace();
                        contact.customPhotoUrl = null;
                    }
                }
                db.contactDao().updateCustomPhotoUrl(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.customPhotoUrl);
                AppSingleton.updateCachedPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
            }


            if (changed) {
                // rename the corresponding one-to-one discussion
                Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                if (discussion != null) {
                    discussion.title = contact.getCustomDisplayName();
                    discussion.photoUrl = contact.getCustomPhotoUrl();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                    ShortcutActivity.updateShortcut(discussion);
                }
            }
        }
    }
}
