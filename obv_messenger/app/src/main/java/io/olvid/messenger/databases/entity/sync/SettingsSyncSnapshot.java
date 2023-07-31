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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String AUTO_JOIN_GROUPS = "auto_join_groups";
    public static final String SEND_READ_RECEIPT = "send_read_receipt";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(AUTO_JOIN_GROUPS, SEND_READ_RECEIPT));

    public String auto_join_groups;
    public Boolean send_read_receipt;
    public HashSet<String> domain;

    public static SettingsSyncSnapshot of(OwnedIdentity ownedIdentity) {
        SettingsSyncSnapshot settingsSyncSnapshot = new SettingsSyncSnapshot();
        settingsSyncSnapshot.auto_join_groups = SettingsActivity.getAutoJoinGroups().getStringValue();
        settingsSyncSnapshot.send_read_receipt = SettingsActivity.getDefaultSendReadReceipt();
        settingsSyncSnapshot.domain = DEFAULT_DOMAIN;
        return settingsSyncSnapshot;
    }

    @JsonIgnore
    public String getAuto_join_groups() {
        return auto_join_groups == null ? SettingsActivity.AutoJoinGroupsCategory.CONTACTS.getStringValue() : auto_join_groups;
    }

    @JsonIgnore
    public boolean getSend_read_receipt() {
        return send_read_receipt != null && send_read_receipt;
    }

    @Override
    @JsonIgnore
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof SettingsSyncSnapshot)) {
            return false;
        }

        SettingsSyncSnapshot other = (SettingsSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case AUTO_JOIN_GROUPS: {
                    if (!Objects.equals(getAuto_join_groups(), other.getAuto_join_groups())) {
                        return false;
                    }
                    break;
                }
                case SEND_READ_RECEIPT: {
                    if (getSend_read_receipt() != other.getSend_read_receipt()) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    @JsonIgnore
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        if (!(otherSnapshotNode instanceof SettingsSyncSnapshot)) {
            throw new Exception();
        }
        SettingsSyncSnapshot other = (SettingsSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        List<ObvSyncDiff> diffs = new ArrayList<>();
        for (String item : domainIntersection) {
            switch (item) {
                case AUTO_JOIN_GROUPS: {
                    if (!Objects.equals(getAuto_join_groups(), other.getAuto_join_groups())) {
                        diffs.add(ObvSyncDiff.createSettingAutoJoinGroups(getAuto_join_groups(), other.getAuto_join_groups()));
                    }
                    break;
                }
                case SEND_READ_RECEIPT: {
                    if (getSend_read_receipt() != other.getSend_read_receipt()) {
                        diffs.add(ObvSyncDiff.createSettingSendReadReceipt(getSend_read_receipt(), other.getSend_read_receipt()));
                    }
                    break;
                }
            }
        }
        return diffs;
    }
}
