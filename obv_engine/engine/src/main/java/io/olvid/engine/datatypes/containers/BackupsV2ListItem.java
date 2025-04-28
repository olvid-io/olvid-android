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

package io.olvid.engine.datatypes.containers;


import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class BackupsV2ListItem {
    public final UID threadId;
    public final long version;
    public final String downloadUrl;

    public BackupsV2ListItem(UID threadId, long version, String downloadUrl) {
        this.threadId = threadId;
        this.version = version;
        this.downloadUrl = downloadUrl;
    }

    public static BackupsV2ListItem manyOf(Encoded encoded) throws DecodingException {
        Encoded[] encodeds = encoded.decodeList();
        if (encodeds.length != 3) {
            throw new DecodingException("Bad encoded list length: " + encodeds.length);
        }
        return new BackupsV2ListItem(
                encodeds[0].decodeUid(),
                encodeds[1].decodeLong(),
                encodeds[2].decodeString()
        );
    }

    public static List<BackupsV2ListItem> manyOf(Encoded[] encodeds) throws DecodingException {
        List<BackupsV2ListItem> list = new ArrayList<>();
        for (Encoded encoded : encodeds) {
            list.add(BackupsV2ListItem.manyOf(encoded));
        }
        return list;
    }
}
