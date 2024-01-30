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

package io.olvid.messenger.openid;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.fasterxml.jackson.core.type.TypeReference;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientSecretPost;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.VerificationJwkSelector;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.lang.JoseException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.NoExceptionConnectionBuilder;
import io.olvid.messenger.openid.jsons.JsonGroupsRequest;
import io.olvid.messenger.openid.jsons.JsonGroupsResponse;
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff;
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff;
import io.olvid.messenger.openid.jsons.JsonMeRequest;
import io.olvid.messenger.openid.jsons.JsonMeResponse;
import io.olvid.messenger.openid.jsons.JsonSearchRequest;
import io.olvid.messenger.openid.jsons.JsonSearchResponse;

public class KeycloakTasks {
    private static final String ME_PATH = "olvid-rest/me";
    private static final String PUT_KEY_PATH = "olvid-rest/putKey";
    private static final String GET_KEY_PATH = "olvid-rest/getKey";
    private static final String SEARCH_PATH = "olvid-rest/search";
    private static final String REVOCATION_TEST_PATH = "olvid-rest/revocationTest";
    private static final String GROUPS_PATH = "olvid-rest/groups";

    public static final int RFC_UNKNOWN_ERROR = 0;
    public static final int RFC_INVALID_AUTH_STATE = 1;
    public static final int RFC_AUTHENTICATION_REQUIRED = 2;
    public static final int RFC_NETWORK_ERROR = 3;
    public static final int RFC_BAD_RESPONSE = 4;
    public static final int RFC_INVALID_SIGNATURE = 5;
    public static final int RFC_SERVER_ERROR = 6;
    public static final int RFC_IDENTITY_NOT_MANAGED = 7;

    public static final int RFC_AUTHENTICATION_CANCELLED = 8;
    public static final int RFC_USER_NOT_AUTHENTICATED = 9;
    public static final int RFC_IDENTITY_ALREADY_UPLOADED = 10;
    public static final int RFC_IDENTITY_REVOKED = 11;



    public static final int ERROR_CODE_INTERNAL_ERROR = 1;
    public static final int ERROR_CODE_PERMISSION_DENIED = 2;
    public static final int ERROR_CODE_INVALID_REQUEST = 3;
    public static final int ERROR_CODE_IDENTITY_ALREADY_UPLOADED = 4;
    public static final int ERROR_CODE_IDENTITY_WAS_REVOKED = 6;


    public static void discoverKeycloakServer(@NonNull String keycloakServerUrl, @NonNull DiscoverKeycloakServerCallback callback) {
        Logger.d("Discovering keycloak server at " + keycloakServerUrl);

        keycloakServerUrl = keycloakServerUrl.trim();
        if (!keycloakServerUrl.endsWith("/")) {
            keycloakServerUrl += "/";
        }
        String finalServerUrl = keycloakServerUrl;
        AuthorizationServiceConfiguration.fetchFromUrl(Uri.parse(keycloakServerUrl + ".well-known/openid-configuration"), (serviceConfiguration, ex) -> App.runThread(() -> {
            if (ex == null && serviceConfiguration != null && serviceConfiguration.discoveryDoc != null) {
                AuthState authState = new AuthState(serviceConfiguration);

                JsonWebKeySet jwks;
                try {
                    String jwksString = getJkws(serviceConfiguration.discoveryDoc.getJwksUri());
                    jwks = new JsonWebKeySet(jwksString);
                } catch (JoseException e) {
                    jwks = null;
                }
                if (jwks != null) {
                    callback.success(finalServerUrl, authState, jwks);
                }
                return;
            }
            if (ex != null) {
                ex.printStackTrace();
            }
            callback.failed();
        }), new NoExceptionConnectionBuilder());
    }



    public static void getOwnDetails(@NonNull Context context, @NonNull String keycloakServerUrl, @NonNull AuthState authState, @Nullable String clientSecret, @NonNull JsonWebKeySet jwks, @Nullable Long latestRevocationListTimestamp, @NonNull KeycloakManager.KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>> callback) {
        Logger.d("Fetching keycloak own details");

        final AuthorizationService authorizationService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder())
                .build());
        final AuthState.AuthStateAction action = (accessToken, idToken, ex) -> {
            authorizationService.dispose();
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace();
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR);
                        return;
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED);
            } else {
                App.runThread(() -> {
                    try {
                        final byte[] dataToSend;
                        if (latestRevocationListTimestamp == null) {
                            dataToSend = null;
                        } else {
                            dataToSend = AppSingleton.getJsonObjectMapper().writeValueAsBytes(new JsonMeRequest(latestRevocationListTimestamp));
                        }

                        byte[] bytes = keycloakApiRequest(keycloakServerUrl, ME_PATH, accessToken, dataToSend);

                        Map<String, Object> output = AppSingleton.getJsonObjectMapper().readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        Integer error = (Integer) output.get("error");
                        if (error != null) {
                            if (error == ERROR_CODE_PERMISSION_DENIED) {
                                callback.failed(RFC_AUTHENTICATION_REQUIRED);
                            } else {
                                callback.failed(RFC_SERVER_ERROR);
                            }
                            return;
                        }
                        JsonMeResponse response = AppSingleton.getJsonObjectMapper().readValue(bytes, JsonMeResponse.class);
                        if (response.signature != null) {
                            Pair<String, JsonWebKey> detailsJsonStringAndSignatureKey = verifySignature(response.signature, jwks, null);

                            if (detailsJsonStringAndSignatureKey != null) {
                                // signature verification successful, but the signing key might not be the right one --> this is checked in the success callback
                                JsonKeycloakUserDetails userDetails = AppSingleton.getJsonObjectMapper().readValue(detailsJsonStringAndSignatureKey.first, JsonKeycloakUserDetails.class);
                                callback.success(new Pair<>(
                                        new KeycloakUserDetailsAndStuff(userDetails, response.signature, detailsJsonStringAndSignatureKey.second, response.server, response.apiKey, response.pushTopics, response.selfRevocationTestNonce),
                                        new KeycloakServerRevocationsAndStuff(response.revocationAllowed, response.currentTimestamp, response.signedRevocations, response.minimumBuildVersions))
                                );
                            } else {
                                callback.failed(RFC_INVALID_SIGNATURE);
                            }
                        } else {
                            callback.failed(RFC_BAD_RESPONSE);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        callback.failed(RFC_NETWORK_ERROR);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.failed(RFC_UNKNOWN_ERROR);
                    }
                });
            }
        };
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action);
        } else {
            authState.performActionWithFreshTokens(authorizationService, new ClientSecretPost(clientSecret), action);
        }
    }



    static void uploadOwnIdentity(@NonNull Context context, @NonNull String keycloakServerUrl, @NonNull AuthState authState, @Nullable String clientSecret, @NonNull byte[] bytesOwnedIdentity,  @NonNull KeycloakManager.KeycloakCallback<Void> callback) {
        Logger.d("Uploading Olvid ID to keycloak");

        final AuthorizationService authorizationService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder())
                .build());
        final AuthState.AuthStateAction action = (accessToken, idToken, ex) -> {
            authorizationService.dispose();
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace();
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR);
                        return;
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED);
            } else {
                App.runThread(() -> {
                    try {
                        Map<String, Object> input = new HashMap<>();
                        input.put("identity", bytesOwnedIdentity);

                        byte[] bytes = keycloakApiRequest(keycloakServerUrl, PUT_KEY_PATH, accessToken, AppSingleton.getJsonObjectMapper().writeValueAsBytes(input));

                        Map<String, Object> output = AppSingleton.getJsonObjectMapper().readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        Integer error = (Integer) output.get("error");
                        if (error != null) {
                            switch (error) {
                                case ERROR_CODE_INTERNAL_ERROR:
                                case ERROR_CODE_INVALID_REQUEST:
                                    callback.failed(RFC_SERVER_ERROR);
                                    break;
                                case ERROR_CODE_IDENTITY_ALREADY_UPLOADED:
                                    callback.failed(RFC_IDENTITY_ALREADY_UPLOADED);
                                    break;
                                case ERROR_CODE_IDENTITY_WAS_REVOKED:
                                    callback.failed(RFC_IDENTITY_REVOKED);
                                    break;
                            }
                        } else {
                            callback.success(null);
                        }
                    } catch (IOException e) {
                        callback.failed(RFC_NETWORK_ERROR);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.failed(RFC_UNKNOWN_ERROR);
                    }
                });
            }
        };
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action);
        } else {
            authState.performActionWithFreshTokens(authorizationService, new ClientSecretPost(clientSecret), action);
        }
    }


    // the server groups download timestamp is returned in the callback
    public static void getGroups(Context context, String keycloakServerUrl, AuthState authState, String clientSecret, byte[] bytesOwnedIdentity, Long latestGetGroupsTimestamp, KeycloakManager.KeycloakCallbackWrapper<Long> callback) {
        Logger.d("Fetching keycloak groups");

        final AuthorizationService authorizationService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder())
                .build());
        final AuthState.AuthStateAction action = (accessToken, idToken, ex) -> {
            authorizationService.dispose();
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace();
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR);
                        return;
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED);
            } else {
                App.runThread(() -> {
                    try {
                        JsonGroupsRequest request = new JsonGroupsRequest(latestGetGroupsTimestamp);
                        byte[] bytes;
                        try {
                            bytes = keycloakApiRequest(keycloakServerUrl, GROUPS_PATH, accessToken, AppSingleton.getJsonObjectMapper().writeValueAsBytes(request));
                        } catch (IOException e) {
                            callback.failed(RFC_NETWORK_ERROR);
                            return;
                        }

                        Map<String, Object> output = AppSingleton.getJsonObjectMapper().readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        Integer error = (Integer) output.get("error");
                        if (error != null) {
                            callback.failed(RFC_SERVER_ERROR);
                        } else {
                            JsonGroupsResponse response = AppSingleton.getJsonObjectMapper().readValue(bytes, JsonGroupsResponse.class);
                            if (AppSingleton.getEngine().updateKeycloakGroups(bytesOwnedIdentity, response.getSignedGroupBlobs(), response.getSignedGroupDeletions(), response.getSignedGroupKicks(), response.getCurrentTimestamp())) {
                                callback.success(response.getCurrentTimestamp());
                            } else {
                                callback.failed(RFC_UNKNOWN_ERROR);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.failed(RFC_UNKNOWN_ERROR);
                    }
                });
            }
        };
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action);
        } else {
            authState.performActionWithFreshTokens(authorizationService, new ClientSecretPost(clientSecret), action);
        }
    }


    static void search(@NonNull Context context, @NonNull String keycloakServerUrl, @NonNull AuthState authState, @Nullable String clientSecret, @Nullable String searchQuery, @NonNull KeycloakManager.KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Integer>> callback) {
        Logger.d("Performing search on keycloak");

        final AuthorizationService authorizationService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder())
                .build());
        final AuthState.AuthStateAction action = (accessToken, idToken, ex) -> {
            authorizationService.dispose();
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace();
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR);
                        return;
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED);
            } else {
                App.runThread(() -> {
                    try {
                        JsonSearchRequest query = new JsonSearchRequest();
                        if (searchQuery == null) {
                            query.filter = null;
                        } else {
                            query.filter = searchQuery.trim().split("\\s+");
                        }

                        byte[] bytes = keycloakApiRequest(keycloakServerUrl, SEARCH_PATH, accessToken, AppSingleton.getJsonObjectMapper().writeValueAsBytes(query));

                        try {
                            Map<String, Object> output = AppSingleton.getJsonObjectMapper().readValue(bytes, new TypeReference<Map<String, Object>>() {});
                            Integer error = (Integer) output.get("error");
                            if (error != null) {
                                switch (error) {
                                    case ERROR_CODE_PERMISSION_DENIED:
                                        callback.failed(RFC_AUTHENTICATION_REQUIRED);
                                        break;
                                    case ERROR_CODE_INTERNAL_ERROR:
                                    default:
                                        callback.failed(RFC_SERVER_ERROR);
                                        break;
                                }
                                return;
                            }
                        } catch (Exception e) {
                            // an exception is normal if there was no error in the server response
                        }
                        JsonSearchResponse response = AppSingleton.getJsonObjectMapper().readValue(bytes, JsonSearchResponse.class);
                        callback.success(new Pair<>(response.results, response.count));
                    } catch (IOException e) {
                        callback.failed(RFC_NETWORK_ERROR);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.failed(RFC_UNKNOWN_ERROR);
                    }
                });
            }
        };
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action);
        } else {
            authState.performActionWithFreshTokens(authorizationService, new ClientSecretPost(clientSecret), action);
        }
    }


    static void addContact(@NonNull Context context, @NonNull String keycloakServerUrl, @NonNull AuthState authState, @Nullable String clientSecret, @NonNull JsonWebKeySet jwks, @Nullable JsonWebKey signatureKey, @NonNull byte[] bytesOwnedIdentity, String contactUserId, byte[] bytesContactIdentity, KeycloakManager.KeycloakCallback<Void> callback) {
        Logger.d("Retrieving signed contact details");

        final AuthorizationService authorizationService = new AuthorizationService(context, new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new NoExceptionConnectionBuilder())
                .build());
        final AuthState.AuthStateAction action = (accessToken, idToken, ex) -> {
            authorizationService.dispose();
            if (ex != null || accessToken == null) {
                if (ex != null) {
                    ex.printStackTrace();
                    if (ex.code == 3) {
                        callback.failed(RFC_NETWORK_ERROR);
                        return;
                    }
                }
                // by default, assume an authentication is required
                callback.failed(RFC_AUTHENTICATION_REQUIRED);
            } else {
                App.runThread(() -> {
                    try {
                        Map<String, String> query = new HashMap<>();
                        query.put("user-id", contactUserId);

                        byte[] bytes = keycloakApiRequest(keycloakServerUrl, GET_KEY_PATH, accessToken, AppSingleton.getJsonObjectMapper().writeValueAsBytes(query));

                        Map<String, Object> output = AppSingleton.getJsonObjectMapper().readValue(bytes, new TypeReference<Map<String, Object>>() {
                        });
                        Integer error = (Integer) output.get("error");
                        if (error != null) {
                            switch (error) {
                                case ERROR_CODE_PERMISSION_DENIED:
                                    callback.failed(RFC_AUTHENTICATION_REQUIRED);
                                    break;
                                case ERROR_CODE_INTERNAL_ERROR:
                                case ERROR_CODE_INVALID_REQUEST:
                                default:
                                    callback.failed(RFC_SERVER_ERROR);
                                    break;
                            }
                            return;
                        }
                        String signature = (String) output.get("signature");
                        if (signature != null) {
                            Pair<String, JsonWebKey> detailsJsonStringAndSignatureKey = verifySignature(signature, jwks, signatureKey);

                            if (detailsJsonStringAndSignatureKey != null) {
                                // signature verification successful, with the right signature key
                                JsonKeycloakUserDetails userDetails = AppSingleton.getJsonObjectMapper().readValue(detailsJsonStringAndSignatureKey.first, JsonKeycloakUserDetails.class);

                                if (Arrays.equals(userDetails.getIdentity(), bytesContactIdentity)) {
                                    AppSingleton.getEngine().addKeycloakContact(bytesOwnedIdentity, bytesContactIdentity, signature);
                                    callback.success(null);
                                    return;
                                }
                            } else if (verifySignature(signature, jwks, null) != null) {
                                // signature is valid but with the wrong signature key --> force a resync to detect key change and prompt user with standard dialog
                                KeycloakManager.forceSyncManagedIdentity(bytesOwnedIdentity);
                            }
                        }
                        callback.failed(RFC_INVALID_SIGNATURE);
                    } catch (IOException e) {
                        callback.failed(RFC_NETWORK_ERROR);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.failed(RFC_UNKNOWN_ERROR);
                    }
                });
            }
        };
        if (clientSecret == null) {
            authState.performActionWithFreshTokens(authorizationService, action);
        } else {
            authState.performActionWithFreshTokens(authorizationService, new ClientSecretPost(clientSecret), action);
        }
    }


    // returns with success(true) if the user was revoked
    static void selfRevocationTest(@NonNull String keycloakServerUrl, @NonNull String selfRevocationTestNonce,  @NonNull KeycloakManager.KeycloakCallback<Boolean> callback) {
        Logger.d("Checking keycloak for self revocation");

        App.runThread(() -> {
            try {
                Map<String, String> input = new HashMap<>();
                input.put("nonce", selfRevocationTestNonce);

                byte[] bytes = keycloakApiRequest(keycloakServerUrl, REVOCATION_TEST_PATH, null, AppSingleton.getJsonObjectMapper().writeValueAsBytes(input));

                callback.success( bytes != null && bytes.length == 1 & bytes[0] == 0x01);
            } catch (IOException e) {
                callback.failed(RFC_NETWORK_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                callback.failed(RFC_UNKNOWN_ERROR);
            }
        });
    }



    private static byte[] keycloakApiRequest(@NonNull String keycloakServer, @NonNull String path, @Nullable String accessToken, @Nullable byte[] dataToSend) throws IOException {
        URL requestUrl = new URL(keycloakServer + path);
        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        if (connection instanceof HttpsURLConnection && AppSingleton.getSslSocketFactory() != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(AppSingleton.getSslSocketFactory());
        }
        String userAgentProperty = System.getProperty("http.agent");
        if (userAgentProperty != null) {
            connection.setRequestProperty("User-Agent", userAgentProperty);
        }
        try {
            connection.setConnectTimeout(10_000);
            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (dataToSend == null) {
                connection.setDoOutput(false);
            } else {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(dataToSend.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(dataToSend);
                }
            }

            int serverResponse = connection.getResponseCode();

            if (serverResponse == 200) {
                try (InputStream is = connection.getInputStream();
                     BufferedInputStream bis = new BufferedInputStream(is);
                     ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                    int numberOfBytesRead;
                    byte[] buffer = new byte[32_768];

                    while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                    }
                    byteArrayOutputStream.flush();

                    return byteArrayOutputStream.toByteArray();
                }
            }
        } finally {
            connection.disconnect();
        }

        return null;
    }

    private static Pair<String, JsonWebKey> verifySignature(@NonNull String signature, @NonNull JsonWebKeySet jwks, @Nullable JsonWebKey signatureKey) {
        final List<JsonWebKey> jsonWebKeys;
        if (signatureKey == null) {
            jsonWebKeys = jwks.getJsonWebKeys();
        } else {
            jsonWebKeys = Collections.singletonList(signatureKey);
        }

        JwksVerificationKeyResolver jwksResolver = new JwksVerificationKeyResolver(jsonWebKeys);
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setExpectedAudience(false)
                .setVerificationKeyResolver(jwksResolver)
                .build();
        try {
            JwtContext context = jwtConsumer.process(signature);
            JsonWebKey jsonWebKey = new VerificationJwkSelector().select((JsonWebSignature) context.getJoseObjects().get(0), jsonWebKeys);
            return new Pair<>(context.getJwtClaims().getRawJson(), jsonWebKey);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private static String getJkws(@Nullable Uri jwksUri) {
        if (jwksUri != null) {
            Logger.d("Fetching JKWS at: " + jwksUri);
            HttpURLConnection connection = null;
            try {
                connection = new NoExceptionConnectionBuilder().openConnection(jwksUri);
                int response = connection.getResponseCode();
                if (response == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        return sb.toString();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    static class AuthenticationLifecycleObserver implements DefaultLifecycleObserver {
        private final ActivityResultRegistry registry;
        ActivityResultLauncher<Intent> authenticationResultHandler;
        private AuthenticateCallback callback;

        AuthenticationLifecycleObserver(@NonNull ComponentActivity activity) {
            registry = activity.getActivityResultRegistry();
        }

        void setCallback(AuthenticateCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onCreate(@NonNull LifecycleOwner owner) {
            authenticationResultHandler = registry.register("authentication", owner, new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        String updatedSerializedAuthState = intent.getStringExtra(KeycloakAuthenticationActivity.AUTH_STATE_JSON_INTENT_EXTRA);
                        if (updatedSerializedAuthState != null) {
                            try {
                                AuthState authState = AuthState.jsonDeserialize(updatedSerializedAuthState);
                                if (authState.isAuthorized()) {
                                    if (callback != null) {
                                        callback.success(authState);
                                    }
                                } else {
                                    Logger.d("User not authenticated");
                                    if (callback != null) {
                                        callback.failed(RFC_USER_NOT_AUTHENTICATED);
                                    }
                                }
                            } catch (Exception e) {
                                Logger.d("Error parsing serialized AuthState: receive browser result");
                                if (callback != null) {
                                    callback.failed(RFC_INVALID_AUTH_STATE);
                                }
                            }
                            return;
                        }
                    }
                }
                Logger.d("Keycloak authentication cancelled by user");
                if (callback != null) {
                    callback.failed(RFC_AUTHENTICATION_CANCELLED);
                }
            });
        }
    }

    public interface DiscoverKeycloakServerCallback {
        void success(@NonNull String serverUrl, @NonNull AuthState authState, @NonNull JsonWebKeySet jwks);
        void failed();
    }

    public interface AuthenticateCallback {
        void success(@NonNull AuthState authState);
        void failed(int rfc);
    }


}
