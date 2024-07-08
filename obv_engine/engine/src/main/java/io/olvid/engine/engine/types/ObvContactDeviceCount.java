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


public class ObvContactDeviceCount {
    public final int deviceCount;
    public final int establishedChannelCount;
    public final int preKeyCount; // this only counts device with a preKey that do not have an established channel

    public ObvContactDeviceCount(int deviceCount, int establishedChannelCount, int preKeyCount) {
        this.deviceCount = deviceCount;
        this.establishedChannelCount = establishedChannelCount;
        this.preKeyCount = preKeyCount;
    }
}
