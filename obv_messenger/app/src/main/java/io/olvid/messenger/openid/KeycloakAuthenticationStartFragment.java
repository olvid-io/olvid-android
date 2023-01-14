/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.openid;


import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.openid.appauth.AuthState;

import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class KeycloakAuthenticationStartFragment extends Fragment {
    private FragmentActivity activity;
    private KeycloakTasks.AuthenticationLifecycleObserver authenticateObserver;

    public View authenticationSpinnerGroup;
    public TextView authenticationSpinnerText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();

        authenticateObserver = new KeycloakTasks.AuthenticationLifecycleObserver(activity);
        getLifecycle().addObserver(authenticateObserver);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_keycloak_authentication, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authenticationSpinnerGroup = view.findViewById(R.id.authentication_spinner_group);
        authenticationSpinnerText = view.findViewById(R.id.authentication_spinner_text_view);
    }

    public void authenticate(@NonNull String serializedAuthState, @NonNull String clientId, @Nullable String clientSecret, @NonNull KeycloakTasks.AuthenticateCallback authenticateCallback) {
        if (authenticationSpinnerGroup == null || authenticationSpinnerText == null) {
            authenticateCallback.failed(KeycloakTasks.RFC_UNKNOWN_ERROR);
        }
        authenticationSpinnerGroup.setVisibility(View.VISIBLE);
        authenticationSpinnerText.setText(R.string.label_authenticating);

        authenticateObserver.setCallback(new KeycloakTasks.AuthenticateCallback() {
            @Override
            public void success(@NonNull AuthState authState) {
                activity.runOnUiThread(() -> authenticationSpinnerGroup.setVisibility(View.GONE));
                authenticateCallback.success(authState);
            }

            @Override
            public void failed(int rfc) {
                activity.runOnUiThread(() -> {
                    authenticationSpinnerGroup.setVisibility(View.GONE);
                    App.toast(R.string.toast_message_authentication_failed, Toast.LENGTH_SHORT, Gravity.CENTER);
                });
                authenticateCallback.failed(rfc);
            }
        });

        Intent authenticateIntent = new Intent(activity, KeycloakAuthenticationActivity.class);
        authenticateIntent.setAction(KeycloakAuthenticationActivity.AUTHENTICATE_ACTION);
        authenticateIntent.putExtra(KeycloakAuthenticationActivity.AUTH_STATE_JSON_INTENT_EXTRA, serializedAuthState);
        authenticateIntent.putExtra(KeycloakAuthenticationActivity.CLIENT_ID_INTENT_EXTRA, clientId);
        if (clientSecret != null) {
            authenticateIntent.putExtra(KeycloakAuthenticationActivity.CLIENT_SECRET_INTENT_EXTRA, clientSecret);
        }
        App.doNotKillActivitiesOnLockOrCloseHiddenProfileOnBackground();
        authenticateObserver.authenticationResultHandler.launch(authenticateIntent);
    }
}
