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
import org.java_websocket_olvid.framing.Framedata;

/**
 * Class which represents the normal websocket implementation specified by rfc6455.
 * <p>
 * This is a fallback and will always be available for a Draft_6455
 *
 * @since 1.3.5
 */
public class DefaultExtension implements IExtension {

  @Override
  public void decodeFrame(Framedata inputFrame) throws InvalidDataException {
    //Nothing to do here
  }

  @Override
  public void encodeFrame(Framedata inputFrame) {
    //Nothing to do here
  }

  @Override
  public boolean acceptProvidedExtensionAsServer(String inputExtension) {
    return true;
  }

  @Override
  public boolean acceptProvidedExtensionAsClient(String inputExtension) {
    return true;
  }

  @Override
  public void isFrameValid(Framedata inputFrame) throws InvalidDataException {
    if (inputFrame.isRSV1() || inputFrame.isRSV2() || inputFrame.isRSV3()) {
      throw new InvalidFrameException(
          "bad rsv RSV1: " + inputFrame.isRSV1() + " RSV2: " + inputFrame.isRSV2() + " RSV3: "
              + inputFrame.isRSV3());
    }
  }

  @Override
  public String getProvidedExtensionAsClient() {
    return "";
  }

  @Override
  public String getProvidedExtensionAsServer() {
    return "";
  }

  @Override
  public IExtension copyInstance() {
    return new DefaultExtension();
  }

  public void reset() {
    //Nothing to do here. No internal stats.
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o != null && getClass() == o.getClass();
  }
}
