/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.discussion.location;

import io.olvid.messenger.settings.SettingsActivity;

public class MapFragmentProvider {
    public static Class<? extends MapViewAbstractFragment> getMapFragmentClassForProvider(SettingsActivity.LocationIntegrationEnum integration) {
        switch (integration) {
            case OSM:
            case CUSTOM_OSM:
                return MapViewMapLibreFragment.class;
            case MAPS:
                return MapViewGoogleMapsFragment.class;
            case BASIC:
            case NONE:
            default:
                return null;
        }
    }
}
