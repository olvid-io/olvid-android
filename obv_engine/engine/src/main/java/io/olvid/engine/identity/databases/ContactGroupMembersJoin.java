/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ContactGroupMembersJoin implements ObvDatabase {
    static final String TABLE_NAME = "contact_group_members_join";

    private final IdentityManagerSession identityManagerSession;

    private byte[] groupOwnerAndUid;
    static final String GROUP_OWNER_AND_UID = "group_owner_and_uid";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";

    // region constructors

    public static ContactGroupMembersJoin create(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity) {
        if ((groupOwnerAndUid == null) || (ownedIdentity == null) || (contactIdentity == null)) {
            return null;
        }
        try {
            ContactGroupMembersJoin contactGroupMembersJoin = new ContactGroupMembersJoin(identityManagerSession, groupOwnerAndUid, ownedIdentity, contactIdentity);
            contactGroupMembersJoin.insert();
            return  contactGroupMembersJoin;
        } catch (SQLException e) {
            return null;
        }
    }

    private ContactGroupMembersJoin(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity) {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = groupOwnerAndUid;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
    }

    private ContactGroupMembersJoin(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.groupOwnerAndUid = res.getBytes(GROUP_OWNER_AND_UID);
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
    }

    // endregion


    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    GROUP_OWNER_AND_UID + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    " CONSTRAINT PK_" + TABLE_NAME + " PRIMARY KEY(" + GROUP_OWNER_AND_UID + ", " + OWNED_IDENTITY + ", " + CONTACT_IDENTITY + "), " +
                    " FOREIGN KEY (" + GROUP_OWNER_AND_UID + "," + OWNED_IDENTITY + ") REFERENCES " + ContactGroup.TABLE_NAME + "(" + ContactGroup.GROUP_OWNER_AND_UID + "," + ContactGroup.OWNED_IDENTITY + ") ON DELETE CASCADE, " +
                    " FOREIGN KEY (" + CONTACT_IDENTITY + "," + OWNED_IDENTITY + ") REFERENCES " + ContactIdentity.TABLE_NAME + "(" + ContactIdentity.CONTACT_IDENTITY + "," + ContactIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 11 && newVersion >= 11) {
            try (Statement statement = session.createStatement()) {
                Logger.d("MIGRATING contact_group_members_join DATABASE FROM VERSION " + oldVersion + " TO 11");
                statement.execute("ALTER TABLE contact_group_members_join RENAME TO old_contact_group_members_join");
                statement.execute("CREATE TABLE contact_group_members_join (" +
                        " group_owner_and_uid BLOB NOT NULL, " +
                        " owned_identity BLOB NOT NULL, " +
                        " contact_identity BLOB NOT NULL, " +
                        " CONSTRAINT PK_contact_group_members_join PRIMARY KEY(group_owner_and_uid, owned_identity, contact_identity), " +
                        " FOREIGN KEY (group_owner_and_uid,owned_identity) REFERENCES contact_group(group_owner_and_uid,owned_identity) ON DELETE CASCADE, " +
                        " FOREIGN KEY (contact_identity,owned_identity) REFERENCES contact_identity(identity,owned_identity) ON DELETE CASCADE);");
                try (ResultSet res = statement.executeQuery("SELECT * FROM old_contact_group_members_join")) {
                    while (res.next()) {
                        try (PreparedStatement preparedStatement = session.prepareStatement("INSERT INTO contact_group_members_join VALUES (?,?,?)")) {
                            preparedStatement.setBytes(1, res.getBytes(1));
                            preparedStatement.setBytes(2, res.getBytes(2));
                            preparedStatement.setBytes(3, res.getBytes(3));
                            preparedStatement.executeUpdate();
                        }
                    }
                }
                statement.execute("DROP TABLE old_contact_group_members_join");
            }
            oldVersion = 11;
        }
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM contact_group_members_join AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM contact_group " +
                        " WHERE group_owner_and_uid = p.group_owner_and_uid" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
                statement.execute("DELETE FROM contact_group_members_join AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM contact_identity " +
                        " WHERE identity = p.contact_identity" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?);")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
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

    public static ContactGroupMembersJoin get(IdentityManagerSession identityManagerSession, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " + GROUP_OWNER_AND_UID + " = ? " +
                        " AND " + OWNED_IDENTITY + " = ? " +
                        " AND " + CONTACT_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupOwnerAndUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setBytes(3, contactIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new ContactGroupMembersJoin(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }


    public static Identity[] getContactIdentitiesInGroup(IdentityManagerSession identityManagerSession, byte[] groupUid, Identity ownedIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement(
                "SELECT contact." + ContactIdentity.CONTACT_IDENTITY + " FROM " + TABLE_NAME + " AS joiin " +
                        " INNER JOIN " + ContactIdentity.TABLE_NAME + " AS contact " +
                        " ON contact." + ContactIdentity.CONTACT_IDENTITY + " = joiin." + CONTACT_IDENTITY +
                        " AND contact." + ContactIdentity.OWNED_IDENTITY + " = joiin." + OWNED_IDENTITY +
                        " WHERE joiin." + GROUP_OWNER_AND_UID + " = ? AND joiin." + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, groupUid);
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<Identity> list = new ArrayList<>();
                while (res.next()) {
                    try {
                        list.add(Identity.of(res.getBytes(1)));
                    } catch (DecodingException e) {
                        e.printStackTrace();
                    }
                }
                return list.toArray(new Identity[0]);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new Identity[0];
        }
    }

    public static byte[][] getGroupOwnerAndUidsOfGroupsContainingContact(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + CONTACT_IDENTITY + " = ? AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<byte[]> list = new ArrayList<>();
                while (res.next()) {
                    ContactGroupMembersJoin contactGroupMembersJoin = new ContactGroupMembersJoin(identityManagerSession, res);
                    list.add(contactGroupMembersJoin.groupOwnerAndUid);
                }
                return list.toArray(new byte[0][]);
            }
        }
    }

    // endregion




    // region hooks

    private long commitHookBits = 0;
    private static final long HOOK_BIT_INSERTED = 0x1;
    private static final long HOOK_BIT_DELETED = 0x2;

    @Override
    public void wasCommitted() {
        if ((commitHookBits & HOOK_BIT_INSERTED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_GROUP_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY, contactIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED, userInfo);
        }
        if ((commitHookBits & HOOK_BIT_DELETED) != 0) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_GROUP_UID_KEY, groupOwnerAndUid);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY, ownedIdentity);
            userInfo.put(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY, contactIdentity);
            identityManagerSession.notificationPostingDelegate.postNotification(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED, userInfo);
        }
        commitHookBits = 0;
    }

    // endregion

    // region backup

    static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid) {
        Identity[] contactIdentities = getContactIdentitiesInGroup(identityManagerSession, groupOwnerAndUid, ownedIdentity);
        Pojo_0[] pojos = new Pojo_0[contactIdentities.length];
        for (int i=0; i<contactIdentities.length; i++) {
            pojos[i] = new Pojo_0();
            pojos[i].contact_identity = contactIdentities[i].getBytes();
        }
        return pojos;
    }

    static void restoreAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, byte[] groupOwnerAndUid, Pojo_0[] pojos) {
        if (pojos == null) {
            return;
        }
        try {
            for (Pojo_0 pojo : pojos) {
                create(identityManagerSession, groupOwnerAndUid, ownedIdentity, Identity.of(pojo.contact_identity));
            }
        } catch (DecodingException e) {
            e.printStackTrace();
        }
    }


    public static class Pojo_0 {
        public byte[] contact_identity;
    }

    // endregion
}
