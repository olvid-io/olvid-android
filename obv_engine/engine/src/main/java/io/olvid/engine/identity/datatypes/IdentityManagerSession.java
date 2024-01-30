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

package io.olvid.engine.identity.datatypes;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;

public class IdentityManagerSession implements AutoCloseable {
    public final Session session;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final IdentityDelegate identityDelegate;
    public final String engineBaseDirectory;
    public final ObjectMapper jsonObjectMapper;
    public final PRNGService prng;

    public IdentityManagerSession(Session session, NotificationPostingDelegate notificationPostingDelegate, IdentityDelegate identityDelegate, String engineBaseDirectory, ObjectMapper jsonObjectMapper, PRNGService prng) {
        this.session = session;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.identityDelegate = identityDelegate;
        this.engineBaseDirectory = engineBaseDirectory;
        this.jsonObjectMapper = jsonObjectMapper;
        this.prng = prng;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
