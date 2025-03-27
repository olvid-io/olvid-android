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

package io.olvid.engine.identity.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class PendingGroupMember implements ObvDatabase {
    static final String TABLE_NAME = "pending_group_member";

    private final IdentityManagerSession identityManagerSession;

    private byte[] groupOwnerAndUid;
    static final String GROUP_OWNER_AND_UID = "group_owner_and_uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private String contactSerializedDetails;
    static final String CONTACT_SERIALIZED_DETAILS = "contact_display_name";
    public boolean declined;
    static final String DECLINED = "declined";

    public byte[] getGroupOwnerAndUid() {
        return groupOwnerAndUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public String getContactSerializedDetails() {
        return contactSerializedDetails;
    }

    public boolean isDeclined() {
        return declined;
    }

    // region constructors

    public static PendingGroupMember create(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, String contactSerializedDetails) {
        if ((groupOwnerAndUid == null) || (ownedIdentity == null) || (contactIdentity == null) || (contactSerializedDetails == null)) {
            return null;
        }
        try {
            PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, groupOwnerAndUid, ownedIdentity, contactIdentity, contactSerializedDetails);
            pendingGroupMember.insert();
            return  pendingGroupMember;
        } catch (SQLException e) {
            return null;
        }
    }

    public PendingGroupMember(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, String contactSerializedDetails) {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = groupOwnerAndUid;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
        this.contactSerializedDetails = contactSerializedDetails;
        this.declined = false;
    }

    private PendingGroupMember(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = res.getBytes(GROUP_OWNER_AND_UID);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.contactSerializedDetails = res.getString(CONTACT_SERIALIZED_DETAILS);
        this.declined = res.getBoolean(DECLINED);
    }

    // endregion

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    GROUP_OWNER_AND_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_SERIALIZED_DETAILS + " TEXT NOT NULL, " +
                    DECLINED + " BIT NOT NULL, " +
                    "CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_OWNER_AND_UID + ", " + OWNED_IDENTITY + ", " + CONTACT_IDENTITY + "), " +
                    "FOREIGN KEY (" + GROUP_OWNER_AND_UID + "," + OWNED_IDENTITY + ") REFERENCES " + ContactGroup.TABLE_NAME + "(" + ContactGroup.GROUP_OWNER_AND_UID + "," + ContactGroup.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING pending_group_member DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("DROP TABLE pending_group_member");
                statement.execute("CREATE TABLE pending_group_member (" +
                        "group_owner_and_uid BLOB NOT NULL, " +
                        "owned_identity BLOB NOT NULL, " +
                        "contact_identity BLOB NOT NULL, " +
                        "contact_display_name TEXT NOT NULL, " +
                        "declined BIT NOT NULL, " +
                        "CONSTRAINT PK_pending_group_member PRIMARY KEY(group_owner_and_uid, owned_identity, contact_identity), " +
                        "FOREIGN KEY (group_owner_and_uid,owned_identity) REFERENCES contact_group(group_owner_and_uid,owned_identity) ON DELETE CASCADE);");
            }
            oldVersion = 11;
        }
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM pending_group_member AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM contact_group " +
                        " WHERE group_owner_and_uid = p.group_owner_and_uid" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?);")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            statement.setString(4, contactSerializedDetails);
            statement.setBoolean(5, declined);
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_INSERTED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ? AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            statement.executeUpdate();
            commitHookBits |= HOOK_BIT_DELETED;
            identityManagerSession.session.addSessionCommitListener(this);
        }
    }

    // endregion


    // region getters

    public static IdentityWithSerializedDetails[] getPendingMembersInGroup(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity) {
        if ((groupOwnerAndUid == null) || (ownedIdentity == null)) {
            return new IdentityWithSerializedDetails[0];
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<IdentityWithSerializedDetails> list = new ArrayList<>();
                while (res.next()) {
                    PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, res);
                    list.add(new IdentityWithSerializedDetails(pendingGroupMember.contactIdentity, pendingGroupMember.contactSerializedDetails));
                }
                return list.toArray(new IdentityWithSerializedDetails[0]);
            }
        } catch (SQLException e) {
            return new IdentityWithSerializedDetails[0];
        }
    }

    public static Identity[] getDeclinedPendingMembersInGroup(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity) {
        if ((groupOwnerAndUid == null) || (ownedIdentity == null)) {
            return new Identity[0];
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ? AND " + DECLINED + " = 1;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<Identity> list = new ArrayList<>();
                while (res.next()) {
                    PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, res);
                    list.add(pendingGroupMember.contactIdentity);
                }
                return list.toArray(new Identity[0]);
            }
        } catch (SQLException e) {
            return new Identity[0];
        }
    }

    public static PendingGroupMember get(IdentityManagerSession identityManagerSession, byte[] groupUid, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        if ((groupUid == null) || (ownedIdentity == null) || (contactIdentity == null)) {
            return null;
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ? AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new PendingGroupMember(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static PendingGroupMember[] getAllInGroup(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity) {
        if ((groupOwnerAndUid == null) || (ownedIdentity == null)) {
            return new PendingGroupMember[0];
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + GROUP_OWNER_AND_UID + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<PendingGroupMember> list = new ArrayList<>();
                while (res.next()) {
                    PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, res);
                    list.add(pendingGroupMember);
                }
                return list.toArray(new PendingGroupMember[0]);
            }
        } catch (SQLException e) {
            return new PendingGroupMember[0];
        }
    }

    public static byte[][] getGroupOwnerAndUidOfGroupsWhereContactIsPending(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, boolean excludeDeclined) {
        if ((ownedIdentity == null) || (contactIdentity == null)) {
            return new byte[0][];
        }
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + CONTACT_IDENTITY + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ? " +
                        (excludeDeclined ? (" AND " + DECLINED + " = 0 ") : "") +
                        " AND " + GROUP_OWNER_AND_UID +
                        " IN (SELECT " + ContactGroup.GROUP_OWNER_AND_UID + " FROM " + ContactGroup.TABLE_NAME + " WHERE " + ContactGroup.OWNED_IDENTITY + " = ? AND " + ContactGroup.GROUP_OWNER + " IS NULL);")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<byte[]> list = new ArrayList<>();
                while (res.next()) {
                    PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, res);
                    list.add(pendingGroupMember.getGroupOwnerAndUid());
                }
                return list.toArray(new byte[0][]);
            }
        } catch (SQLException e) {
            return new byte[0][];
        }
    }

    // endregion

    // region setters


    public void setDeclined(boolean declined) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("UPDATE " + TABLE_NAME +
                " SET " + DECLINED + " = ? " +
                " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ? " +
                " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBoolean(1, declined);
            statement.setBytes(2, this.groupOwnerAndUid);
            statement.setBytes(3, this.ownedIdentity.getBytes());
            statement.setBytes(4, this.contactIdentity.getBytes());
            statement.executeUpdate();
            this.declined = declined;
        }
        commitHookBits |= HOOK_BIT_DECLINED_TOGGLED;
        identityManagerSession.session.addSessionCommitListener(this);
    }

    // endregion



    // region hooks

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;
    private static final long HOOK_BIT_DELETED = 0x2;
    private static final long HOOK_BIT_DECLINED_TOGGLED = 0x4;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_GROUP_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_SERIALIZED_DETAILS_KEY, contactSerializedDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_GROUP_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_SERIALIZED_DETAILS_KEY, contactSerializedDetails);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DECLINED_TOGGLED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_GROUP_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_CONTACT_IDENTITY_KEY, contactIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_DECLINED_KEY, declined);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED, userInfo);
        }
        commitHookBits = 0;
    }

    // endregion

    // region backup

    static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid) {
        PendingGroupMember[] pendingGroupMembers = getAllInGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity);
        Pojo_0[] pojos = new Pojo_0[pendingGroupMembers.length];
        for (int i=0; i<pendingGroupMembers.length; i++) {
            pojos[i] = pendingGroupMembers[i].backup();
        }
        return pojos;
    }

    static void restoreAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid, Pojo_0[] pojos) throws SQLException {
        if (pojos == null) {
            return;
        }
        for (Pojo_0 pojo: pojos) {
            restore(identityManagerSession, ownedIdentity, groupOwnerAndUid, pojo);
        }
    }

    private Pojo_0 backup() {
        Pojo_0 pojo = new Pojo_0();
        pojo.contact_identity = contactIdentity.getBytes();
        pojo.serialized_details = contactSerializedDetails;
        pojo.declined = declined;
        return pojo;
    }

    static void restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid, Pojo_0 pojo) throws SQLException {
        try {
            PendingGroupMember pendingGroupMember = new PendingGroupMember(identityManagerSession, groupOwnerAndUid, ownedIdentity, Identity.of(pojo.contact_identity), pojo.serialized_details);
            pendingGroupMember.declined = pojo.declined;
            pendingGroupMember.insert();
        } catch (DecodingException e) {
            Logger.x(e);
        }
    }

    public static class Pojo_0 {
        public byte[] contact_identity;
        public String serialized_details;
        public boolean declined;
    }

    // endregion
}
