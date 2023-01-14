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

package io.olvid.messenger.webclient.datatypes;

public class Constants {
    public static final int VERSION = 0;

    public static final String QRCODE_URL_SCHEME = "https://web.olvid.io/#";

    public static final int ATTACHMENT_DONE_TIMEOUT_MS = 15_000;
    public static final long PROTOCOL_TIMEOUT = 10_000;
    public static final long CONNECTION_TIMEOUT_MILLIS = 5_000;
    public static final long WEBSOCKET_PING_INTERVAL = 10_000L;
    public static final long RECONNECTION_TIMEOUT_MILLIS = 30_000;
    public static final long PING_TIMER_DELAY = 600_000; //10 minutes
    public static final long PING_TIMER_PERIOD = 600_000; //10 minutes
    public static final long DECLARE_INACTIVE_TIMEOUT = 600_000; //10 minutes of inactivity

    public static final int ATTACHMENT_THUMBNAIL_SIZE = 128;
    public static final int MAX_PAYLOAD_SIZE = 24_000; // max AWS message size is 32kB, but we need to account for the colissimo and encryption overheads, as well as json base64 encoding and overhead
    public static final long MAX_PENDING_BYTES_DURING_ATTACHMENT_UPLOAD = 5L * MAX_PAYLOAD_SIZE;

    public static final String[] SUPPORTED_LANGUAGES = new String[]{"en", "fr"};
}
