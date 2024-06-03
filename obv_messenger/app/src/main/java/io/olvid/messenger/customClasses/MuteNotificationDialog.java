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


import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;

import io.olvid.messenger.R;

public class MuteNotificationDialog {
    @NonNull private final Context context;
    @NonNull private final OnMuteExpirationSelectedListener onMuteExpirationSelectedListener;
    @NonNull private final AlertDialog.Builder builder;
    private Dialog dialog = null;

    private boolean muteWholeProfile;
    private boolean muteExceptMentioned;

    public enum MuteType {
        DISCUSSION,
        DISCUSSIONS,
        PROFILE,
        DISCUSSION_OR_PROFILE,
    }

    public MuteNotificationDialog(@NonNull Context context, @NonNull OnMuteExpirationSelectedListener onMuteExpirationSelectedListener, @NonNull MuteType muteType, boolean muteExceptMentioned) {
        this.context = context;
        this.onMuteExpirationSelectedListener = onMuteExpirationSelectedListener;

        final Long[] timeoutOptionsDurations = new Long[]{
                3_600_000L,
                28_800_000L,
                604_800_000L,
                null};
        final String[] timeoutOptionsLabels = new String[]{
                context.getString(R.string.pref_mute_notifications_one_hour),
                context.getString(R.string.pref_mute_notifications_eight_hours),
                context.getString(R.string.pref_mute_notifications_one_week),
                context.getString(R.string.pref_mute_notifications_indefinitely)};

        this.muteWholeProfile = muteType == MuteType.PROFILE;
        this.muteExceptMentioned = muteExceptMentioned;

        ArrayAdapter<String> timeoutOptionsAdapter = new ArrayAdapter<>(context, R.layout.dialog_item_singlechoice, timeoutOptionsLabels);
        builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setAdapter(timeoutOptionsAdapter, (dialog, which) -> {
                    Long duration = timeoutOptionsDurations[which];
                    Long timestamp;
                    if (duration == null) {
                        timestamp = null;
                    } else {
                        timestamp = System.currentTimeMillis() + duration;
                    }

                    onMuteExpirationSelectedListener.onMuteExpirationSelected(timestamp, this.muteWholeProfile, this.muteExceptMentioned);
                })
                .setNegativeButton(R.string.button_label_cancel, null);

        if (muteType == MuteType.PROFILE) {
            builder.setTitle(R.string.dialog_title_mute_profile_notification_duration);
        } else if (muteType == MuteType.DISCUSSIONS) {
            builder.setTitle(R.string.dialog_title_mute_discussions_notification_duration);
        } else {
            builder.setTitle(R.string.dialog_title_mute_discussion_notification_duration);
        }

        @SuppressLint("InflateParams")
        View additionalView = LayoutInflater.from(context).inflate(R.layout.dialog_view_switch_mute_type, null);
        View switchAndLabel = additionalView.findViewById(R.id.switch_and_label);
        if (muteType == MuteType.DISCUSSION_OR_PROFILE) {
            switchAndLabel.setVisibility(View.VISIBLE);
            @SuppressLint("UseSwitchCompatOrMaterialCode")
            Switch switsh = additionalView.findViewById(R.id.mute_profile_switch);
            switsh.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> this.muteWholeProfile = isChecked);
            switchAndLabel.setOnClickListener((View v) -> switsh.toggle());
        } else {
            switchAndLabel.setVisibility(View.GONE);
        }

        TextView customTime = additionalView.findViewById(R.id.custom_time);
        customTime.setOnClickListener(v -> openCustomTimeExpirationPicker());
        TextView customDate = additionalView.findViewById(R.id.custom_date);
        customDate.setOnClickListener(v -> openCustomDateExpirationPicker());

        View mentionedSwitchAndLabel = additionalView.findViewById(R.id.mentioned_view);
        @SuppressLint("UseSwitchCompatOrMaterialCode")
        Switch switsh = additionalView.findViewById(R.id.mentioned_switch);
        switsh.setChecked(this.muteExceptMentioned);
        switsh.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> this.muteExceptMentioned = isChecked);
        mentionedSwitchAndLabel.setOnClickListener((View v) -> switsh.toggle());

        builder.setView(additionalView);
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        builder.setOnDismissListener(onDismissListener);
    }

    public void show() {
        dialog = builder.create();
        dialog.show();
    }

    private void openCustomTimeExpirationPicker() {
        final Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(context, (TimePicker timePicker, int hour, int minute) -> {
            if (calendar.get(Calendar.HOUR_OF_DAY) > hour || (calendar.get(Calendar.HOUR_OF_DAY) == hour && calendar.get(Calendar.MINUTE) >= minute)) {
                calendar.add(Calendar.DATE, 1);
            }
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            onMuteExpirationSelectedListener.onMuteExpirationSelected(calendar.getTimeInMillis(), muteWholeProfile, muteExceptMentioned);
            if (this.dialog != null) {
                this.dialog.dismiss();
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show();
    }

    private void openCustomDateExpirationPicker() {
        final Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(context, (DatePicker datePicker, int year, int month, int dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth, 0, 0, 0);
            onMuteExpirationSelectedListener.onMuteExpirationSelected(calendar.getTimeInMillis(), muteWholeProfile, muteExceptMentioned);
            if (this.dialog != null) {
                this.dialog.dismiss();
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    public interface OnMuteExpirationSelectedListener {
        void onMuteExpirationSelected(Long muteExpirationTimestamp, boolean muteWholeProfile, boolean exceptMentioned);
    }
}
