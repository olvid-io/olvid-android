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

package org.java_websocket_olvid.protocols;

import java.util.regex.Pattern;

/**
 * Class which represents the protocol used as Sec-WebSocket-Protocol
 *
 * @since 1.3.7
 */
public class Protocol implements IProtocol {

  private static final Pattern patternSpace = Pattern.compile(" ");
  private static final Pattern patternComma = Pattern.compile(",");

  /**
   * Attribute for the provided protocol
   */
  private final String providedProtocol;

  /**
   * Constructor for a Sec-Websocket-Protocol
   *
   * @param providedProtocol the protocol string
   */
  public Protocol(String providedProtocol) {
    if (providedProtocol == null) {
      throw new IllegalArgumentException();
    }
    this.providedProtocol = providedProtocol;
  }

  @Override
  public boolean acceptProvidedProtocol(String inputProtocolHeader) {
    if ("".equals(providedProtocol)) {
      return true;
    }
    String protocolHeader = patternSpace.matcher(inputProtocolHeader).replaceAll("");
    String[] headers = patternComma.split(protocolHeader);
    for (String header : headers) {
      if (providedProtocol.equals(header)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getProvidedProtocol() {
    return this.providedProtocol;
  }

  @Override
  public IProtocol copyInstance() {
    return new Protocol(getProvidedProtocol());
  }

  @Override
  public String toString() {
    return getProvidedProtocol();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Protocol protocol = (Protocol) o;

    return providedProtocol.equals(protocol.providedProtocol);
  }

  @Override
  public int hashCode() {
    return providedProtocol.hashCode();
  }
}
