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

/**
 * Implementation for a client handshake
 */
public class HandshakeImpl1Client extends HandshakedataImpl1 implements ClientHandshakeBuilder {

  /**
   * Attribute for the resource descriptor
   */
  private String resourceDescriptor = "*";

  @Override
  public void setResourceDescriptor(String resourceDescriptor) {
    if (resourceDescriptor == null) {
      throw new IllegalArgumentException("http resource descriptor must not be null");
    }
    this.resourceDescriptor = resourceDescriptor;
  }

  @Override
  public String getResourceDescriptor() {
    return resourceDescriptor;
  }
}
