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

package io.olvid.engine.networkfetch.operations;


import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.ServerSession;

public class QueryApiKeyStatusOperation extends Operation {
    private final Identity ownedIdentity;
    private final UUID apiKey;

    private final SSLSocketFactory sslSocketFactory;
    private ServerSession.ApiKeyStatus apiKeyStatus;
    private List<ServerSession.Permission> permissions;
    private long apiKeyExpirationTimestamp;

    public QueryApiKeyStatusOperation(SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UUID apiKey, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(null, onFinishCallback, onCancelCallback);
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.apiKey = apiKey;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UUID getApiKey() {
        return apiKey;
    }

    public ServerSession.ApiKeyStatus getApiKeyStatus() {
        return apiKeyStatus;
    }

    public List<ServerSession.Permission> getPermissions() {
        return permissions;
    }

    public long getApiKeyExpirationTimestamp() {
        return apiKeyExpirationTimestamp;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        QueryApiKeyStatusServerMethod serverMethod = new QueryApiKeyStatusServerMethod(ownedIdentity, apiKey);
        serverMethod.setSslSocketFactory(sslSocketFactory);

        byte returnStatus = serverMethod.execute(true);

        if (returnStatus == ServerMethod.OK) {
            apiKeyStatus = ServerSession.deserializeApiKeyStatus(serverMethod.getApiKeyStatus());
            permissions = ServerSession.deserializePermissions(serverMethod.getPermissions());
            apiKeyExpirationTimestamp = serverMethod.getApiKeyExpiration();
            setFinished();
        } else {
            cancel(null);
            processCancel();
        }
    }
}

class QueryApiKeyStatusServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/queryApiKeyStatus";

    private final String server;
    private final Identity ownedIdentity;
    private final UUID apiKey;

    private int apiKeyStatus = -1;
    private long permissions = 0;
    private long apiKeyExpiration = 0;

    public QueryApiKeyStatusServerMethod(Identity ownedIdentity, UUID apiKey) {
        this.ownedIdentity = ownedIdentity;
        this.server = ownedIdentity.getServer();
        this.apiKey = apiKey;
    }

    public int getApiKeyStatus() {
        return apiKeyStatus;
    }

    public long getPermissions() {
        return permissions;
    }

    public long getApiKeyExpiration() {
        return apiKeyExpiration;
    }

    @Override
    protected String getServer() {
        return server;
    }

    @Override
    protected String getServerMethod() {
        return SERVER_METHOD_PATH;
    }

    @Override
    protected byte[] getDataToSend() {
        return Encoded.of(new Encoded[]{
                Encoded.of(ownedIdentity),
                Encoded.of(apiKey),
        }).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            try {
                this.apiKeyStatus = (int) receivedData[0].decodeLong();
                this.permissions = receivedData[1].decodeLong();
                this.apiKeyExpiration = receivedData[2].decodeLong();
            } catch (DecodingException e) {
                Logger.x(e);
                returnStatus = ServerMethod.GENERAL_ERROR;
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }
}