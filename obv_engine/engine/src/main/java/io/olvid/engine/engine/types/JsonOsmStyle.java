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

package io.olvid.engine.engine.types;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonOsmStyle {
    public String id; // should never be null
    public Map<String, String> name; // should never be null
    public String url; // should never be null

    public JsonOsmStyle() {}

    public JsonOsmStyle(String id, String url) {
        this.id = id;
        this.name = Collections.emptyMap();
        this.url = url;
    }
}
