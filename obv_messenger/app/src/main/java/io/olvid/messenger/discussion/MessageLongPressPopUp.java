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

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.Reaction;
import io.olvid.messenger.databases.tasks.CopySelectedMessageTask;
import io.olvid.messenger.databases.tasks.DeleteMessagesTask;
import io.olvid.messenger.databases.tasks.ShareSelectedMessageTask;
import io.olvid.messenger.databases.tasks.UpdateReactionsTask;
import io.olvid.messenger.settings.SettingsActivity;

public class MessageLongPressPopUp {
    @NonNull private final FragmentActivity activity;
    @NonNull private final DiscussionActivity.DiscussionDelegate discussionDelegate;
    @NonNull private final View parentView;
    private final int clickX;
    private final int clickY;
    private final int messageViewBottomPx;
    private final long messageId;
    private final Vibrator vibrator;
    private final DisplayMetrics metrics;
    String previousReaction = null;

    private Message message;
    private Discussion discussion;


    private Context wrappedContext;
    private PopupWindow popupWindow;
    private LinearLayout reactionsPopUpLinearLayout;
    private ConstraintLayout reactionConstraintLayout;
    private Flow reactionFlow;
    private int viewSizePx;
    private int fontSizeDp;
    private ImageView plusButton;
    private boolean plusOpen = false;
    private int additionalBottomPadding = 0;

    private TextView separatorTextView = null;
    private View emojiPickerView = null;
    private int emojiPickerRows = 4;

    public MessageLongPressPopUp(@NonNull FragmentActivity activity, @NonNull DiscussionActivity.DiscussionDelegate discussionDelegate, @NonNull View parentView, int clickX, int clickY, int messageViewBottomPx, long messageId) {
        this.activity = activity;
        this.discussionDelegate = discussionDelegate;
        this.parentView = parentView;
        this.messageId = messageId;
        this.clickX = clickX;
        this.clickY = clickY;
        this.messageViewBottomPx = Math.max(0, messageViewBottomPx);
        this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        this.metrics = activity.getResources().getDisplayMetrics();

        App.runThread(() -> {
            this.message = AppDatabase.getInstance().messageDao().get(messageId);
            if (message == null) {
                return;
            }
            this.discussion = AppDatabase.getInstance().discussionDao().getById(message.discussionId);
            if (discussion == null) {
                return;
            }

            Reaction reaction = AppDatabase.getInstance().reactionDao().getMyReactionForMessage(messageId);
            this.previousReaction = reaction == null ? null : reaction.emoji;
            activity.runOnUiThread(this::buildPopupWindow);
        });
    }

    @SuppressLint("InflateParams")
    private void buildPopupWindow() {
        if (vibrator != null) {
            vibrator.vibrate(20);
        }

        wrappedContext = new ContextThemeWrapper(activity, R.style.SubtleBlueRipple);

        ConstraintLayout popUpView = (ConstraintLayout) LayoutInflater.from(activity).inflate(R.layout.view_unified_reaction_and_swipe, null);
        popUpView.setOnClickListener(v -> popupWindow.dismiss());

        popupWindow = new PopupWindow(popUpView);
        popupWindow.setFocusable(true);
        popupWindow.setElevation(12);
        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setBackgroundDrawable(new ColorDrawable());
        popupWindow.setOnDismissListener(() -> discussionDelegate.setAdditionalBottomPadding(0));

        reactionsPopUpLinearLayout = popUpView.findViewById(R.id.reactions_popup_linear_view);
        reactionsPopUpLinearLayout.setClickable(true);

        // determine the max panel width: screen width with 16dp margin start and end
        int maxPanelWidthDp = (int) ((parentView.getWidth() / metrics.density) - 32);

        // compute the font size so that 7 reactions (6 and the +) fit
        int viewSizeDp = Math.min(56, maxPanelWidthDp / 7);
        viewSizePx = (int) (viewSizeDp * metrics.density);
        fontSizeDp = (viewSizeDp * 5) / 8; // font size if 5/8 of view size

        reactionConstraintLayout = popUpView.findViewById(R.id.reactions_constraint_layout);
        reactionFlow = popUpView.findViewById(R.id.reactions_flow);
        plusButton = popUpView.findViewById(R.id.plus_button);

        if ((message.messageType != Message.TYPE_OUTBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_MESSAGE)
                || message.wipeStatus == Message.WIPE_STATUS_WIPED
                || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                || !discussion.canPostMessages()) {
            // no reactions in this case
            reactionsPopUpLinearLayout.setVisibility(View.GONE);
        } else {
            // reactions are possible
            LayoutTransition layoutTransition = new LayoutTransition();
            layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
            layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            layoutTransition.disableTransitionType(LayoutTransition.APPEARING);
            layoutTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            reactionConstraintLayout.setLayoutTransition(layoutTransition);

            ConstraintLayout.LayoutParams plusLayout = new ConstraintLayout.LayoutParams(viewSizePx, viewSizePx);
            plusLayout.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            plusLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            plusButton.setLayoutParams(plusLayout);

            plusButton.setPadding(viewSizePx / 8, viewSizePx / 8, viewSizePx / 8, viewSizePx / 8);
            plusButton.setOnClickListener(v -> {
                plusOpen = !plusOpen;
                fillReactions();
            });
        }

        boolean twoLines = false;

        View replyView = popUpView.findViewById(R.id.swipe_menu_reply);
        if (!discussion.canPostMessages() ||
                (message.messageType != Message.TYPE_INBOUND_MESSAGE
                        && message.messageType != Message.TYPE_OUTBOUND_MESSAGE)) {
            replyView.setVisibility(View.GONE);
        } else {
            twoLines = true;
            replyView.setOnClickListener(v -> {
                discussionDelegate.replyToMessage(message.discussionId, messageId);
                popupWindow.dismiss();
            });
        }
        View shareView = popUpView.findViewById(R.id.swipe_menu_share);
        View forwardView = popUpView.findViewById(R.id.swipe_menu_forward);
        View copyView = popUpView.findViewById(R.id.swipe_menu_copy);
        if (!message.isForwardable()) {
            shareView.setVisibility(View.GONE);
            forwardView.setVisibility(View.GONE);
            copyView.setVisibility(View.GONE);
        } else {
            twoLines = true;
            shareView.setOnClickListener(v -> {
                App.runThread(new ShareSelectedMessageTask(activity, messageId));
                popupWindow.dismiss();
            });
            forwardView.setOnClickListener(v -> discussionDelegate.initiateMessageForward(messageId, popupWindow::dismiss));
            copyView.setOnClickListener(v -> {
                if (message.hasAttachments() && message.contentBody != null && message.contentBody.length() != 0) {
                    ViewParent parent = parentView.getParent();
                    if (parent instanceof ConstraintLayout) {
                        View anchorView = new View(activity);
                        ((ViewGroup) parent).addView(anchorView);
                        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(v.getWidth(), v.getHeight());
                        layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                        layoutParams.topMargin = ((View) (v.getParent())).getTop();
                        layoutParams.leftMargin = v.getLeft();
                        anchorView.setLayoutParams(layoutParams);

                        PopupMenu popup = new PopupMenu(activity, anchorView);
                        popup.setOnDismissListener(menu -> ((ViewGroup) parent).removeView(anchorView));
                        popup.inflate(R.menu.popup_copy_message_text_and_attachments);
                        popup.setOnMenuItemClickListener((MenuItem item) -> {
                            if (item.getItemId() == R.id.popup_action_copy_message_text) {
                                App.runThread(new CopySelectedMessageTask(activity, messageId, false));
                                popupWindow.dismiss();
                                return true;
                            } else if (item.getItemId() == R.id.popup_action_copy_text_and_attachments) {
                                App.runThread(new CopySelectedMessageTask(activity, messageId, true));
                                popupWindow.dismiss();
                                return true;
                            }
                            return false;
                        });
                        // make this call asynchronous so the anchorView has time to be layout properly
                        new Handler(Looper.getMainLooper()).post(popup::show);
                    }
                } else if (message.hasAttachments()) {
                    App.runThread(new CopySelectedMessageTask(activity, messageId, true));
                    popupWindow.dismiss();
                } else {
                    App.runThread(new CopySelectedMessageTask(activity, messageId, false));
                    popupWindow.dismiss();
                }
            });
        }
        View selectView = popUpView.findViewById(R.id.swipe_menu_select);
        selectView.setOnClickListener(v -> {
            discussionDelegate.selectMessage(messageId, message.isForwardable());
            popupWindow.dismiss();
        });
        View detailsView = popUpView.findViewById(R.id.swipe_menu_details);
        if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE
                && message.messageType != Message.TYPE_INBOUND_MESSAGE
                && message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
            detailsView.setVisibility(View.GONE);
        } else {
            detailsView.setOnClickListener(v -> {
                discussionDelegate.doNotMarkAsReadOnPause();
                App.openMessageDetails(activity, messageId, message.hasAttachments(), message.isInbound());
                popupWindow.dismiss();
            });
        }
        View editView = popUpView.findViewById(R.id.swipe_menu_edit);
        if (!discussion.canPostMessages()
                || message.messageType != Message.TYPE_OUTBOUND_MESSAGE
                || message.wipeStatus == Message.WIPE_STATUS_WIPED
                || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                || message.isLocationMessage()) {
            editView.setVisibility(View.GONE);
        } else {
            editView.setOnClickListener(v -> {
                Utils.launchModifyMessagePopup(activity, message);
                popupWindow.dismiss();
            });
        }
        View deleteView = popUpView.findViewById(R.id.swipe_menu_delete);
        deleteView.setOnClickListener(v -> {
            App.runThread(() -> {
                boolean canRemoteDelete;
                boolean canRemoteDeleteOwn;
                if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                    Group2 group2 = AppDatabase.getInstance().group2Dao().get(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                    if (group2 != null) {
                        canRemoteDelete = group2.ownPermissionRemoteDeleteAnything;
                        canRemoteDeleteOwn = group2.ownPermissionEditOrRemoteDeleteOwnMessages;
                    } else {
                        canRemoteDelete = false;
                        canRemoteDeleteOwn = false;
                    }
                } else {
                    canRemoteDelete = discussion.canPostMessages();
                    canRemoteDeleteOwn = discussion.canPostMessages();
                }
                final AlertDialog.Builder builder;
                if (((canRemoteDeleteOwn && (message.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                        || (canRemoteDelete && ((message.messageType == Message.TYPE_INBOUND_MESSAGE) || (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE))))
                        && message.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED) {
                    builder = new SecureDeleteEverywhereDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_confirm_deletion)
                            .setType(SecureDeleteEverywhereDialogBuilder.TYPE.SINGLE_MESSAGE)
                            .setMessage(activity.getResources().getQuantityString(R.plurals.dialog_message_delete_messages, 1, 1))
                            .setDeleteCallback(deleteEverywhere -> App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, Collections.singletonList(messageId), deleteEverywhere)));
                } else {
                    builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_confirm_deletion)
                            .setMessage(activity.getResources().getQuantityString(R.plurals.dialog_message_delete_messages, 1, 1))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(new DeleteMessagesTask(discussion.bytesOwnedIdentity, Collections.singletonList(messageId), false)))
                            .setNegativeButton(R.string.button_label_cancel, null);
                }
                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
            });

            popupWindow.dismiss();
        });

        final int buttonsHeightPx;
        if (twoLines) {
            buttonsHeightPx = (int) (96 * metrics.density);
        } else {
            buttonsHeightPx = (int) (48 * metrics.density);
        }
        if (messageViewBottomPx < buttonsHeightPx) {
            additionalBottomPadding = buttonsHeightPx - messageViewBottomPx;
            discussionDelegate.setAdditionalBottomPadding(additionalBottomPadding);
        } else {
            additionalBottomPadding = 0;
        }

        // only fill reactions at the end because we need the additionalBottomPadding
        fillReactions();

        popupWindow.setWidth(parentView.getWidth());
        popupWindow.setHeight(parentView.getHeight());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            int[] pos = new int[2];
            parentView.getLocationOnScreen(pos);
            popupWindow.showAtLocation(parentView, Gravity.NO_GRAVITY, 0, pos[1]);
        } else {
            popupWindow.showAtLocation(parentView, Gravity.NO_GRAVITY, 0, 0);
        }
    }

    private void fillReactions() {
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
                    popupWindow.dismiss();
                });
                textView.setOnLongClickListener(v -> {
                    if (!plusOpen) {
                        react(null);
                        popupWindow.dismiss();
                    }
                    return true;
                });
            } else {
                textView.setBackgroundResource(R.drawable.background_circular_ripple);
                textView.setOnClickListener(v -> {
                    react(reaction);
                    popupWindow.dismiss();
                });
                textView.setOnLongClickListener(v -> {
                    if (plusOpen) {
                        togglePreferred(reaction);
                    } else {
                        react(reaction);
                        popupWindow.dismiss();
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
                reactionsPopUpLinearLayout.addView(separatorTextView, 1);
            }


            if (emojiPickerView == null) {
                EmojiPickerViewFactory.EmojiClickListener emojiClickListener = new EmojiPickerViewFactory.EmojiClickListener() {
                    @Override
                    public void onClick(String emoji) {
                        react(emoji);
                        popupWindow.dismiss();
                    }

                    @Override
                    public void onHighlightedClick(View emojiView, String emoji) {
                        react(null);
                        popupWindow.dismiss();
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
                reactionsPopUpLinearLayout.addView(emojiPickerView, 2);
            }
            panelHeightPx = constraintLayoutHeightPx + (int) (24 * metrics.density) + emojiPickerRows * ((int) (40* metrics.density)) + (int) metrics.density + (int) (28 * metrics.density);
        } else {
            plusButton.setImageResource(R.drawable.ic_plus_reaction);
            if (separatorTextView != null && separatorTextView.getParent() != null) {
                reactionsPopUpLinearLayout.removeView(separatorTextView);
            }
            if (emojiPickerView != null && emojiPickerView.getParent() != null) {
                reactionsPopUpLinearLayout.removeView(emojiPickerView);
            }
            panelHeightPx = constraintLayoutHeightPx;
        }

        int fingerOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 8, metrics);
        fingerOffset += additionalBottomPadding;
        int x = Math.max(sixteenDpInPx, Math.min(clickX - panelWidthPx / 2, parentView.getWidth() - panelWidthPx - sixteenDpInPx));

        // adjust fingerOffset so the popup remains on screen
        if (clickY - constraintLayoutHeightPx - fingerOffset < 0) {
            fingerOffset = clickY-constraintLayoutHeightPx;
        } else if (clickY - constraintLayoutHeightPx - fingerOffset + panelHeightPx > parentView.getHeight()) {
            fingerOffset = clickY - constraintLayoutHeightPx + panelHeightPx - parentView.getHeight();
        }


        ConstraintLayout.LayoutParams reactionsPopUpLayoutParams = (ConstraintLayout.LayoutParams) reactionsPopUpLinearLayout.getLayoutParams();
        reactionsPopUpLayoutParams.topMargin =  clickY - constraintLayoutHeightPx - fingerOffset;
        reactionsPopUpLayoutParams.leftMargin = x;
        reactionsPopUpLayoutParams.height = panelHeightPx;
        reactionsPopUpLayoutParams.width = panelWidthPx;

        reactionsPopUpLinearLayout.setLayoutParams(reactionsPopUpLayoutParams);
    }

    private void togglePreferred(String emoji) {
        List<String> preferredReactions = SettingsActivity.getPreferredReactions();
        if (preferredReactions.contains(emoji)) {
            preferredReactions.remove(emoji);
        } else {
            preferredReactions.add(emoji);
        }
        SettingsActivity.setPreferredReactions(preferredReactions);
        fillReactions();
    }

    private void react(String emoji) {
        if (vibrator != null) {
            vibrator.vibrate(20);
        }
        App.runThread(new UpdateReactionsTask(messageId, emoji, null, System.currentTimeMillis()));
    }
}
