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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactIdentity;
import io.olvid.engine.identity.databases.ContactIdentityDetails;
import io.olvid.engine.identity.databases.ContactTrustOrigin;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String TRUSTED_DETAILS = "trusted_details";
    public static final String PUBLISHED_DETAILS= "published_details";
    public static final String ONE_TO_ONE= "one_to_one";
    public static final String REVOKED = "revoked";
    public static final String FORCEFULLY_TRUSTED = "forcefully_trusted";
    public static final String TRUST_LEVEL = "trust_level";
    public static final String TRUST_ORIGINS = "trust_origins";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(TRUSTED_DETAILS, PUBLISHED_DETAILS, ONE_TO_ONE, REVOKED, FORCEFULLY_TRUSTED, TRUST_LEVEL, TRUST_ORIGINS));

    public IdentityDetailsSyncSnapshot trusted_details;
    public IdentityDetailsSyncSnapshot published_details; // null if equal to trusted details
    public Boolean one_to_one;
    public Boolean revoked;
    public Boolean forcefully_trusted;
    public String trust_level; // only used for backup/transfer, not taken into account when comparing for synchronization
    public List<TrustOrigin> trust_origins; // only used for backup/transfer, not taken into account when comparing for synchronization
    public HashSet<String> domain;



    public static ContactSyncSnapshot of(IdentityManagerSession identityManagerSession, ContactIdentity contact) throws SQLException {
        ContactSyncSnapshot contactSyncSnapshot = new ContactSyncSnapshot();

        ContactIdentityDetails trustedDetails = contact.getTrustedDetails();
        contactSyncSnapshot.trusted_details = IdentityDetailsSyncSnapshot.of(identityManagerSession, trustedDetails);

        if (contact.getTrustedDetailsVersion() != contact.getPublishedDetailsVersion()) {
            ContactIdentityDetails publishedDetails = contact.getPublishedDetails();
            contactSyncSnapshot.published_details = IdentityDetailsSyncSnapshot.of(identityManagerSession, publishedDetails);
        }

        contactSyncSnapshot.one_to_one = contact.isOneToOne() ? Boolean.TRUE : (contact.isNotOneToOne() ? Boolean.FALSE : null);

        contactSyncSnapshot.revoked = contact.isRevokedAsCompromised() ? true : null;

        contactSyncSnapshot.forcefully_trusted = contact.isForcefullyTrustedByUser() ? true : null;

        contactSyncSnapshot.trust_level = contact.getTrustLevel().toString();

        contactSyncSnapshot.trust_origins = new ArrayList<>();
        for (ContactTrustOrigin contactTrustOrigin : ContactTrustOrigin.getAll(identityManagerSession, contact.getContactIdentity(), contact.getOwnedIdentity())) {
            contactSyncSnapshot.trust_origins.add(TrustOrigin.of(contactTrustOrigin));
        }

        contactSyncSnapshot.domain = DEFAULT_DOMAIN;
        return contactSyncSnapshot;
    }

    @JsonIgnore
    public ContactIdentity restore(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (!domain.contains(TRUSTED_DETAILS)) {
            Logger.e("Trying to restore an incomplete ContactSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        // restore the trusted details
        ContactIdentityDetails trustedDetails = trusted_details.restoreContact(identityManagerSession, ownedIdentity, contactIdentity);
        ContactIdentityDetails publishedDetails;
        if (domain.contains(PUBLISHED_DETAILS) && published_details != null && !Objects.equals(trusted_details.version, published_details.version)) {
            publishedDetails = published_details.restoreContact(identityManagerSession, ownedIdentity, contactIdentity);
        } else {
            publishedDetails = null;
        }

        TrustLevel trustLevel = (domain.contains(TRUST_LEVEL) && trust_level != null) ? TrustLevel.of(trust_level) : new TrustLevel(0, 0);
        int oneToOne = (domain.contains(ONE_TO_ONE) && one_to_one != null) ? (one_to_one ? ContactIdentity.ONE_TO_ONE_STATUS_TRUE : ContactIdentity.ONE_TO_ONE_STATUS_FALSE) : ContactIdentity.ONE_TO_ONE_STATUS_UNKNOWN;

        ContactIdentity contactIdentityObject = new ContactIdentity(identityManagerSession, contactIdentity, ownedIdentity, trustedDetails.getVersion(), trustLevel, oneToOne);
        if (publishedDetails != null) {
            contactIdentityObject.publishedDetailsVersion = publishedDetails.getVersion();
        }
        contactIdentityObject.revokedAsCompromised = domain.contains(REVOKED) && revoked != null && revoked;
        contactIdentityObject.forcefullyTrustedByUser = domain.contains(FORCEFULLY_TRUSTED) && forcefully_trusted != null && forcefully_trusted;
        contactIdentityObject.insert();

        // check for keycloak badge
        JsonKeycloakUserDetails jsonKeycloakUserDetails = identityManagerSession.identityDelegate.verifyKeycloakIdentitySignature(identityManagerSession.session, ownedIdentity, trustedDetails.getJsonIdentityDetailsWithVersionAndPhoto().getIdentityDetails().getSignedUserDetails());
        if (jsonKeycloakUserDetails != null) {
            contactIdentityObject.setCertifiedByOwnKeycloak(true, trustedDetails.getSerializedJsonDetails());
        }

        // restore trust origin
        if (domain.contains(TRUST_ORIGINS) && trust_origins != null) {
            for (TrustOrigin trustOrigin : trust_origins) {
                Identity mediatorOrGroupOwnerIdentity = null;
                try {
                    if (trustOrigin.mediator_or_group_owner_identity != null) {
                        mediatorOrGroupOwnerIdentity = Identity.of(trustOrigin.mediator_or_group_owner_identity);
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
                int trustType;
                switch (TrustOrigin.TrustType.fromIntValue(trustOrigin.trust_type)) {
                    case TYPE_DIRECT:
                        trustType = ContactTrustOrigin.TRUST_TYPE_DIRECT;
                        break;
                    case TYPE_GROUP:
                        trustType = ContactTrustOrigin.TRUST_TYPE_GROUP;
                        break;
                    case TYPE_INTRODUCTION:
                        trustType = ContactTrustOrigin.TRUST_TYPE_INTRODUCTION;
                        break;
                    case TYPE_KEYCLOAK:
                        trustType = ContactTrustOrigin.TRUST_TYPE_IDENTITY_SERVER;
                        break;
                    case TYPE_SERVER_GROUP_V2:
                        trustType = ContactTrustOrigin.TRUST_TYPE_SERVER_GROUP_V2;
                        break;
                    default:
                        // ignore unknown trust types
                        continue;
                }
                ContactTrustOrigin contactTrustOrigin = new ContactTrustOrigin(identityManagerSession, contactIdentity, ownedIdentity, trustOrigin.timestamp, trustType, mediatorOrGroupOwnerIdentity, 0, trustOrigin.identity_server, trustOrigin.raw_obv_group_v2_identifier);
                contactTrustOrigin.insert();
            }
        }

        return contactIdentityObject;
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        // TODO areContentsTheSame
        return false;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrustOrigin {
        enum TrustType {
            TYPE_DIRECT(0),
            TYPE_GROUP(1),
            TYPE_INTRODUCTION(2),
            TYPE_KEYCLOAK(3),
            TYPE_SERVER_GROUP_V2(4);
            private static final Map<Integer, TrustType> valueMap = new HashMap<>();

            static {
                for (TrustType trustType : values()) {
                    valueMap.put(trustType.value, trustType);
                }
            }

            public final int value;

            TrustType(int value) {
                this.value = value;
            }

            static TrustType fromIntValue(int value) {
                return valueMap.get(value);
            }
        }


        public long timestamp;
        public int trust_type;
        public byte[] mediator_or_group_owner_identity;
        public String identity_server;
        public byte[] raw_obv_group_v2_identifier;

        private static TrustOrigin of(ContactTrustOrigin contactTrustOrigin) {
            io.olvid.engine.datatypes.containers.TrustOrigin trustOrigin = contactTrustOrigin.getTrustOrigin();

            TrustOrigin to = new TrustOrigin();
            to.timestamp = trustOrigin.getTimestamp();
            switch (trustOrigin.getType()) {
                case DIRECT: {
                    to.trust_type = TrustType.TYPE_DIRECT.value;
                    break;
                }
                case INTRODUCTION: {
                    to.trust_type = TrustType.TYPE_INTRODUCTION.value;
                    to.mediator_or_group_owner_identity = trustOrigin.getMediatorOrGroupOwnerIdentity().getBytes();
                    break;
                }
                case GROUP: {
                    to.trust_type = TrustType.TYPE_GROUP.value;
                    to.mediator_or_group_owner_identity = trustOrigin.getMediatorOrGroupOwnerIdentity().getBytes();
                    break;
                }
                case KEYCLOAK: {
                    to.trust_type = TrustType.TYPE_KEYCLOAK.value;
                    to.identity_server = trustOrigin.getKeycloakServer();
                    break;
                }
                case SERVER_GROUP_V2: {
                    to.trust_type = TrustType.TYPE_SERVER_GROUP_V2.value;
                    to.raw_obv_group_v2_identifier = trustOrigin.getGroupIdentifier().getBytes();
                    break;
                }
            }

            return to;
        }
    }
}
