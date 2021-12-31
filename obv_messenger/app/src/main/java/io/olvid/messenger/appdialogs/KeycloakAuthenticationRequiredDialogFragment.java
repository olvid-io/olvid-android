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

package io.olvid.messenger.appdialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;

import net.openid.appauth.AuthState;

import org.jose4j.jwk.JsonWebKeySet;

import io.olvid.engine.Logger;
import io.olvid.messenger.R;
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment;
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.openid.KeycloakTasks;
import io.olvid.messenger.settings.SettingsActivity;

public class KeycloakAuthenticationRequiredDialogFragment extends DialogFragment {
    private REASON reason;
    private byte[] bytesOwnedIdentity;
    String serverUrl;
    String clientId;
    String clientSecret;
    private Runnable dismissCallback;
    private KeycloakAuthenticationStartFragment keycloakAuthenticationStartFragment;

    public enum REASON {
        TOKEN_EXPIRED,
        USER_ID_CHANGED,
    }

    public static KeycloakAuthenticationRequiredDialogFragment newInstance(REASON reason, @NonNull byte[] bytesOwnedIdentity, @NonNull String serverUrl, @NonNull String clientId, @Nullable String clientSecret, Runnable dismissCallback) {

        KeycloakAuthenticationRequiredDialogFragment fragment = new KeycloakAuthenticationRequiredDialogFragment();
        fragment.setReason(reason);
        fragment.setBytesOwnedIdentity(bytesOwnedIdentity);
        fragment.setServerUrl(serverUrl);
        fragment.setClientId(clientId);
        fragment.setClientSecret(clientSecret);
        fragment.setDismissCallback(dismissCallback);
        return fragment;
    }

    public void setReason(REASON reason) {
        this.reason = reason;
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
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
        View dialogView = inflater.inflate(R.layout.dialog_fragment_keycloak_authentication_required, container, false);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());

        Button authenticateButton = dialogView.findViewById(R.id.button_authenticate);
        authenticateButton.setOnClickListener(v -> authenticate());

        ImageButton authenticationBrowserChoiceButton = dialogView.findViewById(R.id.button_authentication_browser);
        authenticationBrowserChoiceButton.setOnClickListener(KeycloakBrowserChooserDialog::openBrowserChoiceDialog);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextView dialogMessage = dialogView.findViewById(R.id.dialog_message);
        switch (reason) {
            case TOKEN_EXPIRED:
                dialogTitle.setText(R.string.dialog_title_keycloak_authentication_required_token_expired);
                dialogMessage.setText(R.string.dialog_message_keycloak_authentication_required_token_expired);
                break;
            case USER_ID_CHANGED:
                dialogTitle.setText(R.string.dialog_title_keycloak_authentication_required_user_id_changed);
                dialogMessage.setText(R.string.dialog_message_keycloak_authentication_required_user_id_changed);
                break;
        }

        keycloakAuthenticationStartFragment = new KeycloakAuthenticationStartFragment();

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.authentication_fragment_placeholder, keycloakAuthenticationStartFragment);
        transaction.commit();

        return dialogView;
    }

    private void authenticate() {
        if (keycloakAuthenticationStartFragment != null) {
            if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.VISIBLE);
            }
            if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(R.string.label_checking_server);
            }

            KeycloakTasks.discoverKeycloakServer(serverUrl, new KeycloakTasks.DiscoverKeycloakServerCallback() {
                @Override
                public void success(@NonNull String serverUrl, @NonNull AuthState authState, @NonNull JsonWebKeySet jwks) {
                    new Handler(Looper.getMainLooper()).post(() -> keycloakAuthenticationStartFragment.authenticate(authState.jsonSerializeString(), clientId, clientSecret, new KeycloakTasks.AuthenticateCallback() {
                        @Override
                        public void success(@NonNull AuthState authState) {
                            KeycloakManager.getInstance().reAuthenticationSuccessful(bytesOwnedIdentity, jwks, authState);
                            dismiss();
                        }

                        @Override
                        public void failed(int rfc) {
                            Logger.d("Authentication failed " + rfc);
                        }
                    }));
                }

                @Override
                public void failed() {
                    Logger.d("Authentication failed: unable to discover keycloak");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.setVisibility(View.GONE);
                        }
                    });
                }
            });
        }
    }
}
