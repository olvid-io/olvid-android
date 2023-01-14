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

package io.olvid.messenger.databases.tasks;

import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;

public class DeleteAllHiddenProfilesAndLimitedVisibilityMessages implements Runnable {
    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();

        List<OwnedIdentity> hiddenOwnedIdentities = db.ownedIdentityDao().getAllHidden();

        SettingsActivity.clearEmergencyPIN();

        for (OwnedIdentity hiddenOwnedIdentity: hiddenOwnedIdentities) {
            try {
                AppSingleton.getEngine().deleteOwnedIdentity(hiddenOwnedIdentity.bytesOwnedIdentity);
            } catch (Exception ignored) { }
            new DeleteOwnedIdentityAndEverythingRelatedToItTask(hiddenOwnedIdentity.bytesOwnedIdentity).run();
        }

        new DeleteAllLimitedVisibilityMessages().run();
    }
}
