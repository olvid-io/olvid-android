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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmojiList;

public class EmojiPickerViewFactory {
    private static int lastScrollPosition = 0;

    static View createEmojiPickerView(Context context, @Nullable View windowTokenView, @NonNull EmojiClickListener emojiClickListener, @Nullable EmojiKeyboardListener emojiKeyboardListener, int maxRows, boolean isReactionPopup, @Nullable String highlightedEmoji) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        Pair<RecyclerView, EmojiListAdapter> recyclerViewAndAdapter = createEmojiRecyclerView(context, emojiClickListener, maxRows, isReactionPopup, highlightedEmoji, windowTokenView);
        RecyclerView emojiRecyclerView = recyclerViewAndAdapter.first;
        linearLayout.addView(emojiRecyclerView);

        View separator = new View(context);
        separator.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) context.getResources().getDisplayMetrics().density));
        separator.setBackgroundColor(ContextCompat.getColor(context, R.color.lightGrey));
        linearLayout.addView(separator);

        LinearLayout groupLinearLayout = createGroupLinearLayout(context, emojiKeyboardListener, recyclerViewAndAdapter.second, isReactionPopup);
        linearLayout.addView(groupLinearLayout);

        return linearLayout;
    }

    private static Pair<RecyclerView, EmojiListAdapter> createEmojiRecyclerView(Context context, EmojiClickListener emojiClickListener, int maxRows, boolean isReactionPopup, @Nullable String highlightedEmoji, @Nullable View windowTokenView) {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, maxRows);
        gridLayoutManager.setOrientation(RecyclerView.HORIZONTAL);

        RecyclerView emojiRecyclerView = new RecyclerView(context) {
            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                if (!isReactionPopup) {
                    // count number of rows we could display (minus 2 to account for other views height)
                    int screenRows = newConfig.screenHeightDp / 40 - 2;
                    gridLayoutManager.setSpanCount(Math.min(screenRows / 2, maxRows));
                }
            }
        };
        emojiRecyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emojiRecyclerView.setLayoutManager(gridLayoutManager);

        emojiRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lastScrollPosition = gridLayoutManager.findFirstVisibleItemPosition();
                }
            }
        });
        emojiRecyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!isReactionPopup) {
                    DisplayMetrics metrics = v.getContext().getResources().getDisplayMetrics();
                    // count number of rows we could display (minus 2 to account for other views height)
                    int screenRows = metrics.heightPixels / (int) (40 * metrics.density) - 2;
                    gridLayoutManager.setSpanCount(Math.min(screenRows / 2, maxRows));
                }
                gridLayoutManager.scrollToPosition(lastScrollPosition);
            }

            @Override
            public void onViewDetachedFromWindow(View v) { }
        });

        EmojiListAdapter adapter = new EmojiListAdapter(context, emojiClickListener, isReactionPopup, highlightedEmoji, windowTokenView);
        emojiRecyclerView.setAdapter(adapter);

        return new Pair<>(emojiRecyclerView, adapter);
    }


    private static LinearLayout createGroupLinearLayout(Context context, @Nullable EmojiKeyboardListener emojiKeyboardListener, EmojiListAdapter adapter, boolean isInDialog) {
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout groupLinearLayout = new LinearLayout(context);
        groupLinearLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (28 * density)));
        if (isInDialog) {
            groupLinearLayout.setBackgroundResource(R.drawable.background_emoji_picker_groups_dialog);
            groupLinearLayout.setClipToOutline(true);
        } else {
            groupLinearLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.lighterGrey));
        }
        groupLinearLayout.setOrientation(LinearLayout.HORIZONTAL);

        TypedValue selectableItemBackground = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableItemBackground, true);
        for (EmojiList.EmojiGroup emojiGroup : EmojiList.EmojiGroup.values()) {
            TextView groupTextView = new AppCompatTextView(new ContextThemeWrapper(context, R.style.BlueOrWhiteRipple));
            groupTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2));
            groupTextView.setGravity(Gravity.CENTER);
            groupTextView.setText(emojiForEmojiGroup(emojiGroup));
            groupTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            groupTextView.setTextColor(0xff000000);
            groupTextView.setBackgroundResource(selectableItemBackground.resourceId);

            groupTextView.setOnClickListener(v -> adapter.scrollToPosition(EmojiList.offsetForEmojiGroup(emojiGroup)));
            groupLinearLayout.addView(groupTextView);
        }


        if (emojiKeyboardListener != null) {
            TextView keyboardSwitch = new AppCompatTextView(new ContextThemeWrapper(context, R.style.SubtleBlueRipple));
            keyboardSwitch.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            keyboardSwitch.setGravity(Gravity.CENTER);
            keyboardSwitch.setText(R.string.ABC);
            keyboardSwitch.setTypeface(Typeface.DEFAULT_BOLD);
            keyboardSwitch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            keyboardSwitch.setTextColor(ContextCompat.getColor(context, R.color.greyTint));
            keyboardSwitch.setBackgroundResource(selectableItemBackground.resourceId);

            keyboardSwitch.setOnClickListener(v -> emojiKeyboardListener.onRestoreKeyboard());
            groupLinearLayout.addView(keyboardSwitch, 0);

            ImageView backSpaceImageView = new ImageView(new ContextThemeWrapper(context, R.style.SubtleBlueRipple));
            backSpaceImageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            backSpaceImageView.setBackgroundResource(selectableItemBackground.resourceId);
            backSpaceImageView.setImageResource(R.drawable.ic_backspace);
            backSpaceImageView.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            backSpaceImageView.setOnClickListener(v -> emojiKeyboardListener.onBackspace());
            groupLinearLayout.addView(backSpaceImageView);
        }


        return groupLinearLayout;
    }

    private static String emojiForEmojiGroup(@NonNull EmojiList.EmojiGroup group) {
        switch (group) {
            case SMILEYS_EMOTION:
                return "😀";
            case PEOPLE_BODY:
                return "👍";
            case ANIMALS_NATURE:
                return "🐭";
            case FOOD_DRINK:
                return "🍉";
            case TRAVEL_PLACES:
                return "🌍";
            case ACTIVITIES:
                return "🎉";
            case OBJECTS:
                return "👙";
            case SYMBOLS:
                return "🚾";
            case FLAGS:
                return "🏳️";
        }
        return "";
    }


    private static class EmojiListAdapter extends RecyclerView.Adapter<EmojiViewHolder> {
        private final String[][] emojis = EmojiList.EMOJIS;

        public static final int TYPE_SINGLE_EMOJI = 1;
        public static final int TYPE_EMOJI_WITH_VARIANTS = 2;

        private final LayoutInflater layoutInflater;
        private final EmojiClickListener emojiClickListener;
        private final boolean isReactionPopup;
        @Nullable private final String highlightedEmoji;
        private final RecyclerView.SmoothScroller smoothScroller;
        @Nullable private final View windowTokenView;

        private RecyclerView.LayoutManager layoutManager;
        private int scrollOffsetPx = 0;

        public EmojiListAdapter(Context context, EmojiClickListener emojiClickListener, boolean isReactionPopup, @Nullable String highlightedEmoji, @Nullable View windowTokenView) {
            this.layoutInflater = LayoutInflater.from(context);
            this.emojiClickListener = emojiClickListener;
            this.isReactionPopup = isReactionPopup;
            this.highlightedEmoji = highlightedEmoji;
            this.windowTokenView = windowTokenView;
            this.smoothScroller = new LinearSmoothScroller(context) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return boxStart - viewStart + scrollOffsetPx;
                }

                // let the scroll be twice as fast as normal. There are a lot of emojis!
                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                    return super.calculateSpeedPerPixel(displayMetrics) / 2;
                }
            };
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view;
            if (viewType == TYPE_SINGLE_EMOJI) {
                view = layoutInflater.inflate(R.layout.item_view_emoji_single, parent, false);
            } else {
                view = layoutInflater.inflate(R.layout.item_view_emoji_multiple, parent, false);
            }
            return new EmojiViewHolder(view, viewType, emojiClickListener, isReactionPopup, highlightedEmoji, windowTokenView);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            holder.setEmoji(emojis[position][0]);
            if (emojis[position].length > 1) {
                if (isReactionPopup) {
                    holder.setEmojiVariants(emojis[position]);
                } else {
                    holder.setEmojiVariants(Arrays.copyOfRange(emojis[position], 1, emojis[position].length));
                }
            }
        }

        @Override
        public int getItemCount() {
            return emojis.length;
        }

        @Override
        public int getItemViewType(int position) {
            if (emojis[position].length > 1) {
                return TYPE_EMOJI_WITH_VARIANTS;
            }
            return TYPE_SINGLE_EMOJI;
        }

        void scrollToPosition(int position) {
            if (layoutManager != null) {
                smoothScroller.setTargetPosition(position);
                layoutManager.startSmoothScroll(smoothScroller);
            }
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            this.layoutManager = recyclerView.getLayoutManager();
            scrollOffsetPx = (int) (64 * recyclerView.getContext().getResources().getDisplayMetrics().density);
        }

        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            this.layoutManager = null;
        }
    }

    private static class EmojiViewHolder extends RecyclerView.ViewHolder {
        private final TextView emojiTextView;
        @Nullable private final String highlightedEmoji;
        private String emoji;
        private String[] emojiVariants;

        public EmojiViewHolder(@NonNull View itemView, int viewType, EmojiClickListener emojiClickListener, boolean isReactionPopup, @Nullable String highlightedEmoji, @Nullable View windowTokenView) {
            super(itemView);
            this.highlightedEmoji = highlightedEmoji;
            this.emojiTextView = itemView.findViewById(R.id.emoji_text_view);
            if (emojiClickListener != null) {
                this.emojiTextView.setOnClickListener(v -> {
                    if (highlightedEmoji != null && highlightedEmoji.equals(emoji)) {
                        emojiClickListener.onHighlightedClick(this.itemView, this.emoji);
                    } else {
                        emojiClickListener.onClick(this.emoji);
                    }
                });
                if (viewType == EmojiListAdapter.TYPE_SINGLE_EMOJI) {
                    this.emojiTextView.setOnLongClickListener(v -> {
                        emojiClickListener.onLongClick(this.emoji);
                        return true;
                    });
                } else {
                    this.emojiTextView.setOnLongClickListener(v -> {
                        openEmojiVariantPicker(v, this.emojiVariants, emojiClickListener, isReactionPopup, highlightedEmoji, windowTokenView);
                        return true;
                    });
                }
            }
        }

        public void setEmoji(String emoji) {
            this.emoji = emoji;
            this.emojiTextView.setText(emoji);
            if (highlightedEmoji != null && highlightedEmoji.equals(emoji)) {
                this.emojiTextView.setBackgroundResource(R.drawable.background_reactions_panel_previous_reaction);
            } else {
                this.emojiTextView.setBackgroundResource(R.drawable.background_circular_ripple);
            }
        }

        public void setEmojiVariants(String[] emojiVariants) {
            this.emojiVariants = emojiVariants;
        }
    }

    private static void openEmojiVariantPicker(View anchorView, String[] emojiVariants, EmojiClickListener emojiClickListener, boolean isReactionPopup, @Nullable String highlightedEmoji, @Nullable View windowTokenView) {
        Context context = anchorView.getContext();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int fortyDp = (int) (40 * metrics.density);
        int rowSize = (isReactionPopup && emojiVariants.length == 6) ? 6 : 5;

        ScrollView popupView = new ScrollView(context);
        popupView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ConstraintLayout constraintLayout = new ConstraintLayout(context);
        constraintLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupView.addView(constraintLayout);

        Flow flow = new Flow(context);
        flow.setId(View.generateViewId());

        ConstraintLayout.LayoutParams flowLayout = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT);
        flowLayout.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        flow.setLayoutParams(flowLayout);

        flow.setOrientation(Flow.HORIZONTAL);
        flow.setWrapMode(Flow.WRAP_CHAIN);
        flow.setMaxElementsWrap(rowSize);
        constraintLayout.addView(flow);

        PopupWindow popupWindow = new PopupWindow(popupView, rowSize * fortyDp, (1 + (emojiVariants.length - 1) / rowSize) * fortyDp, true);

        // if we are in a case with 25 variants, make the "no variant" emoji the last one, otherwise, leave it first
        if (isReactionPopup && emojiVariants.length > 6) {
            String[] copy = new String[emojiVariants.length];
            System.arraycopy(emojiVariants, 1, copy, 0, copy.length - 1);
            copy[copy.length - 1] = emojiVariants[0];
            emojiVariants = copy;
        }

        for (String emoji : emojiVariants) {
            TextView textView = new AppCompatTextView(context);
            textView.setId(View.generateViewId());
            textView.setLayoutParams(new LinearLayout.LayoutParams(fortyDp, fortyDp));
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
            textView.setText(emoji);
            textView.setTextColor(0xff000000);
            if (highlightedEmoji != null && highlightedEmoji.equals(emoji)) {
                textView.setBackgroundResource(R.drawable.background_reactions_panel_previous_reaction);
                textView.setOnClickListener(v -> {
                    emojiClickListener.onHighlightedClick(textView, emoji);
                    popupWindow.dismiss();
                });
            } else {
                textView.setBackgroundResource(R.drawable.background_circular_ripple);
                textView.setOnClickListener(v -> {
                    emojiClickListener.onClick(emoji);
                    popupWindow.dismiss();
                });
            }

            textView.setOnLongClickListener(v -> {
                emojiClickListener.onLongClick(emoji);
                popupWindow.dismiss();
                return true;
            });
            constraintLayout.addView(textView);
            flow.addView(textView);
        }

        popupWindow.setElevation(24);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        if (isReactionPopup) {
            popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.background_rounded_dialog_almost_white));
        } else {
            popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.background_rounded_dialog));
        }

        int[] pos = new int[2];
        if (windowTokenView == null) {
            anchorView.getLocationInWindow(pos);
        } else {
            anchorView.getLocationOnScreen(pos);
            int[] pos2 = new int[2];
            windowTokenView.getLocationOnScreen(pos2);
            pos[0] -= pos2[0];
            pos[1] -= pos2[1];
            windowTokenView.getLocationInWindow(pos2);
            pos[0] += pos2[0];
            pos[1] += pos2[1];
        }

        int xOffset = -((rowSize - 1) * fortyDp) / 2;
        if (pos[0] + xOffset < fortyDp / 5) {
            xOffset = fortyDp / 5 - pos[0];
        } else if (pos[0] + xOffset + rowSize * fortyDp + fortyDp / 5 > metrics.widthPixels) {
            xOffset = metrics.widthPixels - fortyDp / 5 - pos[0] - rowSize * fortyDp;
        }

        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.showAtLocation(windowTokenView == null ? anchorView : windowTokenView, Gravity.NO_GRAVITY, pos[0] + xOffset, pos[1] - (2 + (emojiVariants.length - 1) / rowSize) * fortyDp);
    }

    interface EmojiClickListener {
        void onClick(String emoji);
        void onLongClick(String emoji);
        void onHighlightedClick(View emojiView, String emoji);
    }

    interface EmojiKeyboardListener {
        void onBackspace();
        void onRestoreKeyboard();
    }
}
