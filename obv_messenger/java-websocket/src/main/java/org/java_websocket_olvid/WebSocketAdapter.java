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

package org.java_websocket_olvid;

import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.java_websocket_olvid.framing.Framedata;
import org.java_websocket_olvid.framing.PingFrame;
import org.java_websocket_olvid.framing.PongFrame;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.HandshakeImpl1Server;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.handshake.ServerHandshakeBuilder;

/**
 * This class default implements all methods of the WebSocketListener that can be overridden
 * optionally when advances functionalities is needed.<br>
 **/
public abstract class WebSocketAdapter implements WebSocketListener {

  private PingFrame pingFrame;

  /**
   * This default implementation does not do anything. Go ahead and overwrite it.
   *
   * @see WebSocketListener#onWebsocketHandshakeReceivedAsServer(WebSocket,
   * Draft, ClientHandshake)
   */
  @Override
  public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft,
                                                                     ClientHandshake request) throws InvalidDataException {
    return new HandshakeImpl1Server();
  }

  @Override
  public void onWebsocketHandshakeReceivedAsClient(WebSocket conn, ClientHandshake request,
      ServerHandshake response) throws InvalidDataException {
    //To overwrite
  }

  /**
   * This default implementation does not do anything which will cause the connections to always
   * progress.
   *
   * @see WebSocketListener#onWebsocketHandshakeSentAsClient(WebSocket,
   * ClientHandshake)
   */
  @Override
  public void onWebsocketHandshakeSentAsClient(WebSocket conn, ClientHandshake request)
      throws InvalidDataException {
    //To overwrite
  }

  /**
   * This default implementation will send a pong in response to the received ping. The pong frame
   * will have the same payload as the ping frame.
   *
   * @see WebSocketListener#onWebsocketPing(WebSocket, Framedata)
   */
  @Override
  public void onWebsocketPing(WebSocket conn, Framedata f) {
    conn.sendFrame(new PongFrame((PingFrame) f));
  }

  /**
   * This default implementation does not do anything. Go ahead and overwrite it.
   *
   * @see WebSocketListener#onWebsocketPong(WebSocket, Framedata)
   */
  @Override
  public void onWebsocketPong(WebSocket conn, Framedata f) {
    //To overwrite
  }

  /**
   * Default implementation for onPreparePing, returns a (cached) PingFrame that has no application
   * data.
   *
   * @param conn The <tt>WebSocket</tt> connection from which the ping frame will be sent.
   * @return PingFrame to be sent.
   * @see WebSocketListener#onPreparePing(WebSocket)
   */
  @Override
  public PingFrame onPreparePing(WebSocket conn) {
    if (pingFrame == null) {
      pingFrame = new PingFrame();
    }
    return pingFrame;
  }
}
