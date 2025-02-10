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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class ContactTrustOrigin implements ObvDatabase {
    static final String TABLE_NAME = "contact_trust_origin";

    private final IdentityManagerSession identityManagerSession;

    private long rowId;
    static final String ROW_ID = "row_id";
    private Identity contactIdentity;
    static final String CONTACT_IDENTITY = "contact_identity";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private long timestamp;
    static final String TIMESTAMP = "timestamp";
    private int trustType;
    static final String TRUST_TYPE = "trust_type";
    private Identity mediatorOrGroupOwnerIdentity;
    static final String MEDIATOR_OR_GROUP_OWNER_IDENTITY = "mediator_or_group_owner_identity";
    private Integer mediatorOrGroupOwnerTrustLevelMajor;
    static final String MEDIATOR_OR_GROUP_OWNER_TRUST_LEVEL_MAJOR = "mediator_or_group_owner_trust_level_major";
    private String identityServer;
    static final String IDENTITY_SERVER = "identity_server";
    private byte[] serializedGroupIdentifier;
    static final String SERIALIZED_GROUP_IDENTIFIER = "serialized_group_identifier";

    public static final int TRUST_TYPE_DIRECT = 1;
    public static final int TRUST_TYPE_INTRODUCTION = 2;
    public static final int TRUST_TYPE_GROUP = 3;
    public static final int TRUST_TYPE_IDENTITY_SERVER = 4;
    public static final int TRUST_TYPE_SERVER_GROUP_V2 = 5;

    // region computed properties

    TrustLevel getTrustLevel() {
        switch (trustType) {
            case TRUST_TYPE_DIRECT:
                return TrustLevel.createDirect();
            case TRUST_TYPE_GROUP:
            case TRUST_TYPE_INTRODUCTION:
                return TrustLevel.createIndirect(mediatorOrGroupOwnerTrustLevelMajor);
            case TRUST_TYPE_IDENTITY_SERVER:
                return TrustLevel.createServer();
            case TRUST_TYPE_SERVER_GROUP_V2:
                return TrustLevel.createServerGroupV2();
            default:
                return null;
        }
    }

    public TrustOrigin getTrustOrigin() {
        switch (trustType) {
            case TRUST_TYPE_DIRECT:
                return TrustOrigin.createDirectTrustOrigin(timestamp);
            case TRUST_TYPE_GROUP:
                return TrustOrigin.createGroupTrustOrigin(timestamp, mediatorOrGroupOwnerIdentity);
            case TRUST_TYPE_INTRODUCTION:
                return TrustOrigin.createIntroductionTrustOrigin(timestamp, mediatorOrGroupOwnerIdentity);
            case TRUST_TYPE_IDENTITY_SERVER:
                return TrustOrigin.createKeycloakTrustOrigin(timestamp, identityServer);
            case TRUST_TYPE_SERVER_GROUP_V2:
                try {
                    return TrustOrigin.createServerGroupV2TrustOrigin(timestamp, GroupV2.Identifier.of(serializedGroupIdentifier));
                } catch (Exception e) {
                    return null;
                }
            default:
                return null;
        }
    }

    // endregion

    // region database

    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    CONTACT_IDENTITY + " BLOB NOT NULL, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    TIMESTAMP + " INTEGER NOT NULL, " +
                    TRUST_TYPE + " INTEGER NOT NULL, " +
                    MEDIATOR_OR_GROUP_OWNER_IDENTITY + " BLOB, " +
                    MEDIATOR_OR_GROUP_OWNER_TRUST_LEVEL_MAJOR + " INTEGER, " +
                    IDENTITY_SERVER + " TEXT, " +
                    SERIALIZED_GROUP_IDENTIFIER + " BLOB, " +
                    " FOREIGN KEY (" + CONTACT_IDENTITY + ", " + OWNED_IDENTITY + ") REFERENCES " + ContactIdentity.TABLE_NAME + " (" + ContactIdentity.CONTACT_IDENTITY + ", " + ContactIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 12 && newVersion >= 12) {
            try (Statement statement = session.createStatement()) {
                statement.execute("DELETE FROM contact_trust_origin AS p " +
                        " WHERE NOT EXISTS (" +
                        " SELECT 1 FROM contact_identity " +
                        " WHERE identity = p.contact_identity" +
                        " AND owned_identity = p.owned_identity" +
                        " )");
            }
            oldVersion = 12;
        }
        if (oldVersion < 32 && newVersion >= 32) {
            try (Statement statement = session.createStatement()) {
                statement.execute("ALTER TABLE contact_trust_origin " +
                        " ADD COLUMN serialized_group_identifier BLOB DEFAULT NULL");
            }
            oldVersion = 32;
        }

    }


    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + "(" +
                CONTACT_IDENTITY + ", " +
                OWNED_IDENTITY + ", " +
                TIMESTAMP + ", " +
                TRUST_TYPE + ", " +
                MEDIATOR_OR_GROUP_OWNER_IDENTITY + ", " +
                MEDIATOR_OR_GROUP_OWNER_TRUST_LEVEL_MAJOR + ", " +
                IDENTITY_SERVER +  ", " +
                SERIALIZED_GROUP_IDENTIFIER + ") " +
                " VALUES (?,?,?,?,?, ?,?,?);", Statement.RETURN_GENERATED_KEYS)) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setLong(3, timestamp);
            statement.setInt(4, trustType);
            if (mediatorOrGroupOwnerIdentity == null) {
                statement.setBytes(5, null);
            } else {
                statement.setBytes(5, mediatorOrGroupOwnerIdentity.getBytes());
            }
            if (mediatorOrGroupOwnerTrustLevelMajor == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setInt(6, mediatorOrGroupOwnerTrustLevelMajor);
            }
            statement.setString(7, identityServer);
            statement.setBytes(8, serializedGroupIdentifier);
            statement.executeUpdate();
            ResultSet res = statement.getGeneratedKeys();
            if (res.next()) {
                this.rowId = res.getLong(1);
            }
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + ROW_ID + " = ?;")) {
            statement.setLong(1, rowId);
            statement.executeUpdate();
        }
    }

    // endregion

    // region constructor

    public static ContactTrustOrigin create(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, TrustOrigin trustOrigin) {
        if (ownedIdentity == null || contactIdentity == null || trustOrigin == null) {
            return null;
        }
        try {
            int trustType;
            Identity mediatorOrGroupOwnerIdentity;
            String identityServer;
            byte[] serializedGroupIdentifier;
            switch (trustOrigin.getType()) {
                case DIRECT:
                    trustType = TRUST_TYPE_DIRECT;
                    mediatorOrGroupOwnerIdentity = null;
                    identityServer = null;
                    serializedGroupIdentifier = null;
                    break;
                case GROUP:
                    trustType = TRUST_TYPE_GROUP;
                    mediatorOrGroupOwnerIdentity = trustOrigin.getMediatorOrGroupOwnerIdentity();
                    identityServer = null;
                    serializedGroupIdentifier = null;
                    break;
                case INTRODUCTION:
                    trustType = TRUST_TYPE_INTRODUCTION;
                    mediatorOrGroupOwnerIdentity = trustOrigin.getMediatorOrGroupOwnerIdentity();
                    identityServer = null;
                    serializedGroupIdentifier = null;
                    break;
                case KEYCLOAK:
                    trustType = TRUST_TYPE_IDENTITY_SERVER;
                    mediatorOrGroupOwnerIdentity = null;
                    identityServer = trustOrigin.getKeycloakServer();
                    serializedGroupIdentifier = null;
                    break;
                case SERVER_GROUP_V2:
                    trustType = TRUST_TYPE_SERVER_GROUP_V2;
                    mediatorOrGroupOwnerIdentity = null;
                    identityServer = null;
                    serializedGroupIdentifier = trustOrigin.getGroupIdentifier().getBytes();
                    break;
                default:
                    return null;
            }
            Integer mediatorOrGroupOwnerTrustLevelMajor = null;
            if (mediatorOrGroupOwnerIdentity != null) {
                ContactIdentity mediatorOrGroupOwner = ContactIdentity.get(identityManagerSession, ownedIdentity, mediatorOrGroupOwnerIdentity);
                if (mediatorOrGroupOwner == null) {
                    return null;
                }
                mediatorOrGroupOwnerTrustLevelMajor = mediatorOrGroupOwner.getTrustLevel().major;
            }

            ContactTrustOrigin contactTrustOrigin = new ContactTrustOrigin(identityManagerSession, contactIdentity, ownedIdentity, trustOrigin.getTimestamp(), trustType, mediatorOrGroupOwnerIdentity, mediatorOrGroupOwnerTrustLevelMajor, identityServer, serializedGroupIdentifier);
            contactTrustOrigin.insert();
            return contactTrustOrigin;
        } catch (SQLException e) {
            Logger.x(e);
            return null;
        }
    }

    public ContactTrustOrigin(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity, long timestamp, int trustType, Identity mediatorOrGroupOwnerIdentity, Integer mediatorOrGroupOwnerTrustLevelMajor, String identityServer, byte[] serializedGroupIdentifier) {
        this.identityManagerSession = identityManagerSession;
        this.contactIdentity = contactIdentity;
        this.ownedIdentity = ownedIdentity;
        this.timestamp = timestamp;
        this.trustType = trustType;
        this.mediatorOrGroupOwnerIdentity = mediatorOrGroupOwnerIdentity;
        this.mediatorOrGroupOwnerTrustLevelMajor = mediatorOrGroupOwnerTrustLevelMajor;
        this.identityServer = identityServer;
        this.serializedGroupIdentifier = serializedGroupIdentifier;
    }


    private ContactTrustOrigin(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.rowId = res.getLong(ROW_ID);
        this.identityManagerSession = identityManagerSession;
        try {
            this.contactIdentity = Identity.of(res.getBytes(CONTACT_IDENTITY));
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
            byte[] mediatorBytes = res.getBytes(MEDIATOR_OR_GROUP_OWNER_IDENTITY);
            if (mediatorBytes == null) {
                this.mediatorOrGroupOwnerIdentity = null;
            } else {
                mediatorOrGroupOwnerIdentity = Identity.of(mediatorBytes);
            }
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.timestamp = res.getLong(TIMESTAMP);
        this.trustType = res.getInt(TRUST_TYPE);
        this.mediatorOrGroupOwnerTrustLevelMajor = res.getInt(MEDIATOR_OR_GROUP_OWNER_TRUST_LEVEL_MAJOR);
        if (res.wasNull()) {
            this.mediatorOrGroupOwnerTrustLevelMajor = null;
        }
        this.identityServer = res.getString(IDENTITY_SERVER);
        this.serializedGroupIdentifier = res.getBytes(SERIALIZED_GROUP_IDENTIFIER);
    }

    // endregion

    // region getters

    public static ContactTrustOrigin[] getAll(IdentityManagerSession identityManagerSession, Identity contactIdentity, Identity ownedIdentity) {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + CONTACT_IDENTITY + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?" +
                " ORDER BY " + TIMESTAMP + " DESC;")) {
            statement.setBytes(1, contactIdentity.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                List<ContactTrustOrigin> list = new ArrayList<>();
                while (res.next()) {
                    list.add(new ContactTrustOrigin(identityManagerSession, res));
                }
                return list.toArray(new ContactTrustOrigin[0]);
            }
        } catch (SQLException e) {
            return new ContactTrustOrigin[0];
        }
    }

    // endregion

    // region hooks

    @Override
    public void wasCommitted() {

    }

    // endregion

    // region backup

    public static Pojo_0[] backupAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) {
        ContactTrustOrigin[] contactTrustOrigins = getAll(identityManagerSession, contactIdentity, ownedIdentity);
        Pojo_0[] pojos = new Pojo_0[contactTrustOrigins.length];
        for (int i=0; i<contactTrustOrigins.length; i++) {
            pojos[i] = contactTrustOrigins[i].backup();
        }
        return pojos;
    }

    public static void restoreAll(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity, Pojo_0[] pojos) throws SQLException {
        if (pojos == null) {
            return;
        }
        for (Pojo_0 pojo: pojos) {
            restore(identityManagerSession, ownedIdentity, contactIdentity, pojo);
        }
    }

    private Pojo_0 backup() {
        Pojo_0 pojo = new Pojo_0();
        pojo.timestamp = timestamp;
        pojo.writeTrust_type(trustType);
        if (mediatorOrGroupOwnerIdentity != null) {
            pojo.mediator_or_group_owner_identity = mediatorOrGroupOwnerIdentity.getBytes();
        }
        if (mediatorOrGroupOwnerTrustLevelMajor != null) {
            pojo.mediator_or_group_owner_trust_level_major = mediatorOrGroupOwnerTrustLevelMajor;
        }
        pojo.identity_server = identityServer;
        pojo.raw_obv_group_v2_identifier = serializedGroupIdentifier;
        return pojo;
    }

    private static void restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity, Pojo_0 pojo) throws SQLException {
        Identity mediatorOrGroupOwnerIdentity = null;
        try {
            if (pojo.mediator_or_group_owner_identity != null) {
                mediatorOrGroupOwnerIdentity = Identity.of(pojo.mediator_or_group_owner_identity);
            }
        } catch (DecodingException e) {
            Logger.x(e);
        }
        ContactTrustOrigin contactTrustOrigin = new ContactTrustOrigin(identityManagerSession, contactIdentity, ownedIdentity, pojo.timestamp, pojo.readTrust_type(), mediatorOrGroupOwnerIdentity, pojo.mediator_or_group_owner_trust_level_major, pojo.identity_server, pojo.raw_obv_group_v2_identifier);
        contactTrustOrigin.insert();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pojo_0 {
        private static final int TYPE_DIRECT = 0;
        private static final int TYPE_GROUP = 1;
        private static final int TYPE_INTRODUCTION = 2;
        private static final int TYPE_IDENTITY_SERVER = 3;
        private static final int TYPE_SERVER_GROUP_V2 = 4;

        public long timestamp;
        private int trust_type;
        public byte[] mediator_or_group_owner_identity;
        public Integer mediator_or_group_owner_trust_level_major;
        public String identity_server;
        public byte[] raw_obv_group_v2_identifier;

        public Pojo_0() {
        }

        public int getTrust_type() {
            return trust_type;
        }

        public void setTrust_type(int trust_type) {
            this.trust_type = trust_type;
        }

        @JsonIgnore
        public int readTrust_type() {
            switch (trust_type) {
                case TYPE_GROUP:
                    return TRUST_TYPE_GROUP;
                case TYPE_INTRODUCTION:
                    return TRUST_TYPE_INTRODUCTION;
                case TYPE_IDENTITY_SERVER:
                    return TRUST_TYPE_IDENTITY_SERVER;
                case TYPE_SERVER_GROUP_V2:
                    return TRUST_TYPE_SERVER_GROUP_V2;
                case TYPE_DIRECT:
                default:
                    return TRUST_TYPE_DIRECT;
            }
        }

        @JsonIgnore
        public void writeTrust_type(int trust_type) {
            switch (trust_type) {
                case TRUST_TYPE_GROUP:
                    this.trust_type = TYPE_GROUP;
                    break;
                case TRUST_TYPE_INTRODUCTION:
                    this.trust_type = TYPE_INTRODUCTION;
                    break;
                case TRUST_TYPE_IDENTITY_SERVER:
                    this.trust_type = TYPE_IDENTITY_SERVER;
                    break;
                case TRUST_TYPE_SERVER_GROUP_V2:
                    this.trust_type = TYPE_SERVER_GROUP_V2;
                    break;
                case TRUST_TYPE_DIRECT:
                default:
                    this.trust_type = TYPE_DIRECT;
                    break;
            }
        }
    }

    // endregion
}
