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

package io.olvid.messenger.openid;


import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.CodeVerifierUtil;
import net.openid.appauth.GrantTypeValues;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;

import java.util.HashMap;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder;
import io.olvid.messenger.settings.SettingsActivity;

public class KeycloakAuthenticationActivity extends AppCompatActivity {
    public static final String AUTHENTICATE_ACTION = "authenticate_action";
    public static final String AUTHORIZATION_COMPLETE_ACTION = "authorization_complete_action";
    public static final String AUTHORIZATION_CANCELLED_ACTION = "authorization_cancelled_action";

    public static final String AUTH_STATE_JSON_INTENT_EXTRA = "auth_state_json";
    public static final String CLIENT_ID_INTENT_EXTRA = "client_id";
    public static final String CLIENT_SECRET_INTENT_EXTRA = "client_secret";
    public static final String CODE_VERIFIER_INTENT_EXTRA = "code_verifier";
    public static final String NONCE_INTENT_EXTRA = "nonce";

    private AuthorizationService authorizationService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppAuthConfiguration.Builder appAuthConfigBuilder = new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder());

        String preferredBrowser = SettingsActivity.getPreferredKeycloakBrowser();
        if (preferredBrowser != null) {
            appAuthConfigBuilder.setBrowserMatcher(descriptor -> descriptor.packageName.equals(preferredBrowser));
        }

        authorizationService = new AuthorizationService(this, appAuthConfigBuilder.build());
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (authorizationService != null) {
            authorizationService.dispose();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null) {
            finish();
            return;
        }

        switch (intent.getAction()) {
            case AUTHENTICATE_ACTION: {
                String serializedAuthState = intent.getStringExtra(AUTH_STATE_JSON_INTENT_EXTRA);
                String clientId = intent.getStringExtra(CLIENT_ID_INTENT_EXTRA);
                String clientSecret = intent.getStringExtra(CLIENT_SECRET_INTENT_EXTRA);
                if (serializedAuthState != null && clientId != null) {
                    try {
                        AuthState authState = AuthState.jsonDeserialize(serializedAuthState);
                        if (authState.getAuthorizationServiceConfiguration() != null) {
                            String codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier();
                            String nonce = UUID.randomUUID().toString();

                            AuthorizationRequest.Builder authorizationRequestBuilder;
                            authorizationRequestBuilder = new AuthorizationRequest.Builder(authState.getAuthorizationServiceConfiguration(), clientId, ResponseTypeValues.CODE, Uri.parse(BuildConfig.KEYCLOAK_REDIRECT_URL));
                            authorizationRequestBuilder.setScope("openid");
                            AuthorizationRequest authorizationRequest = authorizationRequestBuilder
                                    .setPrompt("login consent")
                                    .setNonce(nonce)
                                    .setCodeVerifier(codeVerifier)
                                    .build();


                            Intent successIntent = new Intent(this, KeycloakAuthenticationActivity.class);
                            successIntent.setAction(AUTHORIZATION_COMPLETE_ACTION);
                            successIntent.putExtra(AUTH_STATE_JSON_INTENT_EXTRA, serializedAuthState);
                            successIntent.putExtra(CLIENT_ID_INTENT_EXTRA, clientId);
                            if (clientSecret != null) {
                                successIntent.putExtra(CLIENT_SECRET_INTENT_EXTRA, clientSecret);
                            }
                            successIntent.putExtra(CODE_VERIFIER_INTENT_EXTRA, codeVerifier);
                            successIntent.putExtra(NONCE_INTENT_EXTRA, nonce);
                            PendingIntent successPendingIntent;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                successPendingIntent = PendingIntent.getActivity(this, 3, successIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                            } else {
                                successPendingIntent = PendingIntent.getActivity(this, 3, successIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                            }

                            Intent cancelledIntent = new Intent(this, KeycloakAuthenticationActivity.class);
                            cancelledIntent.setAction(AUTHORIZATION_CANCELLED_ACTION);
                            PendingIntent cancelledPendingIntent;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                cancelledPendingIntent = PendingIntent.getActivity(this, 4, cancelledIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                            } else {
                                cancelledPendingIntent = PendingIntent.getActivity(this, 4, cancelledIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                            }



                            authorizationService.performAuthorizationRequest(authorizationRequest, successPendingIntent, cancelledPendingIntent);
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Logger.d("Error parsing serialized AuthState : authentication start");
                    }
                }
                finish();
                break;
            }
            case AUTHORIZATION_COMPLETE_ACTION: {
                AuthorizationResponse authorizationResponse = AuthorizationResponse.fromIntent(intent);
                AuthorizationException authorizationException = AuthorizationException.fromIntent(intent);
                String serializedAuthState = intent.getStringExtra(AUTH_STATE_JSON_INTENT_EXTRA);
                String clientId = intent.getStringExtra(CLIENT_ID_INTENT_EXTRA);
                String clientSecret = intent.getStringExtra(CLIENT_SECRET_INTENT_EXTRA);
                String codeVerifier = intent.getStringExtra(CODE_VERIFIER_INTENT_EXTRA);
                String nonce = intent.getStringExtra(NONCE_INTENT_EXTRA);

                if (serializedAuthState != null && codeVerifier != null && clientId != null) {
                    try {
                        AuthState authState = AuthState.jsonDeserialize(serializedAuthState);
                        authState.update(authorizationResponse, authorizationException);
                        if (authState.getLastAuthorizationResponse() != null && authState.getAuthorizationServiceConfiguration() != null) {
                            // openid authentication successful, let's retrieve the authentication token
                            HashMap<String, String> additionalParameters = new HashMap<>();
                            if (clientSecret != null) {
                                additionalParameters.put("client_secret", clientSecret);
                            }

                            TokenRequest.Builder tokenRequestBuilder = new TokenRequest.Builder(authState.getAuthorizationServiceConfiguration(), clientId)
                                    .setCodeVerifier(codeVerifier)
                                    .setNonce(nonce)
                                    .setAuthorizationCode(authState.getLastAuthorizationResponse().authorizationCode)
                                    .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
                                    .setAdditionalParameters(additionalParameters)
                                    .setRedirectUri(Uri.parse(BuildConfig.KEYCLOAK_REDIRECT_URL));

                            authorizationService.performTokenRequest(tokenRequestBuilder.build(), (tokenResponse, ex) -> {
                                if (tokenResponse != null) {
                                    authState.update(tokenResponse, ex);
                                    Intent resultIntent = new Intent(this, KeycloakAuthenticationActivity.class);
                                    resultIntent.putExtra(AUTH_STATE_JSON_INTENT_EXTRA, authState.jsonSerializeString());
                                    setResult(RESULT_OK, resultIntent);
                                } else if (ex != null) {
                                    ex.printStackTrace();
                                }
                                finish();
                            });
                            // break to avoid finishing now
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Logger.d("Error parsing serialized AuthState : authorization complete");
                    }
                }
                finish();
                break;
            }
            case AUTHORIZATION_CANCELLED_ACTION: {
                setResult(RESULT_CANCELED);
                finish();
                break;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
