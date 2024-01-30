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

package io.olvid.messenger.databases.entity.sync;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvGroupOwnerAndUidKey;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.UpdateAllGroupMembersNames;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedIdentitySyncSnapshot implements ObvSyncSnapshotNode {
    public static final String CUSTOM_NAME = "custom_name";
    public static final String CONTACTS = "contacts";
    public static final String GROUPS = "groups";
    public static final String GROUPS2 = "groups2";
    public static final String PINNED = "pinned";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(CUSTOM_NAME, CONTACTS, GROUPS, GROUPS2, PINNED));

    public String custom_name;
    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, ContactSyncSnapshot> contacts;

    @JsonSerialize(keyUsing = ObvGroupOwnerAndUidKey.Serializer.class)
    @JsonDeserialize(keyUsing = ObvGroupOwnerAndUidKey.Deserializer.class)
    public HashMap<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groups;

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer.class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer.class)
    public HashMap<ObvBytesKey, GroupV2SyncSnapshot> groups2;

    // the following two fields are part of a single PINNED domain
    @JsonSerialize(contentUsing = ObvBytesKey.Serializer.class)
    @JsonDeserialize(contentUsing = ObvBytesKey.Deserializer.class)
    public List<ObvBytesKey> pinned_discussions; // a list en encoded ObvSyncAtom.DiscussionIdentifier
    public Boolean pinned_sorted;
    public HashSet<String> domain;

    @JsonIgnore
    private boolean getPinned_sorted() {
        return pinned_sorted != null && pinned_sorted;
    }

    public static OwnedIdentitySyncSnapshot of(@NonNull AppDatabase db, @NonNull OwnedIdentity ownedIdentity) {
        OwnedIdentitySyncSnapshot ownedIdentitySyncSnapshot = new OwnedIdentitySyncSnapshot();
        ownedIdentitySyncSnapshot.custom_name = ownedIdentity.customDisplayName;

        ownedIdentitySyncSnapshot.contacts = new HashMap<>();
        for (Contact contact : db.contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity)) {
            ownedIdentitySyncSnapshot.contacts.put(new ObvBytesKey(contact.bytesContactIdentity), ContactSyncSnapshot.of(db, contact));
        }

        ownedIdentitySyncSnapshot.groups = new HashMap<>();
        for (Group group : db.groupDao().getAllForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)) {
            ownedIdentitySyncSnapshot.groups.put(new ObvGroupOwnerAndUidKey(group.bytesGroupOwnerAndUid), GroupV1SyncSnapshot.of(db, group));
        }

        ownedIdentitySyncSnapshot.groups2 = new HashMap<>();
        for (Group2 group2 : db.group2Dao().getAllForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)) {
            ownedIdentitySyncSnapshot.groups2.put(new ObvBytesKey(group2.bytesGroupIdentifier), GroupV2SyncSnapshot.of(db, group2));
        }

        ownedIdentitySyncSnapshot.pinned_discussions = new ArrayList<>();
        for (Discussion discussion : db.discussionDao().getAllPinned(ownedIdentity.bytesOwnedIdentity)) {
            if (!discussion.isNormalOrReadOnly()) {
                continue;
            }
            ObvSyncAtom.DiscussionIdentifier discussionIdentifier;
            switch (discussion.discussionType) {
                case Discussion.TYPE_CONTACT:
                    discussionIdentifier = new ObvSyncAtom.DiscussionIdentifier(ObvSyncAtom.DiscussionIdentifier.CONTACT, discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP:
                    discussionIdentifier = new ObvSyncAtom.DiscussionIdentifier(ObvSyncAtom.DiscussionIdentifier.GROUP_V1, discussion.bytesDiscussionIdentifier);
                    break;
                case Discussion.TYPE_GROUP_V2:
                    discussionIdentifier = new ObvSyncAtom.DiscussionIdentifier(ObvSyncAtom.DiscussionIdentifier.GROUP_V2, discussion.bytesDiscussionIdentifier);
                    break;
                default:
                    continue;
            }
            ownedIdentitySyncSnapshot.pinned_discussions.add(new ObvBytesKey(discussionIdentifier.encode().getBytes()));
        }
        ownedIdentitySyncSnapshot.pinned_sorted = false;

        ownedIdentitySyncSnapshot.domain = DEFAULT_DOMAIN;
        return ownedIdentitySyncSnapshot;
    }

    @JsonIgnore
    public void restore(AppDatabase db, byte[] bytesOwnedIdentity) {
        if (domain.contains(CUSTOM_NAME) && custom_name != null) {
            db.ownedIdentityDao().updateCustomDisplayName(bytesOwnedIdentity, custom_name);
        }

        // restore contacts
        if (domain.contains(CONTACTS) && contacts != null) {
            for (Map.Entry<ObvBytesKey, ContactSyncSnapshot> contactEntry : contacts.entrySet()) {
                try {
                    contactEntry.getValue().restore(db, bytesOwnedIdentity, contactEntry.getKey().getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // restore groups v1
        if (domain.contains(GROUPS) && groups != null) {
            for (Map.Entry<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groupEntry : groups.entrySet()) {
                try {
                    groupEntry.getValue().restore(db, bytesOwnedIdentity, groupEntry.getKey().getGroupOwnerAndUid());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // restore groups v2
        if (domain.contains(GROUPS2) && groups2 != null) {
            for (Map.Entry<ObvBytesKey, GroupV2SyncSnapshot> group2Entry : groups2.entrySet()) {
                try {
                    group2Entry.getValue().restore(db, bytesOwnedIdentity, group2Entry.getKey().getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // pinned
        if (domain.contains(PINNED) && pinned_discussions != null) {
            // pinned sorted is ignored on Android (for now)
            for (ObvBytesKey discussionKey : pinned_discussions) {
                try {
                    ObvSyncAtom.DiscussionIdentifier discussionIdentifier = ObvSyncAtom.DiscussionIdentifier.of(new Encoded(discussionKey.getBytes()));
                    Discussion discussion;
                    switch (discussionIdentifier.type) {
                        case ObvSyncAtom.DiscussionIdentifier.CONTACT: {
                            discussion = db.discussionDao().getByContact(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier);
                            break;
                        }
                        case ObvSyncAtom.DiscussionIdentifier.GROUP_V1: {
                            discussion = db.discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier);
                            break;
                        }
                        case ObvSyncAtom.DiscussionIdentifier.GROUP_V2: {
                            discussion = db.discussionDao().getByGroupIdentifier(bytesOwnedIdentity, discussionIdentifier.bytesDiscussionIdentifier);
                            break;
                        }
                        default: {
                            discussion = null;
                            break;
                        }
                    }
                    if (discussion != null) {
                        db.discussionDao().updatePinned(discussion.id, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // update all group member names at the end only
        new UpdateAllGroupMembersNames(bytesOwnedIdentity).run();
    }

    @Override
    @JsonIgnore
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof OwnedIdentitySyncSnapshot)) {
            return false;
        }

        OwnedIdentitySyncSnapshot other = (OwnedIdentitySyncSnapshot) otherSnapshotNode;
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
                case CONTACTS: {
                    if (!Objects.equals(contacts.keySet(), other.contacts.keySet())) {
                        return false;
                    }
                    for (Map.Entry<ObvBytesKey, ContactSyncSnapshot> contactEntry : contacts.entrySet()) {
                        if (!contactEntry.getValue().areContentsTheSame(other.contacts.get(contactEntry.getKey()))) {
                            return false;
                        }
                    }
                    break;
                }
                case GROUPS: {
                    if (!Objects.equals(groups.keySet(), other.groups.keySet())) {
                        return false;
                    }
                    for (Map.Entry<ObvGroupOwnerAndUidKey, GroupV1SyncSnapshot> groupEntry : groups.entrySet()) {
                        if (!groupEntry.getValue().areContentsTheSame(other.groups.get(groupEntry.getKey()))) {
                            return false;
                        }
                    }
                    break;
                }
                case GROUPS2: {
                    if (!Objects.equals(groups2.keySet(), other.groups2.keySet())) {
                        return false;
                    }
                    for (Map.Entry<ObvBytesKey, GroupV2SyncSnapshot> group2Entry : groups2.entrySet()) {
                        if (!group2Entry.getValue().areContentsTheSame(other.groups2.get(group2Entry.getKey()))) {
                            return false;
                        }
                    }
                    break;
                }
                case PINNED: {
                    if (getPinned_sorted() && other.getPinned_sorted()) {
                        // if both snapshot use sorted pinned discussions, compare the lists
                        if (!Objects.equals(pinned_discussions, other.pinned_discussions)) {
                            return false;
                        }
                    } else {
                        // otherwise compare hashsets
                        if (!Objects.equals(new HashSet<>(pinned_discussions), new HashSet<>(other.pinned_discussions))) {
                            return false;
                        }
                    }
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
