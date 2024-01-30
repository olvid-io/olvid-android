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

package io.olvid.messenger.openid.jsons;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonMeResponse {
    public String signature;
    public String server;
    public Boolean revocationAllowed;
    public String apiKey;
    public String selfRevocationTestNonce;
    public List<String> pushTopics;
    public List<String> signedRevocations;
    public long currentTimestamp;
    public Map<String, Integer> minimumBuildVersions;


    @JsonProperty("api-key")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty("api-key")
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @JsonProperty("revocation-allowed")
    public Boolean getRevocationAllowed() {
        return revocationAllowed;
    }

    @JsonProperty("revocation-allowed")
    public void setRevocationAllowed(Boolean revocationAllowed) {
        this.revocationAllowed = revocationAllowed;
    }

    @JsonProperty("push-topics")
    public List<String> getPushTopics() {
        return pushTopics;
    }

    @JsonProperty("push-topics")
    public void setPushTopics(List<String> pushTopics) {
        this.pushTopics = pushTopics;
    }

    @JsonProperty("nonce")
    public String getSelfRevocationTestNonce() {
        return selfRevocationTestNonce;
    }

    @JsonProperty("nonce")
    public void setSelfRevocationTestNonce(String selfRevocationTestNonce) {
        this.selfRevocationTestNonce = selfRevocationTestNonce;
    }

    @JsonProperty("signed-revocations")
    public List<String> getSignedRevocations() {
        return signedRevocations;
    }

    @JsonProperty("signed-revocations")
    public void setSignedRevocations(List<String> signedRevocations) {
        this.signedRevocations = signedRevocations;
    }

    @JsonProperty("current-timestamp")
    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    @JsonProperty("current-timestamp")
    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }

    @JsonProperty("min-build-versions")
    public Map<String, Integer> getMinimumBuildVersions() {
        return minimumBuildVersions;
    }

    @JsonProperty("min-build-versions")
    public void setMinimumBuildVersions(Map<String, Integer> minimumBuildVersions) {
        this.minimumBuildVersions = minimumBuildVersions;
    }
}
