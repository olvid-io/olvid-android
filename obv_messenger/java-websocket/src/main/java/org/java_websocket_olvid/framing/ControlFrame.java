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
import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.java_websocket_olvid.exceptions.InvalidFrameException;

/**
 * Abstract class to represent control frames
 */
public abstract class ControlFrame extends FramedataImpl1 {

  /**
   * Class to represent a control frame
   *
   * @param opcode the opcode to use
   */
  public ControlFrame(Opcode opcode) {
    super(opcode);
  }

  @Override
  public void isValid() throws InvalidDataException {
    if (!isFin()) {
      throw new InvalidFrameException("Control frame can't have fin==false set");
    }
    if (isRSV1()) {
      throw new InvalidFrameException("Control frame can't have rsv1==true set");
    }
    if (isRSV2()) {
      throw new InvalidFrameException("Control frame can't have rsv2==true set");
    }
    if (isRSV3()) {
      throw new InvalidFrameException("Control frame can't have rsv3==true set");
    }
  }
}
