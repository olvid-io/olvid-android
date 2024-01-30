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

package io.olvid.messenger.customClasses;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import io.olvid.messenger.R;

public class ItemDecorationSimpleDivider extends RecyclerView.ItemDecoration {
    private final int dividerHeight;
    private final int marginLeft;
    private final int marginRight;
    private final int backgroundColor;
    private final int foregroundColor;

    public ItemDecorationSimpleDivider(Context context, int dpMarginLeft, int dpMarginRight) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        dividerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
        marginLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpMarginLeft, metrics);
        marginRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpMarginRight, metrics);
        backgroundColor = ContextCompat.getColor(context, R.color.almostWhite);
        foregroundColor = ContextCompat.getColor(context, R.color.lightGrey);
    }

    public ItemDecorationSimpleDivider(Context context, int dpMarginLeft, int dpMarginRight, @ColorRes int backgroundColorResourceId) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        dividerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
        marginLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpMarginLeft, metrics);
        marginRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpMarginRight, metrics);
        backgroundColor = ContextCompat.getColor(context, backgroundColorResourceId);
        foregroundColor = ContextCompat.getColor(context, R.color.lightGrey);
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        for (int i=0; i<childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == 0) {
                continue;
            }
            Rect childRect = new Rect();
            parent.getDecoratedBoundsWithMargins(child, childRect);
            childRect.top += child.getTranslationY();
            childRect.bottom += child.getTranslationY();
            int alpha = (int) (child.getAlpha() * 255);

            int alphaBack = (backgroundColor & 0x00ffffff) | (alpha << 24);
            int alphaFore = (foregroundColor & 0x00ffffff) | (alpha << 24);
            canvas.save();
            canvas.clipRect(childRect.left, childRect.top, childRect.right, childRect.top + dividerHeight);
            canvas.drawColor(alphaBack);
            canvas.clipRect(childRect.left + marginLeft, childRect.top, childRect.right - marginRight, childRect.top + dividerHeight);
            canvas.drawColor(alphaFore);
            canvas.restore();
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        int position = parent.getChildAdapterPosition(view);
        if (position == 0) {
            return;
        }
        outRect.top = dividerHeight;
    }
}
