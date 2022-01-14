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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.messenger.R;
import io.olvid.messenger.fragments.SubscriptionStatusFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class SubscriptionUpdatedDialogFragment extends DialogFragment {
    private byte[] bytesOwnedIdentity;
    private EngineAPI.ApiKeyStatus apiKeyStatus;
    private List<EngineAPI.ApiKeyPermission> apiKeyPermissions;
    private Long apiKeyExpirationTimestamp;
    private Runnable dismissCallback;

    public static SubscriptionUpdatedDialogFragment newInstance(byte[] bytesOwnedIdentity, EngineAPI.ApiKeyStatus apiKeyStatus, @Nullable Long apiKeyExpirationTimestamp, List<EngineAPI.ApiKeyPermission> apiKeyPermissions, Runnable dismissCallback) {
        SubscriptionUpdatedDialogFragment fragment = new SubscriptionUpdatedDialogFragment();
        fragment.setBytesOwnedIdentity(bytesOwnedIdentity);
        fragment.setApiKeyStatus(apiKeyStatus);
        fragment.setApiKeyExpirationTimestamp(apiKeyExpirationTimestamp);
        fragment.setApiKeyPermissions(apiKeyPermissions);
        fragment.setDismissCallback(dismissCallback);
        return fragment;
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
    }

    public void setApiKeyStatus(EngineAPI.ApiKeyStatus apiKeyStatus) {
        this.apiKeyStatus = apiKeyStatus;
    }

    public void setApiKeyPermissions(List<EngineAPI.ApiKeyPermission> apiKeyPermissions) {
        this.apiKeyPermissions = apiKeyPermissions;
    }

    public void setApiKeyExpirationTimestamp(Long apiKeyExpirationTimestamp) {
        this.apiKeyExpirationTimestamp = apiKeyExpirationTimestamp;
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
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissCallback != null) {
            dismissCallback.run();
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_api_key_status_update, container, false);
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(v -> dismiss());

        SubscriptionStatusFragment subscriptionStatusFragment = SubscriptionStatusFragment.newInstance(bytesOwnedIdentity, apiKeyStatus, apiKeyExpirationTimestamp, apiKeyPermissions, false, true);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.subscription_status_placeholder, subscriptionStatusFragment);
        transaction.commit();

        return dialogView;
    }
}
