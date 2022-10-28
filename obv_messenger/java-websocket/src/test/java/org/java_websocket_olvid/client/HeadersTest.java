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
import java.util.HashMap;
import java.util.Map;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.junit.Test;

public class HeadersTest {

  @Test
  public void testHttpHeaders() throws URISyntaxException {
    Map<String, String> httpHeaders = new HashMap<String, String>();
    httpHeaders.put("Cache-Control", "only-if-cached");
    httpHeaders.put("Keep-Alive", "1000");

    WebSocketClient client = new WebSocketClient(new URI("ws://localhost"), httpHeaders) {
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

    assertEquals("only-if-cached", client.removeHeader("Cache-Control"));
    assertEquals("1000", client.removeHeader("Keep-Alive"));
  }

  @Test
  public void test_Add_RemoveHeaders() throws URISyntaxException {
    Map<String, String> httpHeaders = null;
    WebSocketClient client = new WebSocketClient(new URI("ws://localhost"), httpHeaders) {
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
    client.addHeader("Cache-Control", "only-if-cached");
    assertEquals("only-if-cached", client.removeHeader("Cache-Control"));
    assertNull(client.removeHeader("Cache-Control"));

    client.addHeader("Cache-Control", "only-if-cached");
    client.clearHeaders();
    assertNull(client.removeHeader("Cache-Control"));
  }

  @Test
  public void testGetURI() throws URISyntaxException {
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
    String actualURI = client.getURI().getScheme() + "://" + client.getURI().getHost();

    assertEquals("ws://localhost", actualURI);
  }
}
