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

package org.java_websocket_olvid.issues;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.framing.BinaryFrame;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.util.Charsetfunctions;
import org.java_websocket_olvid.util.KeyUtils;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Issue847Test {

  private static Thread thread;
  private static ServerSocket serverSocket;

  private static int port;
  private static final int NUMBER_OF_TESTS = 20;

  @Parameterized.Parameter
  public int size;

  @Parameterized.Parameters
  public static Collection<Integer[]> data() {
    List<Integer[]> ret = new ArrayList<Integer[]>(NUMBER_OF_TESTS);
    for (int i = 1; i <= NUMBER_OF_TESTS + 1; i++) {
      ret.add(new Integer[]{(int) Math.round(Math.pow(2, i))});
    }
    return ret;
  }

  @BeforeClass
  public static void startServer() throws Exception {
    port = SocketUtil.getAvailablePort();
    thread = new Thread(
        new Runnable() {
          public void run() {
            try {
              serverSocket = new ServerSocket(port);
              serverSocket.setReuseAddress(true);
              while (true) {
                Socket client = null;
                try {
                  client = serverSocket.accept();
                  Scanner in = new Scanner(client.getInputStream());
                  String input;
                  String seckey = "";
                  String testCase;
                  boolean useMask = false;
                  int size = 0;
                  OutputStream os = client.getOutputStream();
                  while (in.hasNext()) {
                    input = in.nextLine();
                    if (input.startsWith("Sec-WebSocket-Key: ")) {
                      seckey = input.split(" ")[1];
                    }
                    //Last
                    if (input.startsWith("Upgrade")) {
                      os.write(Charsetfunctions.asciiBytes(
                          "HTTP/1.1 101 Websocket Connection Upgrade\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                              + KeyUtils.getSecKey(seckey) + "\r\n"));
                      os.flush();
                      Thread.sleep(10);
                      Draft_6455 draft_6455 = new Draft_6455();
                      BinaryFrame binaryFrame = new BinaryFrame();
                      binaryFrame.setPayload(ByteBuffer.allocate(size));
                      binaryFrame.setTransferemasked(useMask);
                      ByteBuffer byteBuffer = draft_6455.createBinaryFrame(binaryFrame);
                      byte[] bytes = byteBuffer.array();
                      int first = size / 2;
                      os.write(bytes, 0, first);
                      os.flush();
                      Thread.sleep(5);
                      os.write(bytes, first, bytes.length - first);
                      os.flush();
                      break;
                    }
                    if (input.startsWith("GET ")) {
                      testCase = input.split(" ")[1];
                      String[] strings = testCase.split("/");
                      useMask = Boolean.valueOf(strings[1]);
                      size = Integer.valueOf(strings[2]);
                    }
                  }
                } catch (IOException e) {
                  //
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
              fail("There should be no exception");
            }
          }
        });
    thread.start();
  }

  @AfterClass
  public static void successTests() throws IOException {
    serverSocket.close();
    thread.interrupt();
  }

  @Test(timeout = 5000)
  public void testIncrementalFrameUnmasked() throws Exception {
    testIncrementalFrame(false, size);
  }

  @Test(timeout = 5000)
  public void testIncrementalFrameMsked() throws Exception {
    testIncrementalFrame(true, size);
  }


  private void testIncrementalFrame(boolean useMask, int size) throws Exception {
    final boolean[] threadReturned = {false};
    final WebSocketClient webSocketClient = new WebSocketClient(
        new URI("ws://localhost:" + port + "/" + useMask + "/" + size)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
      }

      @Override
      public void onMessage(String message) {
        fail("There should not be a message!");
      }

      public void onMessage(ByteBuffer message) {
        threadReturned[0] = true;
        close();
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
      }

      @Override
      public void onError(Exception ex) {
        ex.printStackTrace();
      }
    };
    Thread finalThread = new Thread(webSocketClient);
    finalThread.start();
    finalThread.join();
    if (!threadReturned[0]) {
      fail("Error");
    }
  }
}

