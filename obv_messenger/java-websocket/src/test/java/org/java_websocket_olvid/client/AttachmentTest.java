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

package org.java_websocket_olvid.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.net.URISyntaxException;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.junit.Test;

public class AttachmentTest {

  @Test
  public void testDefaultValue() throws URISyntaxException {
    WebSocketClient client = new WebSocketClient(new URI("ws://localhost")) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {

      }

      @Override
      public void onMessage(String message) {

      }

      @Override
      public void onClose(int code, String reason, boolean remote) {

      }

      @Override
      public void onError(Exception ex) {

      }
    };
    assertNull(client.getAttachment());
  }

  @Test
  public void testSetter() throws URISyntaxException {
    WebSocketClient client = new WebSocketClient(new URI("ws://localhost")) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {

      }

      @Override
      public void onMessage(String message) {

      }

      @Override
      public void onClose(int code, String reason, boolean remote) {

      }

      @Override
      public void onError(Exception ex) {

      }
    };
    assertNull(client.<WebSocketClient>getAttachment());
    client.setAttachment(client);
    assertEquals(client.<WebSocketClient>getAttachment(), client);
    client.setAttachment(null);
    assertNull(client.<WebSocketClient>getAttachment());
  }
}
