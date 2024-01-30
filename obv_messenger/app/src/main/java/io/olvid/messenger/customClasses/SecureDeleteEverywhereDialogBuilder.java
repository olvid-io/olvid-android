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
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import io.olvid.messenger.R;

public class SecureDeleteEverywhereDialogBuilder extends SecureAlertDialogBuilder {
    private CharSequence message;
    private boolean deleteEverywhere;
    private DeleteCallback deleteCallback;
    private TYPE type = TYPE.SINGLE_MESSAGE;
    private Button deleteButton;

    public enum TYPE {
        DISCUSSION,
        SINGLE_MESSAGE,
        MULTIPLE_MESSAGE
    }

    public SecureDeleteEverywhereDialogBuilder(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    public SecureDeleteEverywhereDialogBuilder setMessage(@Nullable CharSequence message) {
        this.message = message;
        return this;
    }

    @Override
    public SecureDeleteEverywhereDialogBuilder setMessage(int messageId) {
        this.message = getContext().getString(messageId);
        return this;
    }

    @Override
    public SecureDeleteEverywhereDialogBuilder setTitle(int titleId) {
        super.setTitle(titleId);
        return this;
    }

    @Override
    public SecureDeleteEverywhereDialogBuilder setTitle(@Nullable CharSequence title) {
        super.setTitle(title);
        return this;
    }

    public SecureDeleteEverywhereDialogBuilder setDeleteCallback(DeleteCallback deleteCallback) {
        this.deleteCallback = deleteCallback;
        return this;
    }

    public void setDeleteEverywhere(boolean deleteEverywhere) {
        this.deleteEverywhere = deleteEverywhere;
        if (deleteButton != null) {
            if (deleteEverywhere) {
                deleteButton.setText(R.string.button_label_delete_everywhere);
            } else {
                deleteButton.setText(R.string.button_label_delete);
            }
        }
    }

    public SecureDeleteEverywhereDialogBuilder setType(TYPE type) {
        this.type = type;
        return this;
    }

    @NonNull
    @Override
    public AlertDialog create() {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        @SuppressLint("InflateParams") View dialogView = inflater.inflate(R.layout.dialog_view_delete_everywhere, null);
        TextView messageView = dialogView.findViewById(R.id.dialog_message);
        messageView.setText(this.message);

        @SuppressLint("UseSwitchCompatOrMaterialCode")
        Switch everywhereSwitch = dialogView.findViewById(R.id.everywhere_switch);
        everywhereSwitch.setChecked(false);
        setNegativeButton(R.string.button_label_cancel, null);
        setPositiveButton(R.string.button_label_delete, (dialog, which) -> {
            if (deleteCallback != null) {
                deleteCallback.performDelete(deleteEverywhere);
            }
        });

        TextView explanation = dialogView.findViewById(R.id.dialog_everywhere_explanation);
        switch (type) {
            case DISCUSSION:
                explanation.setText(R.string.dialog_text_delete_everywhere_explanation_discussion);
                break;
            case SINGLE_MESSAGE:
                explanation.setText(R.string.dialog_text_delete_everywhere_explanation);
                break;
            case MULTIPLE_MESSAGE:
                explanation.setText(R.string.dialog_text_delete_everywhere_explanation_multiple);
                break;
        }


        setView(dialogView);
        AlertDialog alertDialog = super.create();
        alertDialog.setOnShowListener(dialog -> {
            deleteButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            setDeleteEverywhere(false);
        });

        everywhereSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> setDeleteEverywhere(isChecked));

        return alertDialog;
    }


    public interface DeleteCallback {
        void performDelete(boolean deleteEverywhere);
    }
}
