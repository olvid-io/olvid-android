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

package io.olvid.messenger.owneddetails;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;


public class OwnedGroupDetailsViewModel extends ViewModel {
    private JsonGroupDetailsWithVersionAndPhoto oldDetails = null;
    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupOwnerAndUid;

    private String groupName;
    private String groupDescription;

    private String absolutePhotoUrl; // absolute path photoUrl
    private Uri takePictureUri;

    private final MutableLiveData<Boolean> valid = new MutableLiveData<>(false);
    private final MutableLiveData<InitialViewContent> initialViewContent = new MutableLiveData<>(null);

    public void setTakePictureUri(Uri takePictureUri) {
        this.takePictureUri = takePictureUri;
    }

    public Uri getTakePictureUri() {
        return takePictureUri;
    }


    public byte[] getBytesOwnedIdentity() {
        return bytesOwnedIdentity;
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
    }

    public byte[] getBytesGroupOwnerAndUid() {
        return bytesGroupOwnerAndUid;
    }

    public void setBytesGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) {
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUid, absolutePhotoUrl));
    }

    public void setOwnedGroupDetails(JsonGroupDetailsWithVersionAndPhoto groupDetails) {
        oldDetails = groupDetails;

        setAbsolutePhotoUrl(App.absolutePathFromRelative(groupDetails.getPhotoUrl()));
        setGroupName(groupDetails.getGroupDetails().getName());
        setGroupDescription(groupDetails.getGroupDetails().getDescription());
        checkValid();
    }

    public LiveData<Boolean> getValid() {
        return valid;
    }

    public LiveData<InitialViewContent> getInitialViewContent() {
        return initialViewContent;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
        checkValid();
    }

    public String getGroupDescription() {
        return groupDescription;
    }

    public void setGroupDescription(String groupDescription) {
        this.groupDescription = groupDescription;
    }

    public String getAbsolutePhotoUrl() {
        return absolutePhotoUrl;
    }

    public void setAbsolutePhotoUrl(String absolutePhotoUrl) {
        if (absolutePhotoUrl != null) {
            initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUid, absolutePhotoUrl));
        } else if (this.absolutePhotoUrl != null){
            initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUid, null));
        }
        this.absolutePhotoUrl = absolutePhotoUrl;
    }

    private void checkValid() {
        boolean newValid = groupName != null && groupName.trim().length() > 0;
        Boolean oldValid = valid.getValue();
        if (oldValid == null || (oldValid ^ newValid)) {
            valid.postValue(newValid);
        }
    }

    public boolean detailsChanged() {
        if (oldDetails == null) {
            return true;
        }
        return !getJsonGroupDetails().equals(oldDetails.getGroupDetails());
    }

    public boolean photoChanged() {
        if (absolutePhotoUrl == null) {
            return oldDetails != null && oldDetails.getPhotoUrl() != null;
        } else {
            return oldDetails == null || !absolutePhotoUrl.equals(App.absolutePathFromRelative(oldDetails.getPhotoUrl()));
        }
    }

    public JsonGroupDetails getJsonGroupDetails() {
        return new JsonGroupDetails(groupName.trim(), groupDescription);
    }

    static class InitialViewContent {
        public final byte[] bytesGroupOwnerAndUid;
        public final String absolutePhotoUrl;

        public InitialViewContent(byte[] bytesGroupOwnerAndUid, String absolutePhotoUrl) {
            if (bytesGroupOwnerAndUid == null) {
                this.bytesGroupOwnerAndUid = new byte[0];
            } else {
                this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
            }
            this.absolutePhotoUrl = absolutePhotoUrl;
        }
    }
}
