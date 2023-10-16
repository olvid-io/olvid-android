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

package io.olvid.messenger.databases.entity.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscussionCustomizationSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String LOCAL_SETTINGS = "local_settings";
    public static final String SHARED_SETTINGS = "shared_settings";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(List.of(LOCAL_SETTINGS, SHARED_SETTINGS));

    public LocalSettings local_settings;
    public SharedSettings shared_settings;
    public HashSet<String> domain;

    public static DiscussionCustomizationSyncSnapshot of(AppDatabase db, DiscussionCustomization discussionCustomization) {
        DiscussionCustomizationSyncSnapshot discussionCustomizationSyncSnapshot = new DiscussionCustomizationSyncSnapshot();

        LocalSettings localSettings = new LocalSettings();
        localSettings.send_read_receipt = discussionCustomization.prefSendReadReceipt;
        discussionCustomizationSyncSnapshot.local_settings = localSettings;

        if (discussionCustomization.sharedSettingsVersion != null) {
            SharedSettings sharedSettings = new SharedSettings();
            sharedSettings.version = discussionCustomization.sharedSettingsVersion;
            sharedSettings.existence_duration = discussionCustomization.settingExistenceDuration;
            sharedSettings.visibility_duration = discussionCustomization.settingVisibilityDuration;
            sharedSettings.read_once = discussionCustomization.settingReadOnce ? true : null;
            discussionCustomizationSyncSnapshot.shared_settings = sharedSettings;
        }
        discussionCustomizationSyncSnapshot.domain = DEFAULT_DOMAIN;
        return discussionCustomizationSyncSnapshot;
    }

    public void restore(AppDatabase db, long discussionId) {
        boolean insertionNeeded = false;
        boolean changed = false;
        DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
        if (discussionCustomization == null) {
            discussionCustomization = new DiscussionCustomization(discussionId);
            insertionNeeded = true;
        }
        if (domain.contains(LOCAL_SETTINGS) && local_settings != null) {
            if (local_settings.send_read_receipt != null) {
                changed = true;
                discussionCustomization.prefSendReadReceipt = local_settings.send_read_receipt;
            }
        }

        if (domain.contains(SHARED_SETTINGS) && shared_settings != null && shared_settings.version != null) {
            changed = true;
            discussionCustomization.sharedSettingsVersion = shared_settings.version;
            discussionCustomization.settingVisibilityDuration = shared_settings.visibility_duration;
            discussionCustomization.settingExistenceDuration = shared_settings.existence_duration;
            discussionCustomization.settingReadOnce = shared_settings.read_once != null && shared_settings.read_once;

            // insert the discussion setting update message in the discussion
            JsonExpiration jsonExpiration = new JsonExpiration();
            jsonExpiration.setVisibilityDuration(shared_settings.visibility_duration);
            jsonExpiration.setExistenceDuration(shared_settings.existence_duration);
            jsonExpiration.setReadOnce(shared_settings.read_once);

            if (!jsonExpiration.likeNull()) {
                JsonSharedSettings jsonSharedSettings = new JsonSharedSettings();
                jsonSharedSettings.setVersion(shared_settings.version);
                jsonSharedSettings.setJsonExpiration(jsonExpiration);
                Message settingsMessage = Message.createDiscussionSettingsUpdateMessage(db, discussionId, jsonSharedSettings, new byte[0], true, null);
                if (settingsMessage != null) {
                    db.messageDao().insert(settingsMessage);
                }
            }
        } else if (!insertionNeeded) {
            // in case a default ephemeral setting was applied, reset it
            discussionCustomization.sharedSettingsVersion = null;
            discussionCustomization.settingVisibilityDuration = null;
            discussionCustomization.settingExistenceDuration = null;
            discussionCustomization.settingReadOnce = false;
            changed = true;
        }

        if (changed) {
            if (insertionNeeded) {
                db.discussionCustomizationDao().insert(discussionCustomization);
            } else {
                db.discussionCustomizationDao().update(discussionCustomization);
            }
        }
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof DiscussionCustomizationSyncSnapshot)) {
            return false;
        }

        DiscussionCustomizationSyncSnapshot other = (DiscussionCustomizationSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case LOCAL_SETTINGS: {
                    if (local_settings == null && other.local_settings != null || local_settings != null && !local_settings.areContentsTheSame(other.local_settings)) {
                        return false;
                    }
                    break;
                }
                case SHARED_SETTINGS: {
                    if (shared_settings == null && other.shared_settings != null || shared_settings != null && !shared_settings.areContentsTheSame(other.shared_settings)) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SharedSettings {
        public Integer version;
        public Long existence_duration;
        public Long visibility_duration;
        public Boolean read_once;

        public boolean areContentsTheSame(SharedSettings other) {
            return Objects.equals(version, other.version)
                    && Objects.equals(existence_duration, other.existence_duration)
                    && Objects.equals(visibility_duration, other.visibility_duration)
                    && (read_once != null && read_once) == (other.read_once != null && other.read_once);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalSettings {
        public Boolean send_read_receipt;

        public boolean areContentsTheSame(LocalSettings other) {
            return Objects.equals(send_read_receipt, other.send_read_receipt);
        }
    }
}
