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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Reaction;
import io.olvid.messenger.databases.tasks.UpdateReactionsTask;

public class ReactionsPanelPopUpMenu {
    @NonNull private final FragmentActivity activity;
    @NonNull private final View parentView;
    private final int clickX;
    private final int clickY;
    private final long messageId;
    private final Vibrator vibrator;
    private final DisplayMetrics metrics;
    String previousReaction = null;

    public ReactionsPanelPopUpMenu(@NonNull FragmentActivity activity, @NonNull View parentView, int clickX, int clickY, long messageId) {
        this.activity = activity;
        this.parentView = parentView;
        this.messageId = messageId;
        this.clickX = clickX;
        this.clickY = clickY;
        this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        this.metrics = activity.getResources().getDisplayMetrics();

        App.runThread(() -> {
            Reaction reaction = AppDatabase.getInstance().reactionDao().getMyReactionForMessage(messageId);
            this.previousReaction = reaction == null ? null : reaction.emoji;
            activity.runOnUiThread(this::showPopupWindow);
        });
    }

    private void showPopupWindow() {
        int twoDpInPx = (int) (2 * metrics.density);
        int eightDpInPx = (int) (8 * metrics.density);
        int sixteenDpInPx = (int) (16 * metrics.density);

        LinearLayout popUpView = new LinearLayout(activity);
        popUpView.setOrientation(LinearLayout.HORIZONTAL);
        popUpView.setPadding(eightDpInPx, 0, eightDpInPx, 0);

        String[] reactions = Reaction.DEFAULT_REACTIONS_LIST;

        int panelWidthDp = (int) ((parentView.getWidth() / metrics.density) - 32);
        if (panelWidthDp > 400) {
            panelWidthDp = 400;
        }
        int panelWidthPx = (int) (panelWidthDp * metrics.density);

        PopupWindow popupWindow = new PopupWindow(popUpView, panelWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_reactions_panel));


        // compute reaction size (text size)
        int reactionFontSizeDp = (panelWidthDp - 16) / reactions.length;

        reactionFontSizeDp -= 4; // leave 2dp padding on each side
        reactionFontSizeDp *= 4f / 5f; // scale factor between emoji height and width

        // cap reaction font size at 32dp
        if (reactionFontSizeDp > 32) {
            reactionFontSizeDp = 32;
        }

        int panelHeightPx = (int) ((reactionFontSizeDp + 4) * metrics.density);

        for (String reaction : reactions) {
            TextView child = new AppCompatTextView(activity);
            child.setTextColor(ContextCompat.getColor(activity, R.color.greyTint));
            child.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            child.setPadding(twoDpInPx, twoDpInPx, twoDpInPx, twoDpInPx);
            child.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            child.setTextSize(TypedValue.COMPLEX_UNIT_DIP, reactionFontSizeDp);
            child.setText(reaction);

            if (Objects.equals(previousReaction, reaction)) {
                child.setBackgroundResource(R.drawable.background_reactions_panel_previous_reaction);
                child.setOnClickListener(v -> {
                    react(null);
                    popupWindow.dismiss();
                });
            } else {
                child.setOnClickListener(v -> {
                    react(reaction);
                    popupWindow.dismiss();
                });
            }

            popUpView.addView(child);
        }

        int fingerOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 8, metrics);
        int x = Math.max(sixteenDpInPx, Math.min(clickX - panelWidthPx / 2, parentView.getWidth() - panelWidthPx - sixteenDpInPx));
        int[] pos = new int[2];
        parentView.getLocationOnScreen(pos);
        popupWindow.showAtLocation(parentView, Gravity.NO_GRAVITY, pos[0] + x, pos[1] + clickY - panelHeightPx - fingerOffset);
        if (vibrator != null) {
            vibrator.vibrate(20);
        }
    }

    private void react(String emoji) {
        if (vibrator != null) {
            vibrator.vibrate(20);
        }
        App.runThread(new UpdateReactionsTask(messageId, emoji, null, System.currentTimeMillis()));
    }
}
