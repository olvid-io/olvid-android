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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import org.java_websocket_olvid.framing.BinaryFrame;
import org.java_websocket_olvid.framing.TextFrame;
import org.junit.Test;

public class DefaultExtensionTest {

  @Test
  public void testDecodeFrame() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    BinaryFrame binaryFrame = new BinaryFrame();
    binaryFrame.setPayload(ByteBuffer.wrap("test".getBytes()));
    defaultExtension.decodeFrame(binaryFrame);
    assertEquals(ByteBuffer.wrap("test".getBytes()), binaryFrame.getPayloadData());
  }

  @Test
  public void testEncodeFrame() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    BinaryFrame binaryFrame = new BinaryFrame();
    binaryFrame.setPayload(ByteBuffer.wrap("test".getBytes()));
    defaultExtension.encodeFrame(binaryFrame);
    assertEquals(ByteBuffer.wrap("test".getBytes()), binaryFrame.getPayloadData());
  }

  @Test
  public void testAcceptProvidedExtensionAsServer() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    assertTrue(defaultExtension.acceptProvidedExtensionAsServer("Test"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsServer(""));
    assertTrue(defaultExtension.acceptProvidedExtensionAsServer("Test, ASDC, as, ad"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsServer("ASDC, as,ad"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsServer("permessage-deflate"));
  }

  @Test
  public void testAcceptProvidedExtensionAsClient() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    assertTrue(defaultExtension.acceptProvidedExtensionAsClient("Test"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsClient(""));
    assertTrue(defaultExtension.acceptProvidedExtensionAsClient("Test, ASDC, as, ad"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsClient("ASDC, as,ad"));
    assertTrue(defaultExtension.acceptProvidedExtensionAsClient("permessage-deflate"));
  }

  @Test
  public void testIsFrameValid() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    TextFrame textFrame = new TextFrame();
    try {
      defaultExtension.isFrameValid(textFrame);
    } catch (Exception e) {
      fail("This frame is valid");
    }
    textFrame.setRSV1(true);
    try {
      defaultExtension.isFrameValid(textFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    textFrame.setRSV1(false);
    textFrame.setRSV2(true);
    try {
      defaultExtension.isFrameValid(textFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    textFrame.setRSV2(false);
    textFrame.setRSV3(true);
    try {
      defaultExtension.isFrameValid(textFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
  }

  @Test
  public void testGetProvidedExtensionAsClient() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    assertEquals("", defaultExtension.getProvidedExtensionAsClient());
  }

  @Test
  public void testGetProvidedExtensionAsServer() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    assertEquals("", defaultExtension.getProvidedExtensionAsServer());
  }

  @Test
  public void testCopyInstance() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    IExtension extensionCopy = defaultExtension.copyInstance();
    assertEquals(defaultExtension, extensionCopy);
  }

  @Test
  public void testToString() throws Exception {
    DefaultExtension defaultExtension = new DefaultExtension();
    assertEquals("DefaultExtension", defaultExtension.toString());
  }

  @Test
  public void testHashCode() throws Exception {
    DefaultExtension defaultExtension0 = new DefaultExtension();
    DefaultExtension defaultExtension1 = new DefaultExtension();
    assertEquals(defaultExtension0.hashCode(), defaultExtension1.hashCode());
  }

  @Test
  public void testEquals() throws Exception {
    DefaultExtension defaultExtension0 = new DefaultExtension();
    DefaultExtension defaultExtension1 = new DefaultExtension();
    assertEquals(defaultExtension0, defaultExtension1);
    assertFalse(defaultExtension0.equals(null));
    assertFalse(defaultExtension0.equals(new Object()));
  }

}