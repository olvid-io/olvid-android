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
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.List;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.Reaction;
import io.olvid.messenger.databases.tasks.UpdateReactionsTask;
import io.olvid.messenger.settings.SettingsActivity;

public class ReactionsPanelPopUpMenu {
    @NonNull private final FragmentActivity activity;
    @NonNull private final View parentView;
    private final int clickX;
    private final int clickY;
    private final long messageId;
    private final Vibrator vibrator;
    private final DisplayMetrics metrics;
    String previousReaction = null;


    private Context wrappedContext;
    private PopupWindow reactionPopupWindow;
    private LinearLayout popUpViewLinearLayout;
    private ConstraintLayout reactionConstraintLayout;
    private Flow reactionFlow;
    private int viewSizePx;
    private int fontSizeDp;
    private ImageView plusButton;

    private TextView separatorTextView = null;
    private View emojiPickerView = null;
    private int emojiPickerRows = 4;

    private boolean plusOpen = false;

    public ReactionsPanelPopUpMenu(@NonNull FragmentActivity activity, @NonNull View parentView, int clickX, int clickY, long messageId) {
        this.activity = activity;
        this.parentView = parentView;
        this.messageId = messageId;
        this.clickX = clickX;
        this.clickY = clickY;
        this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        this.metrics = activity.getResources().getDisplayMetrics();

        App.runThread(() -> {
            Message message = AppDatabase.getInstance().messageDao().get(messageId);
            if (message == null || message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE || message.wipeStatus == Message.WIPE_STATUS_WIPED || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED) {
                return;
            }

            Reaction reaction = AppDatabase.getInstance().reactionDao().getMyReactionForMessage(messageId);
            this.previousReaction = reaction == null ? null : reaction.emoji;
            activity.runOnUiThread(this::buildPopupWindow);
        });
    }

    private void buildPopupWindow() {
        popUpViewLinearLayout = new LinearLayout(activity);
        popUpViewLinearLayout.setOrientation(LinearLayout.VERTICAL);
        popUpViewLinearLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        reactionPopupWindow = new PopupWindow(popUpViewLinearLayout);
        reactionPopupWindow.setFocusable(true);
        reactionPopupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        reactionPopupWindow.setElevation(12);
        reactionPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        reactionPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_rounded_dialog));


        // determine the max panel width: screen width with 16dp margin start and end
        int maxPanelWidthDp = (int) ((parentView.getWidth() / metrics.density) - 32);

        // compute the font size so that 7 reactions (6 and the +) fit
        int viewSizeDp = Math.min(56, maxPanelWidthDp / 7);
        viewSizePx = (int) (viewSizeDp * metrics.density);
        fontSizeDp = (viewSizeDp * 5) / 8; // font size if 5/8 of view size


        reactionConstraintLayout = new ConstraintLayout(activity);
        reactionConstraintLayout.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
        popUpViewLinearLayout.addView(reactionConstraintLayout);

        reactionFlow = new Flow(activity);
        reactionFlow.setId(View.generateViewId());

        ConstraintLayout.LayoutParams flowLayout = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        flowLayout.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        reactionFlow.setLayoutParams(flowLayout);

        reactionFlow.setOrientation(Flow.HORIZONTAL);
        reactionFlow.setWrapMode(Flow.WRAP_ALIGNED);
        reactionFlow.setHorizontalStyle(Flow.CHAIN_PACKED);
        reactionFlow.setHorizontalBias(0);
        reactionConstraintLayout.addView(reactionFlow);

        wrappedContext = new ContextThemeWrapper(activity, R.style.SubtleBlueRipple);

        plusButton = new ImageView(wrappedContext);
        ConstraintLayout.LayoutParams plusLayout = new ConstraintLayout.LayoutParams(viewSizePx, viewSizePx);
        plusLayout.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        plusLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        plusButton.setLayoutParams(plusLayout);

        plusButton.setPadding(viewSizePx/8, viewSizePx/8, viewSizePx/8, viewSizePx/8);
        plusButton.setBackgroundResource(R.drawable.background_circular_ripple);

        plusButton.setOnClickListener(v -> {
            plusOpen = !plusOpen;
            fillAndShowPopupWindow();
        });
        reactionConstraintLayout.addView(plusButton);

        if (vibrator != null) {
            vibrator.vibrate(20);
        }

        fillAndShowPopupWindow();
    }

    private void fillAndShowPopupWindow() {
        List<String> reactions = SettingsActivity.getPreferredReactions();
        if (previousReaction != null && !reactions.contains(previousReaction)) {
            reactions.add(previousReaction);
        }

        int sixteenDpInPx = (int) (16 * metrics.density);

        final int panelWidthPx;
        final int constraintLayoutHeightPx;
        if (plusOpen) {
            panelWidthPx = parentView.getWidth() - 2*sixteenDpInPx;
            int reactionsPerRow = panelWidthPx / viewSizePx;
            constraintLayoutHeightPx = (1 + reactions.size() / reactionsPerRow) * viewSizePx;
        } else {
            panelWidthPx = Math.min(7, reactions.size() + 1) * viewSizePx;
            constraintLayoutHeightPx = (1 + reactions.size() / 7) * viewSizePx;
        }

        ViewGroup.LayoutParams layoutParams = reactionConstraintLayout.getLayoutParams();
        layoutParams.width = panelWidthPx;
        layoutParams.height = constraintLayoutHeightPx;

        int[] previousIds = reactionFlow.getReferencedIds();
        for (int viewId : previousIds) {
            View view = reactionConstraintLayout.findViewById(viewId);
            if (view != null) {
                reactionFlow.removeView(view);
                reactionConstraintLayout.removeView(view);
            }
        }

        for (String reaction : reactions) {
            TextView textView = new AppCompatTextView(wrappedContext);
            textView.setId(View.generateViewId());
            textView.setTextColor(0xff000000);
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSizeDp);
            textView.setText(reaction);
            textView.setLayoutParams(new ViewGroup.LayoutParams(viewSizePx, viewSizePx));

            if (Objects.equals(previousReaction, reaction)) {
                textView.setBackgroundResource(R.drawable.background_reactions_panel_previous_reaction);
                textView.setOnClickListener(v -> {
                    react(null);
                    reactionPopupWindow.dismiss();
                });
                textView.setOnLongClickListener(v -> {
                    if (!plusOpen) {
                        react(null);
                        reactionPopupWindow.dismiss();
                    }
                    return true;
                });
            } else {
                textView.setBackgroundResource(R.drawable.background_circular_ripple);
                textView.setOnClickListener(v -> {
                    react(reaction);
                    reactionPopupWindow.dismiss();
                });
                textView.setOnLongClickListener(v -> {
                    if (plusOpen) {
                        togglePreferred(reaction);
                    } else {
                        react(reaction);
                        reactionPopupWindow.dismiss();
                    }
                    return true;
                });
            }

            reactionConstraintLayout.addView(textView);
            reactionFlow.addView(textView);
        }

        reactionConstraintLayout.requestLayout();

        final int panelHeightPx;
        if (plusOpen) {
            plusButton.setImageResource(R.drawable.ic_minus_reaction);
            if (separatorTextView == null) {
                separatorTextView = new TextView(wrappedContext);
                separatorTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * metrics.density), 0));
                separatorTextView.setBackgroundColor(ContextCompat.getColor(activity, R.color.lighterGrey));
                separatorTextView.setTextColor(ContextCompat.getColor(activity, R.color.greyTint));
                separatorTextView.setAllCaps(true);
                separatorTextView.setText(activity.getString(R.string.label_long_press_for_favorite));
                separatorTextView.setMaxLines(1);
                separatorTextView.setEllipsize(TextUtils.TruncateAt.END);
                separatorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                separatorTextView.setGravity(Gravity.CENTER_VERTICAL);
                separatorTextView.setPadding((int) (8* metrics.density), 0, (int) (8* metrics.density), 0);
            }
            if (separatorTextView.getParent() == null) {
                popUpViewLinearLayout.addView(separatorTextView, 1);
            }


            if (emojiPickerView == null) {
                EmojiPickerViewFactory.EmojiClickListener emojiClickListener = new EmojiPickerViewFactory.EmojiClickListener() {
                    @Override
                    public void onClick(String emoji) {
                        react(emoji);
                        reactionPopupWindow.dismiss();
                    }

                    @Override
                    public void onHighlightedClick(View emojiView, String emoji) {
                        react(null);
                        reactionPopupWindow.dismiss();
                    }

                    @Override
                    public void onLongClick(String emoji) {
                        togglePreferred(emoji);
                    }
                };

                emojiPickerRows = Math.max(1, Math.min(4, (parentView.getHeight() - (constraintLayoutHeightPx + (int) (24 * metrics.density) + (int) metrics.density + (int) (28 * metrics.density)))/ (int) (40* metrics.density)));

                emojiPickerView = EmojiPickerViewFactory.createEmojiPickerView(activity, parentView, emojiClickListener, null, emojiPickerRows, true, previousReaction);
                emojiPickerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
            }
            if (emojiPickerView.getParent() == null) {
                popUpViewLinearLayout.addView(emojiPickerView, 2);
            }
            panelHeightPx = constraintLayoutHeightPx + (int) (24 * metrics.density) + emojiPickerRows * ((int) (40* metrics.density)) + (int) metrics.density + (int) (28 * metrics.density);
        } else {
            plusButton.setImageResource(R.drawable.ic_plus_reaction);
            if (separatorTextView != null && separatorTextView.getParent() != null) {
                popUpViewLinearLayout.removeView(separatorTextView);
            }
            if (emojiPickerView != null && emojiPickerView.getParent() != null) {
                popUpViewLinearLayout.removeView(emojiPickerView);
            }
            panelHeightPx = constraintLayoutHeightPx;
        }

        int fingerOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 8, metrics);
        int x = Math.max(sixteenDpInPx, Math.min(clickX - panelWidthPx / 2, parentView.getWidth() - panelWidthPx - sixteenDpInPx));
        int[] pos = new int[2];
        parentView.getLocationInWindow(pos);

        // adjust fingerOffset so the popup remains on screen
        if (clickY - constraintLayoutHeightPx - fingerOffset < 0) {
            fingerOffset = clickY-constraintLayoutHeightPx;
        } else if (clickY - constraintLayoutHeightPx - fingerOffset + panelHeightPx > parentView.getHeight()) {
            fingerOffset = clickY - constraintLayoutHeightPx + panelHeightPx - parentView.getHeight();
        }

        if (reactionPopupWindow.isShowing()) {
            reactionPopupWindow.update(pos[0] + x, pos[1] + clickY - constraintLayoutHeightPx - fingerOffset, panelWidthPx, panelHeightPx);
        } else {
            reactionPopupWindow.setWidth(panelWidthPx);
            reactionPopupWindow.setHeight(panelHeightPx);
            reactionPopupWindow.showAtLocation(parentView, Gravity.NO_GRAVITY, pos[0] + x, pos[1] + clickY - constraintLayoutHeightPx - fingerOffset);
        }
    }

    private void togglePreferred(String emoji) {
        List<String> preferredReactions = SettingsActivity.getPreferredReactions();
        if (preferredReactions.contains(emoji)) {
            preferredReactions.remove(emoji);
        } else {
            preferredReactions.add(emoji);
        }
        SettingsActivity.setPreferredReactions(preferredReactions);
        fillAndShowPopupWindow();
    }

    private void react(String emoji) {
        if (vibrator != null) {
            vibrator.vibrate(20);
        }
        App.runThread(new UpdateReactionsTask(messageId, emoji, null, System.currentTimeMillis()));
    }
}
