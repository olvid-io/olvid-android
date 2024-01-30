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

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Objects;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;

public class UpdateGroupV2PhotoFromEngineTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesGroupIdentifier;

    public UpdateGroupV2PhotoFromEngineTask(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesGroupIdentifier = bytesGroupIdentifier;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Group2 group = db.group2Dao().get(bytesOwnedIdentity, bytesGroupIdentifier);

        ObvGroupV2.ObvGroupV2DetailsAndPhotos detailsAndPhotos = AppSingleton.getEngine().getGroupV2DetailsAndPhotos(bytesOwnedIdentity, bytesGroupIdentifier);
        if (group != null && detailsAndPhotos != null) {
            Discussion discussion = db.discussionDao().getByGroupIdentifier(group.bytesOwnedIdentity, group.bytesGroupIdentifier);
            if (detailsCanBeAutoTrusted(detailsAndPhotos)) {
                try {
                    AppSingleton.getEngine().trustGroupV2PublishedDetails(bytesOwnedIdentity, bytesGroupIdentifier);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if ((group.newPublishedDetails == Group2.PUBLISHED_DETAILS_NOTHING_NEW) && userShouldBeNotifiedOfNewPublishedDetails(detailsAndPhotos)) {
                if (discussion != null) {
                    // group indicates there is nothing new, still, after the photo download we realize that he should be notified --> notify him
                    Message newDetailsMessage = Message.createNewPublishedDetailsMessage(db, discussion.id, discussion.bytesOwnedIdentity);
                    db.messageDao().insert(newDetailsMessage);
                    if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                    }
                }

                group.newPublishedDetails = Group2.PUBLISHED_DETAILS_NEW_UNSEEN;
                db.group2Dao().updateNewPublishedDetails(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.newPublishedDetails);
            }
            
            if (!Objects.equals(group.photoUrl, detailsAndPhotos.photoUrl)) {
                group.photoUrl = detailsAndPhotos.photoUrl;
                db.group2Dao().updatePhotoUrl(group.bytesOwnedIdentity, group.bytesGroupIdentifier, group.photoUrl);

                // update the corresponding group discussion
                if (discussion != null) {
                    discussion.title = group.getCustomName();
                    discussion.photoUrl = group.getCustomPhotoUrl();
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, discussion.title, discussion.photoUrl);

                    ShortcutActivity.updateShortcut(discussion);
                }
            }
        }
    }

    public static boolean detailsCanBeAutoTrusted(@NonNull ObvGroupV2.ObvGroupV2DetailsAndPhotos detailsAndPhotos) {
        if (detailsAndPhotos.serializedPublishedDetails != null) {
            try {
                JsonGroupDetails publishedDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedPublishedDetails, JsonGroupDetails.class);
                JsonGroupDetails trustedDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);

                if (Objects.equals(publishedDetails, trustedDetails)) {
                    // same details -> compare the photoUrl
                    if (detailsAndPhotos.photoUrl == null) {
                        // always auto-trust the new photo if there was no previous photo
                        return true;
                    } else if (detailsAndPhotos.photoUrl.length() != 0) {
                        // there was a photo, and we download it
                        if (Objects.equals(detailsAndPhotos.photoUrl, detailsAndPhotos.publishedPhotoUrl)) {
                            // same photo --> trust
                            return true;
                        } else if (detailsAndPhotos.publishedPhotoUrl != null && detailsAndPhotos.publishedPhotoUrl.length() > 0){
                            // both photoUrl and publishedPhotoUrl are non null but have different values --> compare the file contents
                            File trustedPhotoFile = new File(App.absolutePathFromRelative(detailsAndPhotos.photoUrl));
                            File publishedPhotoFile = new File(App.absolutePathFromRelative(detailsAndPhotos.publishedPhotoUrl));

                            ByteArrayOutputStream trustedBaos = new ByteArrayOutputStream();
                            try (FileInputStream fis = new FileInputStream(trustedPhotoFile)) {
                                byte[] buffer = new byte[65_536];
                                int c;
                                while ((c = fis.read(buffer)) != -1) {
                                    trustedBaos.write(buffer, 0, c);
                                }
                            }
                            ByteArrayOutputStream publishedBaos = new ByteArrayOutputStream();
                            try (FileInputStream fis = new FileInputStream(publishedPhotoFile)) {
                                byte[] buffer = new byte[65_536];
                                int c;
                                while ((c = fis.read(buffer)) != -1) {
                                    publishedBaos.write(buffer, 0, c);
                                }
                            }

                            if (Arrays.equals(trustedBaos.toByteArray(), publishedBaos.toByteArray())) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    // this method is used to determine whether we should show to the user that there are some new details to trust. Typically, if details are the same but the photo is not downloaded yet, we do not know if details will be auto-trusted, so we return false
    public static boolean userShouldBeNotifiedOfNewPublishedDetails(@NonNull ObvGroupV2.ObvGroupV2DetailsAndPhotos detailsAndPhotos) {
        if (detailsAndPhotos.serializedPublishedDetails != null) {
            try {
                JsonGroupDetails publishedDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedPublishedDetails, JsonGroupDetails.class);
                JsonGroupDetails trustedDetails = AppSingleton.getJsonObjectMapper().readValue(detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);

                // if details are different, always notify
                if (!Objects.equals(publishedDetails, trustedDetails)) {
                    return true;
                }

                // if there was no photo (never notify as it will be auto trusted)
                if (detailsAndPhotos.photoUrl == null) {
                    return false;
                }

                // photo was removed, notify if old photo was downloaded
                if (detailsAndPhotos.publishedPhotoUrl == null) {
                    return detailsAndPhotos.photoUrl.length() > 0;
                }

                // new photo not downloaded yet, do not notify (too early to decide)
                if (detailsAndPhotos.publishedPhotoUrl.length() == 0) {
                    return false;
                }

                // new photo was downloaded, but not the old one --> notify
                if (detailsAndPhotos.photoUrl.length() == 0) {
                    return true;
                }

                /////////
                // both photos are downloaded --> compare them to decide if they are the same
                File trustedPhotoFile = new File(App.absolutePathFromRelative(detailsAndPhotos.photoUrl));
                File publishedPhotoFile = new File(App.absolutePathFromRelative(detailsAndPhotos.publishedPhotoUrl));

                ByteArrayOutputStream trustedBaos = new ByteArrayOutputStream();
                try (FileInputStream fis = new FileInputStream(trustedPhotoFile)) {
                    byte[] buffer = new byte[65_536];
                    int c;
                    while ((c = fis.read(buffer)) != -1) {
                        trustedBaos.write(buffer, 0, c);
                    }
                }
                ByteArrayOutputStream publishedBaos = new ByteArrayOutputStream();
                try (FileInputStream fis = new FileInputStream(publishedPhotoFile)) {
                    byte[] buffer = new byte[65_536];
                    int c;
                    while ((c = fis.read(buffer)) != -1) {
                        publishedBaos.write(buffer, 0, c);
                    }
                }

                return !Arrays.equals(trustedBaos.toByteArray(), publishedBaos.toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
