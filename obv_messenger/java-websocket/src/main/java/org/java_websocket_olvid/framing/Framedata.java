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

import java.nio.ByteBuffer;
import org.java_websocket_olvid.enums.Opcode;

/**
 * The interface for the frame
 */
public interface Framedata {

  /**
   * Indicates that this is the final fragment in a message.  The first fragment MAY also be the
   * final fragment.
   *
   * @return true, if this frame is the final fragment
   */
  boolean isFin();

  /**
   * Indicates that this frame has the rsv1 bit set.
   *
   * @return true, if this frame has the rsv1 bit set
   */
  boolean isRSV1();

  /**
   * Indicates that this frame has the rsv2 bit set.
   *
   * @return true, if this frame has the rsv2 bit set
   */
  boolean isRSV2();

  /**
   * Indicates that this frame has the rsv3 bit set.
   *
   * @return true, if this frame has the rsv3 bit set
   */
  boolean isRSV3();

  /**
   * Defines whether the "Payload data" is masked.
   *
   * @return true, "Payload data" is masked
   */
  boolean getTransfereMasked();

  /**
   * Defines the interpretation of the "Payload data".
   *
   * @return the interpretation as a Opcode
   */
  Opcode getOpcode();

  /**
   * The "Payload data" which was sent in this frame
   *
   * @return the "Payload data" as ByteBuffer
   */
  ByteBuffer getPayloadData();// TODO the separation of the application data and the extension data is yet to be done

  /**
   * Appends an additional frame to the current frame
   * <p>
   * This methods does not override the opcode, but does override the fin
   *
   * @param nextframe the additional frame
   */
  void append(Framedata nextframe);
}
