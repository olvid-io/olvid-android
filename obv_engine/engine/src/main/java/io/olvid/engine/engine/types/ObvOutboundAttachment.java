/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.engine.engine.types;

public class ObvOutboundAttachment {
    private final String path; // should always be the getNoBackupFilesDir() relative path to the file
    private final long attachmentLength;
    private final byte[] metadata;

    public ObvOutboundAttachment(String path, long attachmentLength, byte[] metadata) {
        this.path = path;
        this.attachmentLength = attachmentLength;
        this.metadata = metadata;
    }

    public String getPath() {
        return path;
    }

    public long getAttachmentLength() {
        return attachmentLength;
    }

    public byte[] getMetadata() {
        return metadata;
    }
}
