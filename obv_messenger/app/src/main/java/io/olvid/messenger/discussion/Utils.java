/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.Markdown;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils2Kt;
import io.olvid.messenger.customClasses.spans.OrderedListItemSpan;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonUserMention;
import io.olvid.messenger.discussion.mention.MentionUrlSpan;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity;
import io.olvid.messenger.settings.SettingsActivity;

public class Utils {
    private static final Calendar cal1 = Calendar.getInstance();
    private static final Calendar cal2 = Calendar.getInstance();

    static boolean notTheSameDay(long timestamp1, long timestamp2) {
        cal1.setTimeInMillis(timestamp1);
        cal2.setTimeInMillis(timestamp2);
        return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) || cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR);
    }

    static void openForwardMessageDialog(FragmentActivity activity, @NonNull List<Long> selectedMessageIds, @Nullable Runnable openDialogCallback) {
        Runnable runnable = () -> {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            if (prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION, false)) {
                if (openDialogCallback != null) {
                    openDialogCallback.run();
                }
                ForwardMessagesDialogFragment forwardMessagesDialogFragment = ForwardMessagesDialogFragment.newInstance();
                forwardMessagesDialogFragment.show(activity.getSupportFragmentManager(), "ForwardMessagesDialogFragment");
            } else {
                View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(R.string.dialog_message_forward_message_explanation);
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_forward_message_explanation)
                        .setView(dialogView)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> {
                            if (openDialogCallback != null) {
                                openDialogCallback.run();
                            }
                            ForwardMessagesDialogFragment forwardMessageDialogFragment = ForwardMessagesDialogFragment.newInstance();
                            forwardMessageDialogFragment.show(activity.getSupportFragmentManager(), "ForwardMessagesDialogFragment");
                        });
                builder.create().show();
            }
        };

        App.runThread(() -> {
            int count = 0;
            try {
                count = AppDatabase.getInstance().messageDao().countMessagesWithIncompleteFyles(selectedMessageIds);
            } catch (Exception ignored) { }

            int finalCount = count;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalCount == selectedMessageIds.size()) {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_incomplete_attachments)
                            .setMessage(R.string.dialog_message_incomplete_attachments)
                            .setNegativeButton(R.string.button_label_ok, null);
                    builder.create().show();
                } else if (finalCount > 0) {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_incomplete_attachments)
                            .setMessage(R.string.dialog_message_incomplete_attachments_partial)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> runnable.run());
                    builder.create().show();
                } else {
                    runnable.run();
                }
            });
        });
    }
    public static void applyBodyWithSpans(@NonNull TextView textView, @Nullable byte[] bytesOwnedIdentity, @NonNull Message message, @Nullable List<Pattern> searchPatterns, boolean linkifyLinks, boolean markdown, String finalUrlToTruncate) {
        String body = message.getStringContent(textView.getContext());
        if (finalUrlToTruncate != null && endsWithIgnoreCase(body, finalUrlToTruncate) && SettingsActivity.truncateMessageBodyTrailingLinks()) {
            body = body.substring(0, body.length() - finalUrlToTruncate.length()).trim();
        }
        SpannableString result = new SpannableString(body);
        try {
            // call linkify first as it removes existing spans
            if (linkifyLinks) {
                StringUtils2Kt.linkify(result);
            }
            if (bytesOwnedIdentity != null) {
                applyMentionSpans(textView.getContext(), bytesOwnedIdentity, message, result);
            }
        } catch (Exception ex) {
            Logger.w("Error while applying spans to message content body");
        }
        if (markdown) {
            SpannableStringBuilder spannableString = Markdown.formatMarkdown(result, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            OrderedListItemSpan.Companion.measure(textView, spannableString);
            textView.setText(spannableString);
        } else {
            textView.setText(result);
        }
    }

    private static boolean endsWithIgnoreCase(@NonNull String source, @NonNull String suffix) {
        return source.regionMatches(true, source.length() - suffix.length(), suffix, 0, suffix.length());
    }

    public static void applyMentionSpans(@NonNull Context context, @NonNull byte[] bytesOwnedIdentity, @NonNull Message message, SpannableString result) {
        List<JsonUserMention> mentions = message.getMentions();
        if (mentions != null && !mentions.isEmpty()) {
            for (JsonUserMention mention : mentions) {
                if (mention.getRangeStart() >= 0 && mention.getRangeEnd() <= result.length()) {
                    // this test also considers groupV2 pending members as contacts --> need to check this at click time
                    if (mention.getUserIdentifier() == null || AppSingleton.getContactCustomDisplayName(mention.getUserIdentifier()) == null) {
                        // Unknown contact
                        result.setSpan(new MentionUrlSpan(mention.getUserIdentifier(), mention.getLength(), context.getResources().getColor(R.color.darkGrey), null), mention.getRangeStart(), mention.getRangeEnd(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        int color = InitialView.getTextColor(context, mention.getUserIdentifier(), AppSingleton.getContactCustomHue(mention.getUserIdentifier()));
                         result.setSpan(new MentionUrlSpan(mention.getUserIdentifier(), mention.getLength(), color, () -> {
                            if (Arrays.equals(bytesOwnedIdentity, mention.getUserIdentifier())) {
                                context.startActivity(new Intent(context, OwnedIdentityDetailsActivity.class));
                            } else {
                                // check that there is indeed a contact
                                App.runThread(() -> {
                                    if (AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, mention.getUserIdentifier()) != null) {
                                        new Handler(Looper.getMainLooper()).post(() -> App.openContactDetailsActivity(context, bytesOwnedIdentity, mention.getUserIdentifier()));
                                    }
                                });
                            }
                            return null;
                        }), mention.getRangeStart(), mention.getRangeEnd(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
    }


    public static CharSequence protectMentionUrlSpansWithFEFF(Spanned input) {
        List<MentionUrlSpan> mentionUrlSpans = new ArrayList<>(Arrays.asList(input.getSpans(0, input.length(), MentionUrlSpan.class)));
        //noinspection ComparatorCombinators
        Collections.sort(mentionUrlSpans, (o1, o2) -> Integer.compare(input.getSpanStart(o1), input.getSpanStart(o2)));
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int offset = 0;
        for (MentionUrlSpan mentionUrlSpan : mentionUrlSpans) {
            ssb.append(input.subSequence(offset, input.getSpanStart(mentionUrlSpan)));
            offset = input.getSpanEnd(mentionUrlSpan);
            SpannableString spannableString = new SpannableString(input.subSequence(input.getSpanStart(mentionUrlSpan), offset) + "\ufeff");
            spannableString.setSpan(new MentionUrlSpan(mentionUrlSpan.getUserIdentifier(), spannableString.length(), mentionUrlSpan.getColor(), mentionUrlSpan.getOnClick()), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(spannableString);
        }
        ssb.append(input.subSequence(offset, input.length()));
        return ssb;
    }

    public static Pair<String, List<JsonUserMention>> removeProtectionFEFFsAndTrim(@NonNull CharSequence protectedMessageBody, @Nullable Collection<JsonUserMention> mentions) {
        // sort the mentions
        ArrayList<JsonUserMention> sortedMentions = mentions == null ? new ArrayList<>() : new ArrayList<>(mentions);
        //noinspection ComparatorCombinators
        Collections.sort(sortedMentions, (o1, o2) -> Integer.compare(o1.getRangeStart(), o2.getRangeStart()));

        // rebuild the String without the FEFFs
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        // trim the String start
        while ((offset < protectedMessageBody.length()) && (protectedMessageBody.charAt(offset) <= ' ')) {
            offset++;
        }
        int mentionRangeCorrection = offset;
        ArrayList<JsonUserMention> correctedMentions = new ArrayList<>(sortedMentions.size());
        for (JsonUserMention mention : sortedMentions) {
            if (mention.getRangeEnd() <= offset || mention.getRangeEnd() > protectedMessageBody.length()) {
                // this can happen after deleting a mention
                continue;
            }
            if (protectedMessageBody.charAt(mention.getRangeEnd()-1) == '\ufeff') {
                sb.append(protectedMessageBody.subSequence(offset, mention.getRangeEnd() - 1));
                offset = mention.getRangeEnd();
                correctedMentions.add(new JsonUserMention(mention.getUserIdentifier(), mention.getRangeStart() - mentionRangeCorrection, mention.getRangeEnd() - mentionRangeCorrection - 1));
                mentionRangeCorrection++;
            } else {
                sb.append(protectedMessageBody.subSequence(offset, mention.getRangeEnd()));
                offset = mention.getRangeEnd();
                correctedMentions.add(new JsonUserMention(mention.getUserIdentifier(), mention.getRangeStart() - mentionRangeCorrection, mention.getRangeEnd() - mentionRangeCorrection));
            }
        }
        // trim the String end
        int end = protectedMessageBody.length();
        while ((end > offset) && (protectedMessageBody.charAt(end - 1) <= ' ')) {
            end--;
        }
        sb.append(protectedMessageBody.subSequence(offset, end));
        return new Pair<>(sb.length() == 0 ? null : sb.toString(), !correctedMentions.isEmpty() ? correctedMentions : null);
    }
}
