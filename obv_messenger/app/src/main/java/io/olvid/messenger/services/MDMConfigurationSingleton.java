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

package io.olvid.messenger.services;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;

import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.customClasses.ConfigurationPojo;
import io.olvid.messenger.customClasses.StringUtils;

public class MDMConfigurationSingleton {
    private static final String KEYCLOAK_CONFIGURATION_URI = "keycloak_configuration_uri";
    private static final String DISABLE_NEW_VERSION_NOTIFICATION = "disable_new_version_notification";
    private static final String SETTINGS_CONFIGURATION_URI = "settings_configuration_uri";
    private static final String ALTERNATE_TURN_SERVER_URL = "alternate_turn_server_url";
    private static final String ALTERNATE_TURN_SERVER_USERNAME = "alternate_turn_server_username";
    private static final String ALTERNATE_TURN_SERVER_PASSWORD = "alternate_turn_server_password";


    private static MDMConfigurationSingleton INSTANCE = null;

    private final String keycloakConfigurationUri;
    private final boolean disableNewVersionNotification;
    private final String settingsConfigurationUri;
    private final String alternateTurnServerUrl;
    private final String alternateTurnServerUsername;
    private final String alternateTurnServerPassword;

    // parse all restrictions once at initialisation
    public MDMConfigurationSingleton() {
        RestrictionsManager restrictionsManager = (RestrictionsManager) App.getContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        Bundle restrictions =  restrictionsManager.getApplicationRestrictions();

        if (restrictions != null) {
            if (restrictions.containsKey(KEYCLOAK_CONFIGURATION_URI)) {
                keycloakConfigurationUri = restrictions.getString(KEYCLOAK_CONFIGURATION_URI);
            } else {
                keycloakConfigurationUri = null;
            }

            if (restrictions.containsKey(DISABLE_NEW_VERSION_NOTIFICATION)) {
                disableNewVersionNotification = restrictions.getBoolean(DISABLE_NEW_VERSION_NOTIFICATION, false);
            } else {
                disableNewVersionNotification = false;
            }

            if (restrictions.containsKey(SETTINGS_CONFIGURATION_URI)) {
                settingsConfigurationUri = restrictions.getString(SETTINGS_CONFIGURATION_URI);
            } else {
                settingsConfigurationUri = null;
            }

            if (restrictions.containsKey(ALTERNATE_TURN_SERVER_URL) && restrictions.containsKey(ALTERNATE_TURN_SERVER_USERNAME) && restrictions.containsKey(ALTERNATE_TURN_SERVER_PASSWORD)) {
                alternateTurnServerUrl = restrictions.getString(ALTERNATE_TURN_SERVER_URL);
                alternateTurnServerUsername = restrictions.getString(ALTERNATE_TURN_SERVER_USERNAME);
                alternateTurnServerPassword = restrictions.getString(ALTERNATE_TURN_SERVER_PASSWORD);
            } else {
                alternateTurnServerUrl = null;
                alternateTurnServerUsername = null;
                alternateTurnServerPassword = null;
            }
        } else {
            keycloakConfigurationUri = null;
            disableNewVersionNotification = false;
            settingsConfigurationUri = null;
            alternateTurnServerUrl = null;
            alternateTurnServerUsername = null;
            alternateTurnServerPassword = null;
        }
    }

    public static MDMConfigurationSingleton getInstance() {
        if (INSTANCE == null) {
            synchronized (MDMConfigurationSingleton.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MDMConfigurationSingleton();
                }
            }
        }
        return INSTANCE;
    }

    // called after a profile creation, in case the WEBDAV_AUTOMATIC_BACKUP_URI contains substitution parameters
    public static void reloadMDMConfiguration() {
        if (INSTANCE != null) {
            INSTANCE = null;
        }
    }

    public static String getKeycloakConfigurationUri() {
        return getInstance().keycloakConfigurationUri;
    }

    public static boolean getDisableNewVersionNotification() {
        return getInstance().disableNewVersionNotification;
    }

    public static String getSettingsConfigurationUri() {
        return getInstance().settingsConfigurationUri;
    }

    public static String getAlternateTurnServerUrl() {
        return getInstance().alternateTurnServerUrl;
    }

    public static String getAlternateTurnServerUsername() {
        return getInstance().alternateTurnServerUsername;
    }

    public static String getAlternateTurnServerPassword() {
        return getInstance().alternateTurnServerPassword;
    }

    private String replaceWebDavUriVariablesFromKeycloakProfile(String uri, String keycloakConfigurationUri) {
        try {
            Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(keycloakConfigurationUri);
            if (matcher.find()) {
                ConfigurationPojo configurationPojo = AppSingleton.getJsonObjectMapper().readValue(ObvBase64.decode(matcher.group(2)), ConfigurationPojo.class);
                if (configurationPojo.keycloak != null) {
                    JsonIdentityDetails details = null;

                    ObvIdentity[] ownedIdentities = AppSingleton.getEngine().getOwnedIdentities();
                    for (ObvIdentity ownedIdentity : ownedIdentities) {
                        if (ownedIdentity.isKeycloakManaged() && ownedIdentity.isActive()) {
                            ObvKeycloakState keycloakState = AppSingleton.getEngine().getOwnedIdentityKeycloakState(ownedIdentity.getBytesIdentity());
                            if (keycloakState != null && Objects.equals(keycloakState.keycloakServer, configurationPojo.keycloak.getServer())) {
                                if (details == null) {
                                    details = ownedIdentity.getIdentityDetails();
                                } else {
                                    throw new Exception("Multiple identities managed by Keycloak");
                                }
                            }
                        }
                    }

                    if (details != null) {
                        String first_name = unAccentAndClean(details.getFirstName());
                        String last_name = unAccentAndClean(details.getLastName());
                        String position = unAccentAndClean(details.getPosition());
                        String company = unAccentAndClean(details.getCompany());

                        return uri.replace("{{first_name}}", first_name).replace("{{last_name}}", last_name).replace("{{position}}", position).replace("{{company}}", company);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (uri.contains("{{first_name}}") || uri.contains("{{last_name}}") || uri.contains("{{position}}") || uri.contains("{{company}}")) {
            return null;
        }
        return uri;
    }

    @NonNull
    private String unAccentAndClean(@Nullable String s) {
        if (s == null) {
            return "";
        }
        return StringUtils.unAccent(s).toLowerCase().trim().replaceAll("[^a-z]", "_");
    }
}
