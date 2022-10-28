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

package org.java_websocket_olvid.extensions;

import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.java_websocket_olvid.exceptions.InvalidFrameException;
import org.java_websocket_olvid.framing.ControlFrame;
import org.java_websocket_olvid.framing.DataFrame;
import org.java_websocket_olvid.framing.Framedata;

/**
 * Implementation for a compression extension specified by https://tools.ietf.org/html/rfc7692
 *
 * @since 1.3.5
 */
public abstract class CompressionExtension extends DefaultExtension {

  @Override
  public void isFrameValid(Framedata inputFrame) throws InvalidDataException {
    if ((inputFrame instanceof DataFrame) && (inputFrame.isRSV2() || inputFrame.isRSV3())) {
      throw new InvalidFrameException(
          "bad rsv RSV1: " + inputFrame.isRSV1() + " RSV2: " + inputFrame.isRSV2() + " RSV3: "
              + inputFrame.isRSV3());
    }
    if ((inputFrame instanceof ControlFrame) && (inputFrame.isRSV1() || inputFrame.isRSV2()
        || inputFrame.isRSV3())) {
      throw new InvalidFrameException(
          "bad rsv RSV1: " + inputFrame.isRSV1() + " RSV2: " + inputFrame.isRSV2() + " RSV3: "
              + inputFrame.isRSV3());
    }
  }
}
