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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonGroupDetails {
    String name;
    String description;

    public JsonGroupDetails() {
    }

    public JsonGroupDetails(String name, String description) {
        this.name = nullOrTrim(name);
        this.description = nullOrTrim(description);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = nullOrTrim(name);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = nullOrTrim(description);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return name == null;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JsonGroupDetails)) {
            return false;
        }
        JsonGroupDetails other = (JsonGroupDetails) obj;
        return Objects.equals(name, other.name) && Objects.equals(description, other.description);
    }

    private static String nullOrTrim(String in) {
        if (in == null) {
            return null;
        }
        String out = in.trim();
        if (out.length() == 0) {
            return null;
        }
        return out;
    }
}
