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

package org.java_websocket_olvid.handshake;

import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Implementation of a handshake builder
 */
public class HandshakedataImpl1 implements HandshakeBuilder {

  /**
   * Attribute for the content of the handshake
   */
  private byte[] content;

  /**
   * Attribute for the http fields and values
   */
  private TreeMap<String, String> map;

  /**
   * Constructor for handshake implementation
   */
  public HandshakedataImpl1() {
    map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  }

  @Override
  public Iterator<String> iterateHttpFields() {
    return Collections.unmodifiableSet(map.keySet()).iterator();// Safety first
  }

  @Override
  public String getFieldValue(String name) {
    String s = map.get(name);
    if (s == null) {
      return "";
    }
    return s;
  }

  @Override
  public byte[] getContent() {
    return content;
  }

  @Override
  public void setContent(byte[] content) {
    this.content = content;
  }

  @Override
  public void put(String name, String value) {
    map.put(name, value);
  }

  @Override
  public boolean hasFieldValue(String name) {
    return map.containsKey(name);
  }
}
