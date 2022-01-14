/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.engine.datatypes.notifications;


public abstract class ProtocolNotifications {
    public static final String NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED = "protocol_manager_notification_mutual_scan_contact_added";
    public static final String NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_OWNED_IDENTITIY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_CONTACT_IDENTITIY_KEY = "contact_identity"; // Identity
    public static final String NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY = "nonce"; // byte[]

}
