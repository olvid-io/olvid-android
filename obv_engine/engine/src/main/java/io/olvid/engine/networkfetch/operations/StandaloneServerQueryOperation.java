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

package io.olvid.engine.networkfetch.operations;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;

public class StandaloneServerQueryOperation extends Operation {
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_UNSUPPORTED_SERVER_QUERY_TYPE = 2;
    public static final int RFC_INVALID_SERVER_SESSION = 3;
    public static final int RFC_INVALID_API_KEY = 4;

    private final ServerQuery serverQuery;
    private Encoded serverResponse; // will be set if the operation finishes normally

    public Encoded getServerResponse() {
        return serverResponse;
    }

    public StandaloneServerQueryOperation(ServerQuery serverQuery) {
        this.serverQuery = serverQuery;
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try {
            ServerQueryServerMethod serverMethod;
            switch (serverQuery.getType().getId()) {
                case ServerQuery.Type.OWNED_DEVICE_DISCOVERY_QUERY_ID: {
                    serverMethod = new OwnedDeviceDiscoveryServerMethod(serverQuery.getOwnedIdentity());
                    break;
                }
                case ServerQuery.Type.REGISTER_API_KEY_QUERY_ID: {
                    serverMethod = new RegisterApiKeyServerMethod(serverQuery.getOwnedIdentity(), serverQuery.getType().getNonce(), serverQuery.getType().getDataUrl());
                    break;
                }
                default: {
                    cancel(RFC_UNSUPPORTED_SERVER_QUERY_TYPE);
                    return;
                }
            }

            byte returnStatus = serverMethod.execute(true);
            Logger.d("?? Server query return status (after parse): " + returnStatus);

            switch (returnStatus) {
                case ServerMethod.OK: {
                    serverResponse = serverMethod.getServerResponse();
                    finished = true;
                    return;
                }
                case ServerMethod.INVALID_SESSION: {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                case ServerMethod.INVALID_API_KEY: {
                    cancel(RFC_INVALID_API_KEY);
                    return;
                }
                default: {
                    cancel(RFC_NETWORK_ERROR);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (finished) {
                setFinished();
            } else {
                if (hasNoReasonForCancel()) {
                    cancel(null);
                }
                processCancel();
            }
        }
    }

    @Override
    public void doCancel() {
        // nothing to do here
    }
}
