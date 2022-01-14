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

package io.olvid.engine.engine.types;

public class ObvBackupKeyVerificationOutput {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_TOO_SHORT = 1;
    public static final int STATUS_TOO_LONG = 2;
    public static final int STATUS_BAD_KEY = 3;

    public final int verificationStatus;

    public ObvBackupKeyVerificationOutput(int verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
