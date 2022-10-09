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

package io.olvid.messenger.databases.tasks;


import java.io.File;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.notifications.AndroidNotificationManager;

public class DeleteOwnedIdentityAndEverythingRelatedToItTask implements Runnable {
    final byte[] bytesOwnedIdentity;

    public DeleteOwnedIdentityAndEverythingRelatedToItTask(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();

        //////////////
        // deleting an OwnedIdentity will cascade delete EVERYTHING related to it!
        OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(bytesOwnedIdentity);
        if (ownedIdentity != null) {
            db.ownedIdentityDao().delete(ownedIdentity);
        }

        //////////////
        // only orphan Fyles may remain --> clean them up
        List<Fyle> strayFyles = db.fyleDao().getStray();
        for (Fyle strayFyle: strayFyles) {
            if (strayFyle.sha256 != null) {
                Logger.i("Cleaning stray Fyle with sha256 " + Logger.toHexString(strayFyle.sha256));
                Fyle.acquireLock(strayFyle.sha256);
                db.fyleDao().delete(strayFyle);
                if (strayFyle.filePath != null) {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        new File(App.absolutePathFromRelative(strayFyle.filePath)).delete();
                    } catch (Exception ignored) {}
                }
                Fyle.releaseLock(strayFyle.sha256);
            } else {
                Logger.i("Cleaning stray Fyle with NULL sha256");
                db.fyleDao().delete(strayFyle);
            }
        }

        AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(bytesOwnedIdentity);
        AppSingleton.getInstance().ownedIdentityWasDeleted(bytesOwnedIdentity);
    }
}
