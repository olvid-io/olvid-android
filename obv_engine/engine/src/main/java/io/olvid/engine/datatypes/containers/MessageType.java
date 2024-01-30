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

package io.olvid.engine.datatypes.containers;


public abstract class MessageType {
    public static final int PROTOCOL_MESSAGE_TYPE = 0;
    public static final int APPLICATION_MESSAGE_TYPE = 1;
    public static final int DIALOG_MESSAGE_TYPE = 2;
    public static final int DIALOG_RESPONSE_MESSAGE_TYPE = 3;
    public static final int SERVER_QUERY_TYPE = 4;
    public static final int SERVER_RESPONSE_TYPE = 5;
}
