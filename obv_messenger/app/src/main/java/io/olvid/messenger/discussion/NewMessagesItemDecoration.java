/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.dao.MessageDao;

public class NewMessagesItemDecoration extends RecyclerView.ItemDecoration implements Observer<MessageDao.UnreadCountAndFirstMessage> {
    private final Rect itemRect = new Rect();
    private final int headerHeight;
    private long firstUnreadMessageId = -1;
    private int shift = 0;
    private boolean firstLoad = true;
    private Bitmap headerBitmap;
    private int firstUnreadMessagePosition;
    private int unreadCount;

    @NonNull private final FragmentActivity activity;
    @NonNull private final DiscussionActivity.MessageListAdapter messageListAdapter;
    @NonNull private final MessageDateItemDecoration messageDateItemDecoration;


    NewMessagesItemDecoration(@NonNull FragmentActivity activity, @NonNull DiscussionActivity.MessageListAdapter messageListAdapter, @NonNull MessageDateItemDecoration messageDateItemDecoration) {
        this.activity = activity;
        this.messageListAdapter = messageListAdapter;
        this.messageDateItemDecoration = messageDateItemDecoration;

        DiscussionViewModel discussionViewModel = new ViewModelProvider(activity).get(DiscussionViewModel.class);
        discussionViewModel.getUnreadCountAndFirstMessage().observe(activity, this);

        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, metrics);
    }

    void resetHeaderBitmapCache() {
        headerBitmap = null;
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (firstUnreadMessageId == -1) {
            return;
        }
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == 0) {
                continue;
            }
            position--;
            if (position != firstUnreadMessagePosition) {
                continue;
            }
            if (headerBitmap == null) {
                View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_message_new_message_count, parent, false);
                TextView tv = headerView.findViewById(R.id.message_new_message_count_text_view);
                String headerString = activity.getResources().getQuantityString(R.plurals.text_unread_message_count, unreadCount, unreadCount);
                tv.setText(headerString);
                headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(headerBitmap);
                headerView.draw(bitmapCanvas);
            }
            canvas.save();
            parent.getDecoratedBoundsWithMargins(child, itemRect);
            itemRect.top += child.getTranslationY();
            itemRect.bottom += child.getTranslationY();
            canvas.drawBitmap(headerBitmap, itemRect.left, itemRect.top + shift, null);
            canvas.restore();
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
        if (firstUnreadMessageId != -1 &&
                messageListAdapter.messages.get(position).id == firstUnreadMessageId) {
            outRect.top += headerHeight;
            Rect shiftRect = new Rect();
            messageDateItemDecoration.getItemOffsets(shiftRect, view, parent, state);
            shift = shiftRect.top;
            firstUnreadMessagePosition = position;
        }
    }

    @Override
    public void onChanged(MessageDao.UnreadCountAndFirstMessage unreadCountAndFirstMessage) {
        if (unreadCountAndFirstMessage == null) {
            return;
        }

        if (unreadCountAndFirstMessage.unreadCount == 0) {
            firstUnreadMessageId = -1;
            firstUnreadMessagePosition = -127;
        } else {
            firstUnreadMessageId = unreadCountAndFirstMessage.messageId;
            unreadCount = unreadCountAndFirstMessage.unreadCount;
            headerBitmap = null;

            App.runThread(() -> {
                // search for the message adapter position, and notify update so the getItemOffset is called again
                try {
                    for (int i = messageListAdapter.messages.size() - 1; i >= 0; i--) {
                        if (messageListAdapter.messages.get(i).id == firstUnreadMessageId) {
                            int adapterPosition = i + 1;
                            activity.runOnUiThread(() -> messageListAdapter.notifyItemChanged(adapterPosition, 0));
                            break;
                        }
                    }
                } catch (Exception e) {
                    // do nothing here
                }
            });

            if (firstLoad) {
                messageListAdapter.requestScrollToMessageId(firstUnreadMessageId, true, false);
            }
        }
        firstLoad = false;
    }
}
