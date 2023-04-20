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

package io.olvid.messenger.customClasses;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.olvid.messenger.R;

public class SelectableArrayAdapter<T> extends ArrayAdapter<T> {
    @Nullable private final Integer initiallySelectedEntry;

    public SelectableArrayAdapter(@NonNull Context context, @Nullable Integer initiallySelectedEntry, @NonNull T[] objects) {
        super(context, R.layout.dialog_singlechoice, objects);
        this.initiallySelectedEntry = initiallySelectedEntry;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (initiallySelectedEntry != null && initiallySelectedEntry == position) {
            TextView textView = view.findViewById(android.R.id.text1);
            if (textView != null) {
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.radio_circle_selected, 0, 0, 0);
            }
        } else if (convertView != null) {
            TextView textView = view.findViewById(android.R.id.text1);
            if (textView != null) {
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.radio_circle, 0, 0, 0);
            }
        }
        return view;
    }
}
