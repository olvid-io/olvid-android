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

package io.olvid.engine.engine.types;

import java.util.HashMap;
import java.util.Objects;

public abstract class SimpleEngineNotificationListener implements EngineNotificationListener {
    private final String notificationName;
    private Long registrationNumber = null;

    public SimpleEngineNotificationListener(String notificationName) {
        this.notificationName = notificationName;
    }

    public abstract void callback(HashMap<String, Object> userInfo);

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (Objects.equals(notificationName, this.notificationName)) {
            callback(userInfo);
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return registrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return registrationNumber != null;
    }
}
