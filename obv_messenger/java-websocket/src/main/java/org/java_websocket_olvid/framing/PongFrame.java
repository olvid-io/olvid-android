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

package org.java_websocket_olvid.framing;

import org.java_websocket_olvid.enums.Opcode;

/**
 * Class to represent a pong frame
 */
public class PongFrame extends ControlFrame {

  /**
   * constructor which sets the opcode of this frame to pong
   */
  public PongFrame() {
    super(Opcode.PONG);
  }

  /**
   * constructor which sets the opcode of this frame to ping copying over the payload of the ping
   *
   * @param pingFrame the PingFrame which payload is to copy
   */
  public PongFrame(PingFrame pingFrame) {
    super(Opcode.PONG);
    setPayload(pingFrame.getPayloadData());
  }
}
