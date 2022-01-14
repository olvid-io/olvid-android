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

package io.olvid.messenger.customClasses;


import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

public class MultilineSummaryPreferenceCategory extends PreferenceCategory {
    public MultilineSummaryPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MultilineSummaryPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MultilineSummaryPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultilineSummaryPreferenceCategory(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        try {
            TextView summary = (TextView) holder.findViewById(android.R.id.summary);
            if (summary != null) {
                summary.setSingleLine(false);
                summary.setMaxLines(Integer.MAX_VALUE);
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                summary.setTypeface(summary.getTypeface(), Typeface.ITALIC);
            }
        } catch (Exception e) {
            // nothing to do, summary will be on one line...
        }
    }
}
