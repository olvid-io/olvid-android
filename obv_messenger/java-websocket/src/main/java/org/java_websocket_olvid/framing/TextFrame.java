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
import org.java_websocket_olvid.util.Charsetfunctions;

/**
 * Class to represent a text frames
 */
public class TextFrame extends DataFrame {

  /**
   * constructor which sets the opcode of this frame to text
   */
  public TextFrame() {
    super(Opcode.TEXT);
  }

  @Override
  public void isValid() throws InvalidDataException {
    super.isValid();
    if (!Charsetfunctions.isValidUTF8(getPayloadData())) {
      throw new InvalidDataException(CloseFrame.NO_UTF8, "Received text is no valid utf8 string!");
    }
  }
}
