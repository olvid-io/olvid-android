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

package io.olvid.messenger.databases.entity.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String CUSTOM_NAME = "custom_name";
    public static final String CUSTOM_HUE = "custom_hue";
    public static final String PERSONAL_NOTE = "personal_note";
    public static final String DISCUSSION_CUSTOMIZATION = "discussion_customization";

    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(CUSTOM_NAME, CUSTOM_HUE, PERSONAL_NOTE, DISCUSSION_CUSTOMIZATION));

    public String custom_name;
    public Integer custom_hue;
    public String personal_note;
    public DiscussionCustomizationSyncSnapshot discussion_customization;
    public HashSet<String> domain;

    public static ContactSyncSnapshot of(AppDatabase db, Contact contact) {
        ContactSyncSnapshot contactSyncSnapshot = new ContactSyncSnapshot();
        contactSyncSnapshot.custom_name = contact.customDisplayName;
        contactSyncSnapshot.custom_hue = contact.customNameHue;
        contactSyncSnapshot.personal_note = contact.personalNote;

        // only store discussion customizations for one-to-one contacts. Locked discussions do not need to be synchronized
        if (contact.oneToOne) {
            Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            if (discussion != null) {
                DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                if (discussionCustomization != null) {
                    contactSyncSnapshot.discussion_customization = DiscussionCustomizationSyncSnapshot.of(db, discussionCustomization);
                }
            }
        }

        contactSyncSnapshot.domain = DEFAULT_DOMAIN;
        return contactSyncSnapshot;
    }

    public void restore(AppDatabase db, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null) {
            Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            if (domain.contains(CUSTOM_NAME) && custom_name != null) {
                contact.setCustomDisplayName(custom_name);
                db.contactDao().updateAllDisplayNames(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.identityDetails, contact.displayName, contact.customDisplayName, contact.sortDisplayName, contact.fullSearchDisplayName);
                if (discussion != null) {
                    db.discussionDao().updateTitleAndPhotoUrl(discussion.id, contact.getCustomDisplayName(), discussion.photoUrl);
                }
            }
            if (domain.contains(CUSTOM_HUE) && custom_hue != null) {
                db.contactDao().updateCustomNameHue(bytesOwnedIdentity, bytesContactIdentity, custom_hue);
            }
            if (domain.contains(PERSONAL_NOTE) && personal_note != null) {
                db.contactDao().updatePersonalNote(bytesOwnedIdentity, bytesContactIdentity, personal_note);
            }
            if (discussion != null) {
                if (domain.contains(DISCUSSION_CUSTOMIZATION) && discussion_customization != null) {
                    discussion_customization.restore(db, discussion.id);
                } else {
                    // in case a default ephemeral setting was applied, reset it
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussion.id);
                    if (discussionCustomization != null) {
                        discussionCustomization.sharedSettingsVersion = null;
                        discussionCustomization.settingVisibilityDuration = null;
                        discussionCustomization.settingExistenceDuration = null;
                        discussionCustomization.settingReadOnce = false;
                        db.discussionCustomizationDao().update(discussionCustomization);
                    }
                }
            }
        }
    }

    @Override
    @JsonIgnore
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof ContactSyncSnapshot)) {
            return false;
        }

        ContactSyncSnapshot other = (ContactSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case CUSTOM_NAME: {
                    if (!Objects.equals(custom_name, other.custom_name)) {
                        return false;
                    }
                    break;
                }
                case CUSTOM_HUE: {
                    if (!Objects.equals(custom_hue, other.custom_hue)) {
                        return false;
                    }
                    break;
                }
                case PERSONAL_NOTE: {
                    if (!Objects.equals(personal_note, other.personal_note)) {
                        return false;
                    }
                    break;
                }
                case DISCUSSION_CUSTOMIZATION: {
                    if ((discussion_customization == null && other.discussion_customization != null)
                            || (discussion_customization != null && !discussion_customization.areContentsTheSame(other.discussion_customization))) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    @JsonIgnore
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }
}
