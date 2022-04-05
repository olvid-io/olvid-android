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

package io.olvid.messenger.appdialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Arrays;
import java.util.HashMap;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;


public class IdentityDeactivatedDialogFragment extends DialogFragment implements EngineNotificationListener {
    private OwnedIdentity ownedIdentity;
    private Runnable dismissCallback;
    private Long engineNotificationRegistrationNumber = null;

    public static IdentityDeactivatedDialogFragment newInstance(OwnedIdentity ownedIdentity, Runnable dismissCallback) {
        IdentityDeactivatedDialogFragment dialogFragment = new IdentityDeactivatedDialogFragment();
        dialogFragment.setOwnedIdentity(ownedIdentity);
        dialogFragment.setDismissCallback(dismissCallback);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, dialogFragment);
        return dialogFragment;
    }

    public void setOwnedIdentity(OwnedIdentity ownedIdentity) {
        this.ownedIdentity = ownedIdentity;
    }

    public void setDismissCallback(Runnable dismissCallback) {
        this.dismissCallback = dismissCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fragment_identity_deactivated, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View dialogView, @Nullable Bundle savedInstanceState) {
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(v -> dismiss());

        Button reactivateButton = dialogView.findViewById(R.id.button_reactivate_identity);
        reactivateButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(v.getContext(), R.style.CustomAlertDialog);
            builder.setMessage(R.string.dialog_message_identity_reactivation_confirmation)
                    .setTitle(R.string.dialog_title_identity_reactivation_confirmation)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                        try {
                            AppSingleton.getEngine().registerToPushNotification(ownedIdentity.bytesOwnedIdentity, AppSingleton.retrieveFirebaseToken(), true, false);
                        } catch (Exception e) {
                            App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                            return;
                        }
                        reactivateButton.setEnabled(false);
                    });
            builder.create().show();
        });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, this);
        if (dismissCallback != null) {
            dismissCallback.run();
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber != null;
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (!EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED.equals(notificationName)) {
            return;
        }
        Boolean active = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY);
        byte[] ownedIdentityBytes = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
        if (active != null && active && Arrays.equals(ownedIdentity.bytesOwnedIdentity, ownedIdentityBytes)) {
            dismiss();
        }
    }
}
