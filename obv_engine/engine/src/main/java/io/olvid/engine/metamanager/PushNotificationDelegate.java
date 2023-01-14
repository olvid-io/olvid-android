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

package io.olvid.engine.metamanager;


import java.sql.SQLException;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;

public interface PushNotificationDelegate {

    void registerPushNotificationIfConfigurationChanged(Session session, Identity identity, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters) throws SQLException;
    void unregisterPushNotification(Session session, Identity identity) throws SQLException;
    void processAndroidPushNotification(String maskingUidString);
    void forceRegisterPushNotification(Identity ownedIdentity);
}
