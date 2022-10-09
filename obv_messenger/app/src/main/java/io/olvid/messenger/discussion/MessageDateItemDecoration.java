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

package io.olvid.messenger.discussion;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.StringUtils;

public class MessageDateItemDecoration extends RecyclerView.ItemDecoration {
    private final HashMap<String, Bitmap> headerBitmaps = new HashMap<>();
    private final Set<String> headerStringsToRemove = new HashSet<>();
    private final Rect itemRect = new Rect();
    private final Rect headerRect = new Rect();
    private boolean wasScrolling = false;
    private boolean firstLoad = true;
    private boolean scrolling = false;
    private int headerHeight;

    private String fadingHeader;
    private int previousOpacity;
    private boolean appearing;

    @NonNull private final FragmentActivity activity;
    @NonNull private final DiscussionActivity.MessageListAdapter messageListAdapter;
    @NonNull private final EmptyRecyclerView messageRecyclerView;
    private final ValueAnimator headerAppearingAnimator;
    private final ValueAnimator headerDisappearingAnimator;

    MessageDateItemDecoration(@NonNull FragmentActivity activity, @NonNull EmptyRecyclerView messageRecyclerView, @NonNull DiscussionActivity.MessageListAdapter messageListAdapter) {
        this.activity = activity;
        this.messageListAdapter = messageListAdapter;
        this.messageRecyclerView = messageRecyclerView;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, metrics);

        headerAppearingAnimator = ValueAnimator.ofInt(0, 255);
        headerAppearingAnimator.setDuration(300);
        headerAppearingAnimator.addUpdateListener(animation -> messageRecyclerView.invalidate());

        headerDisappearingAnimator = ValueAnimator.ofInt(255, 0);
        headerDisappearingAnimator.setStartDelay(300);
        headerDisappearingAnimator.setDuration(300);
        headerDisappearingAnimator.addUpdateListener(animation -> messageRecyclerView.invalidate());
    }

    void setScrolling(boolean scrolling) {
        this.scrolling = scrolling;
    }

    void resetHeaderBitmapCache() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, metrics);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            headerBitmaps.clear();
            messageRecyclerView.invalidate();
        }, 100);
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (messageListAdapter.messages == null) {
            return;
        }

        headerStringsToRemove.clear();
        headerStringsToRemove.addAll(headerBitmaps.keySet());
        int topOrigin = parent.getPaddingTop();

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION || position == 0) {
                continue;
            }
            position--;
            long thisTimestamp = messageListAdapter.messages.get(position).timestamp;
            String headerString = StringUtils.getDayOfDateString(parent.getContext(), thisTimestamp).toString();
            if (headerBitmaps.containsKey(headerString)) {
                headerStringsToRemove.remove(headerString);
            } else {
                View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_message_top_timestamp, parent, false);
                TextView tv = headerView.findViewById(R.id.message_timestamp_top_text_view);
                tv.setText(headerString);
                headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                Bitmap headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(headerBitmap);
                headerView.draw(bitmapCanvas);
                headerBitmaps.put(headerString, headerBitmap);
            }

            parent.getDecoratedBoundsWithMargins(child, itemRect);
            itemRect.top += child.getTranslationY();
            itemRect.bottom += child.getTranslationY();
            if (itemRect.top <= topOrigin &&
                    itemRect.bottom > topOrigin) {
                boolean shifted = false;
                if (messageListAdapter.messages.size() > position + 1) {
                    if (Utils.notTheSameDay(thisTimestamp, messageListAdapter.messages.get(position + 1).timestamp)) {
                        if (itemRect.bottom - headerHeight < topOrigin) {
                            shifted = true;
                        }
                    }
                }

                if (shifted) {
                    headerRect.top = itemRect.bottom - headerHeight;
                    headerRect.bottom = itemRect.bottom;
                } else {
                    headerRect.top = topOrigin;
                    headerRect.bottom = topOrigin + headerHeight;
                }
                headerRect.left = itemRect.left;
                headerRect.right = itemRect.right;
                canvas.save();
                canvas.clipRect(headerRect);
                Paint opacityPaint = new Paint();
                if (!wasScrolling && scrolling) {
                    if (headerString.equals(fadingHeader)) {
                        headerAppearingAnimator.setCurrentPlayTime(300L * previousOpacity / 255);
                    }
                    headerAppearingAnimator.start();
                    fadingHeader = headerString;
                    appearing = true;
                    firstLoad = false;
                } else if (wasScrolling && !scrolling) {
                    if (headerString.equals(fadingHeader) && (previousOpacity != 255)) {
                        headerDisappearingAnimator.setCurrentPlayTime(300L * previousOpacity / 255);
                    } else {
                        previousOpacity = 255;
                    }
                    headerDisappearingAnimator.start();
                    fadingHeader = headerString;
                    appearing = false;
                    firstLoad = false;
                }

                int alpha = 255;
                if (itemRect.top != topOrigin ||
                        (position != 0 &&
                                !Utils.notTheSameDay(thisTimestamp, messageListAdapter.messages.get(position - 1).timestamp))) {
                    if (headerString.equals(fadingHeader)) {
                        if (appearing) {
                            if (headerAppearingAnimator.isStarted()) {
                                alpha = (int) headerAppearingAnimator.getAnimatedValue();
                            }
                        } else {
                            if (headerDisappearingAnimator.isStarted()) {
                                if (headerDisappearingAnimator.isRunning()) {
                                    alpha = (int) headerDisappearingAnimator.getAnimatedValue();
                                } else {
                                    alpha = previousOpacity;
                                }
                            } else {
                                alpha = 0;
                            }
                        }
                        previousOpacity = alpha;
                    } else if (firstLoad) {
                        alpha = 0;
                    }
                }
                wasScrolling = scrolling;
                opacityPaint.setAlpha(alpha);

                Bitmap bitmap = headerBitmaps.get(headerString);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, headerRect.left, headerRect.top, opacityPaint);
                    canvas.restore();
                }
            } else if (itemRect.top > topOrigin) {
                if (position == 0 ||
                        Utils.notTheSameDay(thisTimestamp, messageListAdapter.messages.get(position - 1).timestamp)) {
                    itemRect.bottom = itemRect.top + headerHeight;
                    canvas.save();
                    canvas.clipRect(itemRect);
                    Bitmap bitmap = headerBitmaps.get(headerString);
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, itemRect.left, itemRect.top, null);
                        canvas.restore();
                    }
                }
            }
        }

        for (String headerString : headerStringsToRemove) {
            headerBitmaps.remove(headerString);
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION || position == 0) {
            return;
        }
        position--;
        if (position == 0 ||
                Utils.notTheSameDay(messageListAdapter.messages.get(position).timestamp, messageListAdapter.messages.get(position - 1).timestamp)) {
            outRect.top += headerHeight;
        }
    }
}
