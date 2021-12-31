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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationPojo {
    String server;
    String apikey;
    ConfigurationKeycloakPojo keycloak;
    ConfigurationSettingsPojo settings;

    public ConfigurationPojo() {
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    public ConfigurationKeycloakPojo getKeycloak() {
        return keycloak;
    }

    public void setKeycloak(ConfigurationKeycloakPojo keycloak) {
        this.keycloak = keycloak;
    }

    public ConfigurationSettingsPojo getSettings() {
        return settings;
    }

    public void setSettings(ConfigurationSettingsPojo settings) {
        this.settings = settings;
    }
}


