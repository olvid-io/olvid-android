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

package io.olvid.engine.backup.datatypes;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;

import io.olvid.engine.datatypes.Session;
import io.olvid.engine.metamanager.NotificationPostingDelegate;

public class BackupManagerSession implements AutoCloseable {
    public final Session session;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final ObjectMapper jsonObjectMapper;

    public BackupManagerSession(Session session, NotificationPostingDelegate notificationPostingDelegate, ObjectMapper jsonObjectMapper) {
        this.session = session;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
