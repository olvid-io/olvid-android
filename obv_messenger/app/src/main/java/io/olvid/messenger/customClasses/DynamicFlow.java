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
import android.util.AttributeSet;
import android.view.View;

import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;

public class DynamicFlow extends Flow {
    private ConstraintLayout parent = null;

    public DynamicFlow(Context context) {
        super(context);
    }

    public DynamicFlow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DynamicFlow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setVisibility(int visibility) {
        if (parent == null) {
            parent = (ConstraintLayout) getParent();
        }
        parent.setVisibility(visibility);
    }

    @Override
    public void addView(View newView) {
        if (parent == null) {
            parent = (ConstraintLayout) getParent();
        }
        parent.addView(newView);
        super.addView(newView);
    }

    public void removeAllViews() {
        if (parent == null) {
            parent = (ConstraintLayout) getParent();
        }
        for (int id : getReferencedIds()) {
            View view = parent.getViewById(id);
            removeView(view);
            parent.removeView(view);
        }
    }
}