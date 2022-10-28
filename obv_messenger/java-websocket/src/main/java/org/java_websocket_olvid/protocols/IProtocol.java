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

/**
 * Interface which specifies all required methods for a Sec-WebSocket-Protocol
 *
 * @since 1.3.7
 */
public interface IProtocol {

  /**
   * Check if the received Sec-WebSocket-Protocol header field contains a offer for the specific
   * protocol
   *
   * @param inputProtocolHeader the received Sec-WebSocket-Protocol header field offered by the
   *                            other endpoint
   * @return true, if the offer does fit to this specific protocol
   * @since 1.3.7
   */
  boolean acceptProvidedProtocol(String inputProtocolHeader);

  /**
   * Return the specific Sec-WebSocket-protocol header offer for this protocol if the endpoint. If
   * the extension returns an empty string (""), the offer will not be included in the handshake.
   *
   * @return the specific Sec-WebSocket-Protocol header for this protocol
   * @since 1.3.7
   */
  String getProvidedProtocol();

  /**
   * To prevent protocols to be used more than once the Websocket implementation should call this
   * method in order to create a new usable version of a given protocol instance.
   *
   * @return a copy of the protocol
   * @since 1.3.7
   */
  IProtocol copyInstance();

  /**
   * Return a string which should contain the protocol name as well as additional information about
   * the current configurations for this protocol (DEBUG purposes)
   *
   * @return a string containing the protocol name as well as additional information
   * @since 1.3.7
   */
  String toString();
}
