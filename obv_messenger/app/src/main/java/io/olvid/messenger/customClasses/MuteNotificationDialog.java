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

package io.olvid.messenger.customClasses;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import io.olvid.messenger.R;

public class MuteNotificationDialog {
    @NonNull private final AlertDialog.Builder builder;

    private boolean muteWholeProfile;

    public enum MuteType {
        DISCUSSION,
        PROFILE,
        DISCUSSION_OR_PROFILE,
    }

    public MuteNotificationDialog(@NonNull Context context, @NonNull OnMuteExpirationSelectedListener onMuteExpirationSelectedListener, @NonNull MuteType muteType) {
        final Long[] timeoutOptionsDurations = new Long[]{
                3_600_000L,
                28_800_000L,
                604_800_000L,
                null};
        String[] timeoutOptionsLabels = new String[]{
                context.getString(R.string.pref_mute_notifications_one_hour),
                context.getString(R.string.pref_mute_notifications_eight_hours),
                context.getString(R.string.pref_mute_notifications_one_week),
                context.getString(R.string.pref_mute_notifications_indefinitely)};

        muteWholeProfile = muteType == MuteType.PROFILE;


        ArrayAdapter<String> timeoutOptionsAdapter = new ArrayAdapter<>(context, R.layout.dialog_singlechoice, timeoutOptionsLabels);
        builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setAdapter(timeoutOptionsAdapter, (dialog, which) -> {
                    Long duration = timeoutOptionsDurations[which];
                    Long timestamp;
                    if (duration == null) {
                        timestamp = null;
                    } else {
                        timestamp = System.currentTimeMillis() + duration;
                    }

                    onMuteExpirationSelectedListener.onMuteExpirationSelected(timestamp, muteWholeProfile);
                })
                .setNegativeButton(R.string.button_label_cancel, null);

        if (muteType == MuteType.PROFILE) {
            builder.setTitle(R.string.dialog_title_mute_profile_notification_duration);
        } else {
            builder.setTitle(R.string.dialog_title_mute_discussion_notification_duration);
        }

        if (muteType == MuteType.DISCUSSION_OR_PROFILE) {
            @SuppressLint("InflateParams")
            View switchView = LayoutInflater.from(context).inflate(R.layout.dialog_view_switch_mute_type, null);
            @SuppressLint("UseSwitchCompatOrMaterialCode")
            Switch switsh = switchView.findViewById(R.id.mute_profile_switch);
            switsh.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> muteWholeProfile = isChecked);
            switchView.setOnClickListener((View v) -> switsh.toggle());

            builder.setView(switchView);
        }
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        builder.setOnDismissListener(onDismissListener);
    }

    public void show() {
        builder.create().show();
    }

    public interface OnMuteExpirationSelectedListener {
        void onMuteExpirationSelected(Long muteExpirationTimestamp, boolean muteWholeProfile);
    }
}
