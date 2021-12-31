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

package io.olvid.engine.engine.types.identities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ObvUrlIdentity {
    public static final String URL_PROTOCOL = "https";
    public static final String URL_PROTOCOL_OLVID = "olvid";
    public static final String URL_INVITATION_HOST = "invitation.olvid.io";

    private static final Pattern INVITATION_PATTERN = Pattern.compile("(" + URL_PROTOCOL + "|" + URL_PROTOCOL_OLVID + ")" + Pattern.quote("://" + URL_INVITATION_HOST) + "/[#]?([-_a-zA-Z0-9]+)");

    public final Identity identity;
    public final String displayName;

    public ObvUrlIdentity(Identity identity, String displayName) {
        this.identity = identity;
        this.displayName = displayName;
    }

    public ObvUrlIdentity(byte[] bytesIdentity, String displayName) {
        Identity identity;
        try {
            identity = Identity.of(bytesIdentity);
        } catch (DecodingException e) {
            identity = null;
        }
        this.identity = identity;
        this.displayName = displayName;
    }

    public byte[] getBytesIdentity() {
        return identity.getBytes();
    }

    public String getUrlRepresentation() {
        return URL_PROTOCOL + "://" + URL_INVITATION_HOST + "/#" + ObvBase64.encode(Encoded.of(new Encoded[]{
                Encoded.of(identity),
                Encoded.of(displayName)
        }).getBytes());
    }

    public static ObvUrlIdentity fromUrlRepresentation(String urlRepresentation) {
        Matcher matcher = INVITATION_PATTERN.matcher(urlRepresentation);
        if (matcher.find()) {
            try {
                Encoded[] list = new Encoded(ObvBase64.decode(matcher.group(2))).decodeList();
                Identity identity = list[0].decodeIdentity();
                String displayName = list[1].decodeString();
                return new ObvUrlIdentity(identity, displayName);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

}
