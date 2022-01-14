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
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;


public class SizeAwareCardView extends CardView {
    private SizeChangeListener sizeChangeListener = null;

    public SizeAwareCardView(@NonNull Context context) {
        super(context);
    }

    public SizeAwareCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SizeAwareCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (sizeChangeListener != null) {
            sizeChangeListener.onSizeChange(w, h, oldw, oldh);
        }
    }

    public void setSizeChangeListener(SizeChangeListener sizeChangeListener) {
        this.sizeChangeListener = sizeChangeListener;
    }

    public void removeSizeChangeListener() {
        this.sizeChangeListener = null;
    }

    public interface SizeChangeListener {
        void onSizeChange(int w, int h, int oldw, int oldh);
    }
}
