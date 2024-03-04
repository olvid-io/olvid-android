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

package io.olvid.engine.metamanager;


import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.containers.AttachmentKeyAndMetadata;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.engine.types.JsonOsmStyle;

public interface NetworkFetchDelegate {
    void downloadMessages(Identity ownedIdentity, UID deviceUid);
    DecryptedApplicationMessage getMessage(Identity ownedIdentity, UID messageUid);
    boolean canAllAttachmentsBeDownloaded(Identity ownedIdentity, UID messageUid) throws SQLException;
    void setAttachmentKeyAndMetadataAndMessagePayload(Session session, Identity ownedIdentity, UID messageUid, Identity remoteIdentity, AttachmentKeyAndMetadata[] attachmentKeyAndMetadata, byte[] messagePayload, AuthEncKey extendedPayloadKey) throws Exception;
    void downloadAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber, int priorityCategory);
    void pauseDownloadAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber);
    ReceivedAttachment getAttachment(Identity ownedIdentity, UID messageUid, int attachmentNumber);
    ReceivedAttachment[] getMessageAttachments(Identity ownedIdentity, UID messageUid);
    boolean isInboxAttachmentReceived(Session session, Identity ownedIdentity, UID uid, int engineNumber) throws Exception;

    void deleteMessageAndAttachments(Session session, Identity ownedIdentity, UID messageUid);
    void deleteMessage(Session session, Identity ownedIdentity, UID messageUid);

    void deleteAttachment(Session session, Identity ownedIdentity, UID messageUid, int attachmentNumber) throws SQLException;

    void resendAllDownloadedAttachmentNotifications() throws Exception;

    void createPendingServerQuery(Session session, ServerQuery serverQuery) throws Exception;
    void deleteExistingServerSession(Session session, Identity identity, boolean createNewSession);

    void connectWebsockets(boolean aggressiveReconnectMode, String os, String osVersion, int appBuild, String appVersion);
    void disconnectWebsockets();
    void pingWebsocket (Identity ownedIdentity);
    byte[] getServerAuthenticationToken(Identity ownedIdentity);

    void retryScheduledNetworkTasks();
    void getTurnCredentials(Identity ownedIdentity, UUID callUuid, String username1, String username2);
    void queryApiKeyStatus(Identity ownedIdentity, UUID apiKey);
    void queryFreeTrial(Identity ownedIdentity);
    void startFreeTrial(Identity ownedIdentity);
    void verifyReceipt(Identity ownedIdentity, String storeToken);
    void queryServerWellKnown(String server);
    List<JsonOsmStyle> getOsmStyles(String server);
    String getAddressServerUrl(String server);
}
