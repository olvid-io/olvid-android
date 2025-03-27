/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LocationShareQuality;

public class ShareLocationPopupMenu extends PopupMenu {
    private long[] durationArray;
    private LocationShareQuality[] qualityArray;
    private CharSequence[] shortStringArray;
    private CharSequence[] longStringArray;

    public static ShareLocationPopupMenu getDurationPopUpMenu(Context context, View anchor) {
        ShareLocationPopupMenu popupMenu = new ShareLocationPopupMenu(context, anchor);

        // convert duration string array to int array
        String[] stringDurationArray = context.getResources().getStringArray(R.array.share_location_duration_values);
        popupMenu.durationArray = new long[stringDurationArray.length];
        for (int i = 0; i < stringDurationArray.length; i++) {
            popupMenu.durationArray[i] = Long.parseLong(stringDurationArray[i]);
        }
        popupMenu.shortStringArray = context.getResources().getTextArray(R.array.share_location_duration_short_strings);
        popupMenu.longStringArray = context.getResources().getTextArray(R.array.share_location_duration_long_strings);

        for (int i = 0 ; i < popupMenu.durationArray.length; i++) {
            popupMenu.getMenu().add(0, i, i, popupMenu.shortStringArray[i]);
        }
        return popupMenu;
    }

    public static ShareLocationPopupMenu getQualityPopUpMenu(Context context, View anchor) {
        ShareLocationPopupMenu popupMenu = new ShareLocationPopupMenu(context, anchor);

        // convert duration string array to int array
        String[] stringQualityArray = context.getResources().getStringArray(R.array.share_location_quality_values);
        popupMenu.qualityArray = new LocationShareQuality[stringQualityArray.length];
        for (int i = 0; i < stringQualityArray.length; i++) {
            popupMenu.qualityArray[i] = LocationShareQuality.fromValue(Integer.parseInt(stringQualityArray[i]));
        }
        popupMenu.shortStringArray = context.getResources().getTextArray(R.array.share_location_quality_short_strings);
        popupMenu.longStringArray = context.getResources().getTextArray(R.array.share_location_quality_long_strings);

        for (int i = 0 ; i < popupMenu.qualityArray.length; i++) {
            popupMenu.getMenu().add(0, i, i, popupMenu.shortStringArray[i]);
        }
        return popupMenu;
    }

    private ShareLocationPopupMenu(Context context, View anchor) {
        super(context, anchor);
    }

    public Long getItemDuration(MenuItem item) {
        if (item.getItemId() < 0 || item.getItemId() >= durationArray.length) {
            return null;
        }
        if (durationArray[item.getItemId()] == -1) {
            return null;
        }
        return durationArray[item.getItemId()];
    }

    public LocationShareQuality getItemQuality(MenuItem item) {
        if (item.getItemId() < 0 || item.getItemId() >= qualityArray.length) {
            return null;
        }
        return qualityArray[item.getItemId()];
    }

    public CharSequence getItemLongString(MenuItem item) {
        if (item.getItemId() < 0 || item.getItemId() >= longStringArray.length) {
            return null;
        }
        return longStringArray[item.getItemId()];
    }
}
