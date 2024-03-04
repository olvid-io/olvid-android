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

package io.olvid.engine.networkfetch.datatypes;


import java.util.List;

import io.olvid.engine.engine.types.JsonOsmStyle;
import io.olvid.engine.networkfetch.coordinators.WellKnownCoordinator;

public interface WellKnownCacheDelegate {
    String getWsUrl(String server) throws WellKnownCoordinator.NotCachedException;
    List<String> getTurnUrls(String server) throws WellKnownCoordinator.NotCachedException;
    List<JsonOsmStyle> getOsmStyles(String server) throws WellKnownCoordinator.NotCachedException;
    String getAddressUrl(String server) throws WellKnownCoordinator.NotCachedException;
}
