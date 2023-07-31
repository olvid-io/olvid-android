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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonGroupType {
    public static final String TYPE_SIMPLE = "simple";
    public static final String TYPE_PRIVATE = "private";
    public static final String TYPE_READ_ONLY = "read_only";
    public static final String TYPE_CUSTOM = "custom";

    public static final String REMOTE_DELETE_NOBODY = "nobody";
    public static final String REMOTE_DELETE_ADMINS = "admins";
    public static final String REMOTE_DELETE_EVERYONE = "everyone";

    String type;
    Boolean readOnly;
    String remoteDelete;

    public JsonGroupType() {
    }

    @JsonIgnore
    public static JsonGroupType createSimple() {
        return new JsonGroupType(TYPE_SIMPLE, null, null);
    }
    @JsonIgnore
    public static JsonGroupType createPrivate() {
        return new JsonGroupType(TYPE_PRIVATE, null, null);
    }
    @JsonIgnore
    public static JsonGroupType createReadOnly() {
        return new JsonGroupType(TYPE_READ_ONLY, null, null);
    }
    @JsonIgnore
    public static JsonGroupType createCustom(boolean readOnly, String remoteDelete) {
        if (remoteDelete == null ||
                !(remoteDelete.equals(REMOTE_DELETE_NOBODY) || remoteDelete.equals(REMOTE_DELETE_ADMINS) || remoteDelete.equals(REMOTE_DELETE_EVERYONE))) {
            remoteDelete = REMOTE_DELETE_EVERYONE;
        }
        return new JsonGroupType(TYPE_CUSTOM, readOnly, remoteDelete);
    }

    private JsonGroupType (String type, Boolean readOnly, String remoteDelete) {
        this.type = type;
        this.readOnly = readOnly;
        this.remoteDelete = remoteDelete;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("ro")
    public Boolean getReadOnly() {
        return readOnly;
    }

    @JsonProperty("ro")
    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    @JsonProperty("del")
    public String getRemoteDelete() {
        return remoteDelete;
    }

    @JsonProperty("del")
    public void setRemoteDelete(String remoteDelete) {
        this.remoteDelete = remoteDelete;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return type == null;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JsonGroupType other)) {
            return false;
        }
        return Objects.equals(type, other.type) && Objects.equals(readOnly, other.readOnly) && Objects.equals(remoteDelete, other.remoteDelete);
    }
}
