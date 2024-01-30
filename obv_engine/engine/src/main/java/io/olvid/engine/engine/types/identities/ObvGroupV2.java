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

package io.olvid.engine.engine.types.identities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvBytesKey;

public class ObvGroupV2 {
    public final byte[] bytesOwnedIdentity;
    public final GroupV2.Identifier groupIdentifier;
    public final HashSet<GroupV2.Permission> ownPermissions;
    public final HashSet<ObvGroupV2Member> otherGroupMembers;
    public final HashSet<ObvGroupV2PendingMember> pendingGroupMembers;
    public final ObvGroupV2DetailsAndPhotos detailsAndPhotos;

    public ObvGroupV2(byte[] bytesOwnedIdentity, GroupV2.Identifier groupIdentifier, HashSet<GroupV2.Permission> ownPermissions, HashSet<ObvGroupV2Member> otherGroupMembers, HashSet<ObvGroupV2PendingMember> pendingGroupMembers, String serializedGroupDetails, String photoUrl, String serializedPublishedDetails, String publishedPhotoUrl) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.groupIdentifier = groupIdentifier;
        this.ownPermissions = ownPermissions;
        this.otherGroupMembers = otherGroupMembers;
        this.pendingGroupMembers = pendingGroupMembers;
        this.detailsAndPhotos = new ObvGroupV2DetailsAndPhotos(serializedGroupDetails, photoUrl, serializedPublishedDetails, publishedPhotoUrl);
    }

    private ObvGroupV2(byte[] bytesOwnedIdentity, GroupV2.Identifier groupIdentifier, HashSet<GroupV2.Permission> ownPermissions, HashSet<ObvGroupV2Member> otherGroupMembers, HashSet<ObvGroupV2PendingMember> pendingGroupMembers, ObvGroupV2DetailsAndPhotos detailsAndPhotos) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.groupIdentifier = groupIdentifier;
        this.ownPermissions = ownPermissions;
        this.otherGroupMembers = otherGroupMembers;
        this.pendingGroupMembers = pendingGroupMembers;
        this.detailsAndPhotos = detailsAndPhotos;
    }

    public Encoded encode() {
        List<Encoded> encodedMembers = new ArrayList<>();
        List<Encoded> encodedPendingMembers = new ArrayList<>();
        if (otherGroupMembers != null) {
            for (ObvGroupV2Member member : otherGroupMembers) {
                encodedMembers.add(member.encode());
            }
        }
        if (pendingGroupMembers != null) {
            for (ObvGroupV2PendingMember member : pendingGroupMembers) {
                encodedPendingMembers.add(member.encode());
            }
        }

        //noinspection ConstantConditions
        return Encoded.of(new Encoded[]{
                Encoded.of(bytesOwnedIdentity),
                groupIdentifier.encode(),
                Encoded.of(GroupV2.Permission.serializePermissions(ownPermissions)),
                Encoded.of(encodedMembers.toArray(new Encoded[0])),
                Encoded.of(encodedPendingMembers.toArray(new Encoded[0])),
                detailsAndPhotos.encode(),
        });
    }

    public static ObvGroupV2 of(Encoded encoded) throws DecodingException {
        Encoded[] list = encoded.decodeList();
        if (list.length != 6) {
            throw new DecodingException();
        }
        HashSet<ObvGroupV2Member> otherGroupMembers = new HashSet<>();
        HashSet<ObvGroupV2PendingMember> pendingGroupMembers = new HashSet<>();
        for (Encoded encodedMember : list[3].decodeList()) {
            otherGroupMembers.add(ObvGroupV2Member.of(encodedMember));
        }
        for (Encoded encodedMember : list[4].decodeList()) {
            pendingGroupMembers.add(ObvGroupV2PendingMember.of(encodedMember));
        }

        return new ObvGroupV2(
                list[0].decodeBytes(),
                GroupV2.Identifier.of(list[1]),
                GroupV2.Permission.deserializeKnownPermissions(list[2].decodeBytes()),
                otherGroupMembers,
                pendingGroupMembers,
                ObvGroupV2DetailsAndPhotos.of(list[5])
        );
    }

    public static class ObvGroupV2Member {
        public final byte[] bytesIdentity;
        public final HashSet<GroupV2.Permission> permissions;

        public ObvGroupV2Member(byte[] bytesIdentity, HashSet<GroupV2.Permission> permissions) {
            this.bytesIdentity = bytesIdentity;
            this.permissions = permissions;
        }

        public Encoded encode() {
            //noinspection ConstantConditions
            return Encoded.of(new Encoded[]{
                    Encoded.of(bytesIdentity),
                    Encoded.of(GroupV2.Permission.serializePermissions(permissions)),
            });
        }

        public static ObvGroupV2Member of(Encoded encoded) throws DecodingException {
            Encoded[] list = encoded.decodeList();
            if (list.length != 2) {
                throw new DecodingException();
            }
            return new ObvGroupV2Member(
                    list[0].decodeBytes(),
                    GroupV2.Permission.deserializeKnownPermissions(list[1].decodeBytes())
            );
        }
    }

    public static class ObvGroupV2PendingMember {
        public final byte[] bytesIdentity;
        public final HashSet<GroupV2.Permission> permissions;
        public final String serializedDetails;

        public ObvGroupV2PendingMember(byte[] bytesIdentity, HashSet<GroupV2.Permission> permissions, String serializedDetails) {
            this.bytesIdentity = bytesIdentity;
            this.permissions = permissions;
            this.serializedDetails = serializedDetails;
        }

        public Encoded encode() {
            //noinspection ConstantConditions
            return Encoded.of(new Encoded[]{
                    Encoded.of(bytesIdentity),
                    Encoded.of(GroupV2.Permission.serializePermissions(permissions)),
                    Encoded.of(serializedDetails),
            });
        }

        public static ObvGroupV2PendingMember of(Encoded encoded) throws DecodingException {
            Encoded[] list = encoded.decodeList();
            if (list.length != 3) {
                throw new DecodingException();
            }
            return new ObvGroupV2PendingMember(
                    list[0].decodeBytes(),
                    GroupV2.Permission.deserializeKnownPermissions(list[1].decodeBytes()),
                    list[2].decodeString()
            );
        }
    }

    public static class ObvGroupV2DetailsAndPhotos {
        public final String serializedGroupDetails; // non null
        public final String photoUrl; // null if the group does not has a photo, "" if it has a photo but it was not downloaded yet
        public final String serializedPublishedDetails; // null if same version as serializedGroupDetails
        public final String publishedPhotoUrl; // null if serializedPublishedDetails is null, or if there is no photo, "" if there is a photo and it was not downloaded yet

        private static final String SERIALIZED_GROUP_DETAILS_KEY = "sgd";
        private static final String PHOTO_URL_KEY = "pu";
        private static final String SERIALIZED_PUBLISHED_DETAILS_KEY = "spd";
        private static final String PUBLISHED_PHOTO_URL_KEY = "ppu";

        public ObvGroupV2DetailsAndPhotos(String serializedGroupDetails, String photoUrl, String serializedPublishedDetails, String publishedPhotoUrl) {
            this.serializedGroupDetails = serializedGroupDetails;
            this.photoUrl = photoUrl;
            this.serializedPublishedDetails = serializedPublishedDetails;
            this.publishedPhotoUrl = publishedPhotoUrl;
        }

        public String getNullIfEmptyPhotoUrl() {
            if ((photoUrl == null) || (photoUrl.length() == 0)) {
                return null;
            } else {
                return photoUrl;
            }
        }

        public String getNullIfEmptyPublishedPhotoUrl() {
            if ((publishedPhotoUrl == null) || (publishedPhotoUrl.length() == 0)) {
                return null;
            } else {
                return publishedPhotoUrl;
            }
        }

        Encoded encode() {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            map.put(new DictionaryKey(SERIALIZED_GROUP_DETAILS_KEY), Encoded.of(serializedGroupDetails));
            if (photoUrl != null) {
                map.put(new DictionaryKey(PHOTO_URL_KEY), Encoded.of(photoUrl));
            }
            if (serializedPublishedDetails != null) {
                map.put(new DictionaryKey(SERIALIZED_PUBLISHED_DETAILS_KEY), Encoded.of(serializedPublishedDetails));
            }
            if (publishedPhotoUrl != null) {
                map.put(new DictionaryKey(PUBLISHED_PHOTO_URL_KEY), Encoded.of(publishedPhotoUrl));
            }
            return Encoded.of(map);
        }

        static ObvGroupV2DetailsAndPhotos of(Encoded encoded) throws DecodingException {
            HashMap<DictionaryKey, Encoded> detailsMap = encoded.decodeDictionary();
            Encoded enc = detailsMap.get(new DictionaryKey(SERIALIZED_GROUP_DETAILS_KEY));
            String serializedGroupDetails = enc.decodeString();
            enc = detailsMap.get(new DictionaryKey(PHOTO_URL_KEY));
            String photoUrl = (enc == null) ? null : enc.decodeString();
            enc = detailsMap.get(new DictionaryKey(PHOTO_URL_KEY));
            String serializedPublishedDetails = (enc == null) ? null : enc.decodeString();
            enc = detailsMap.get(new DictionaryKey(PHOTO_URL_KEY));
            String publishedPhotoUrl = (enc == null) ? null : enc.decodeString();

            return new ObvGroupV2DetailsAndPhotos(serializedGroupDetails, photoUrl, serializedPublishedDetails, publishedPhotoUrl);
        }
    }

    public static class ObvGroupV2ChangeSet {
        public final List<byte[]> removedMembers;
        public final HashMap<ObvBytesKey, HashSet<GroupV2.Permission>> addedMembersWithPermissions;
        public final HashMap<ObvBytesKey, HashSet<GroupV2.Permission>> permissionChanges; // may contain your ownedIdentity

        public String updatedSerializedGroupDetails; // null if no change
        public String updatedJsonGroupType; // null if no change
        public String updatedPhotoUrl; // null if no change, "" if photo removed

        public ObvGroupV2ChangeSet() {
            removedMembers = new ArrayList<>();
            addedMembersWithPermissions = new HashMap<>();
            permissionChanges = new HashMap<>();
            updatedSerializedGroupDetails = null;
            updatedJsonGroupType = null;
            updatedPhotoUrl = null;
        }

        public boolean isEmpty() {
            return removedMembers.isEmpty() && addedMembersWithPermissions.isEmpty() && permissionChanges.isEmpty() && updatedPhotoUrl == null && updatedSerializedGroupDetails == null && updatedJsonGroupType == null;
        }

        public Encoded encode() {
            HashMap<DictionaryKey, Encoded> dic = new HashMap<>();
            if (!removedMembers.isEmpty()) {
                ArrayList<Encoded> encodeds = new ArrayList<>();
                for (byte[] removedMember : removedMembers) {
                    encodeds.add(Encoded.of(removedMember));
                }
                dic.put(new DictionaryKey("rm"), Encoded.of(encodeds.toArray(new Encoded[0])));
            }
            if (!addedMembersWithPermissions.isEmpty()) {
                ArrayList<Encoded> encodeds = new ArrayList<>();
                for (Map.Entry<ObvBytesKey, HashSet<GroupV2.Permission>> entry : addedMembersWithPermissions.entrySet()) {
                    byte[] serializedPermissions = GroupV2.Permission.serializePermissions(entry.getValue());
                    if (serializedPermissions != null) {
                        encodeds.add(Encoded.of(entry.getKey().getBytes()));
                        encodeds.add(Encoded.of(serializedPermissions));
                    }
                }
                dic.put(new DictionaryKey("am"), Encoded.of(encodeds.toArray(new Encoded[0])));
            }
            if (!permissionChanges.isEmpty()) {
                ArrayList<Encoded> encodeds = new ArrayList<>();
                for (Map.Entry<ObvBytesKey, HashSet<GroupV2.Permission>> entry : permissionChanges.entrySet()) {
                    byte[] serializedPermissions = GroupV2.Permission.serializePermissions(entry.getValue());
                    if (serializedPermissions != null) {
                        encodeds.add(Encoded.of(entry.getKey().getBytes()));
                        encodeds.add(Encoded.of(serializedPermissions));
                    }
                }
                dic.put(new DictionaryKey("pc"), Encoded.of(encodeds.toArray(new Encoded[0])));
            }
            if (updatedSerializedGroupDetails != null) {
                dic.put(new DictionaryKey("gd"), Encoded.of(updatedSerializedGroupDetails));
            }
            if (updatedJsonGroupType != null) {
                dic.put(new DictionaryKey("gt"), Encoded.of(updatedJsonGroupType));
            }
            if (updatedPhotoUrl != null) {
                dic.put(new DictionaryKey("pu"), Encoded.of(updatedPhotoUrl));
            }
            return Encoded.of(dic);
        }

        public static ObvGroupV2ChangeSet of(Encoded encoded) throws DecodingException {
            HashMap<DictionaryKey, Encoded> dic = encoded.decodeDictionary();

            ObvGroupV2ChangeSet changeSet = new ObvGroupV2ChangeSet();
            Encoded enc = dic.get(new DictionaryKey("rm"));
            if (enc != null) {
                for (Encoded encodedGroupMember : enc.decodeList()) {
                    changeSet.removedMembers.add(encodedGroupMember.decodeBytes());
                }
            }
            enc = dic.get(new DictionaryKey("am"));
            if (enc != null) {
                Encoded[] encodeds = enc.decodeList();
                for (int i=0; i<encodeds.length; i +=2) {
                    changeSet.addedMembersWithPermissions.put(new ObvBytesKey(encodeds[i].decodeBytes()), GroupV2.Permission.deserializeKnownPermissions(encodeds[i+1].decodeBytes()));
                }
            }
            enc = dic.get(new DictionaryKey("pc"));
            if (enc != null) {
                Encoded[] encodeds = enc.decodeList();
                for (int i=0; i<encodeds.length; i +=2) {
                    changeSet.permissionChanges.put(new ObvBytesKey(encodeds[i].decodeBytes()), GroupV2.Permission.deserializeKnownPermissions(encodeds[i+1].decodeBytes()));
                }
            }
            enc = dic.get(new DictionaryKey("gd"));
            if (enc != null) {
                changeSet.updatedSerializedGroupDetails = enc.decodeString();
            }
            enc = dic.get(new DictionaryKey("gt"));
            if (enc != null) {
                changeSet.updatedJsonGroupType = enc.decodeString();
            }
            enc = dic.get(new DictionaryKey("pu"));
            if (enc != null) {
                changeSet.updatedPhotoUrl = enc.decodeString();
            }

            return changeSet;
        }
    }
}
