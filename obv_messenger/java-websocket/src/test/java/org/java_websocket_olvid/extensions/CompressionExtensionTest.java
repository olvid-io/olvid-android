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

import static org.junit.Assert.fail;

import org.java_websocket_olvid.framing.PingFrame;
import org.java_websocket_olvid.framing.TextFrame;
import org.junit.Test;

public class CompressionExtensionTest {


  @Test
  public void testIsFrameValid() {
    CustomCompressionExtension customCompressionExtension = new CustomCompressionExtension();
    TextFrame textFrame = new TextFrame();
    try {
      customCompressionExtension.isFrameValid(textFrame);
    } catch (Exception e) {
      fail("This frame is valid");
    }
    textFrame.setRSV1(true);
    try {
      customCompressionExtension.isFrameValid(textFrame);
    } catch (Exception e) {
      fail("This frame is valid");
    }
    textFrame.setRSV1(false);
    textFrame.setRSV2(true);
    try {
      customCompressionExtension.isFrameValid(textFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    textFrame.setRSV2(false);
    textFrame.setRSV3(true);
    try {
      customCompressionExtension.isFrameValid(textFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    PingFrame pingFrame = new PingFrame();
    try {
      customCompressionExtension.isFrameValid(pingFrame);
    } catch (Exception e) {
      fail("This frame is valid");
    }
    pingFrame.setRSV1(true);
    try {
      customCompressionExtension.isFrameValid(pingFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    pingFrame.setRSV1(false);
    pingFrame.setRSV2(true);
    try {
      customCompressionExtension.isFrameValid(pingFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
    pingFrame.setRSV2(false);
    pingFrame.setRSV3(true);
    try {
      customCompressionExtension.isFrameValid(pingFrame);
      fail("This frame is not valid");
    } catch (Exception e) {
      //
    }
  }

  private static class CustomCompressionExtension extends CompressionExtension {

  }
}
