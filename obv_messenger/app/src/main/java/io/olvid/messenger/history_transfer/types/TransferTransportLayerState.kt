/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.history_transfer.types

enum class TransferTransportLayerState {
    NOT_STARTED, // we are still negotiating in the "control" step, no WebRTC connection has been attempted yet
    INITIALIZING, // transport layer is starting
    CONNECTING, // transport layer is starting
    READY, // transport layer is functional, messages can be sent
    PROCESSING_RECEIVED_DATA, // special state for DST side: the connection is closed but we are still processing received messages
    CLOSED, // transport layer closed
}