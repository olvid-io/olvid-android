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

package io.olvid.engine.engine.types;

public class ObvBackupKeyInformation {
    public final long keyGenerationTimestamp;
    public final long lastSuccessfulKeyVerificationTimestamp;
    public final int successfulVerificationCount;
    public final long lastBackupExport;
    public final long lastBackupUpload;

    public ObvBackupKeyInformation(long keyGenerationTimestamp, long lastSuccessfulKeyVerificationTimestamp, int successfulVerificationCount, long lastBackupExport, long lastBackupUpload) {
        this.keyGenerationTimestamp = keyGenerationTimestamp;
        this.lastSuccessfulKeyVerificationTimestamp = lastSuccessfulKeyVerificationTimestamp;
        this.successfulVerificationCount = successfulVerificationCount;
        this.lastBackupExport = lastBackupExport;
        this.lastBackupUpload = lastBackupUpload;
    }
}
