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

package io.olvid.engine.engine.types.sync;

public class ObvSyncDiff {
    // only used to notify the app, needs to be encodable to send to other device
    public static final int TYPE_SETTING_AUTO_JOIN_GROUPS = 0;
    public static final int TYPE_SETTING_SEND_READ_RECEIPT = 1;

    private final int diffType;
    private boolean resolutionInProgress;
    private final Boolean localBoolean;
    private final Boolean otherBoolean;
    private final String localString;
    private final String otherString;

    public ObvSyncDiff(int diffType, Boolean localBoolean, Boolean otherBoolean, String localString, String otherString) {
        this.diffType = diffType;
        this.resolutionInProgress = false;
        this.localBoolean = localBoolean;
        this.otherBoolean = otherBoolean;
        this.localString = localString;
        this.otherString = otherString;
    }

    public void markResolutionInProgress() {
        this.resolutionInProgress = true;
    }

    public static ObvSyncDiff createSettingAutoJoinGroups(String localValue, String otherValue) {
        return new ObvSyncDiff(TYPE_SETTING_AUTO_JOIN_GROUPS, null, null, localValue, otherValue);
    }
    public static ObvSyncDiff createSettingSendReadReceipt(boolean localValue, boolean otherValue) {
        return new ObvSyncDiff(TYPE_SETTING_SEND_READ_RECEIPT, localValue, otherValue, null, null);
    }
}
