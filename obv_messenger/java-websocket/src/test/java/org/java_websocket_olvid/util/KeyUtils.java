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

package org.java_websocket_olvid.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class KeyUtils {

  /**
   * Generate a final key from a input string
   *
   * @param in the input string
   * @return a final key
   */
  public static String generateFinalKey(String in) {
    String seckey = in.trim();
    String acc = seckey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    MessageDigest sh1;
    try {
      sh1 = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    return Base64.encodeBytes(sh1.digest(acc.getBytes()));
  }

  public static String getSecKey(String seckey) {
    return "Sec-WebSocket-Accept: " + KeyUtils.generateFinalKey(seckey) + "\r\n";
  }

}
