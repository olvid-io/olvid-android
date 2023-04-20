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

package io.olvid.messenger.owneddetails;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;


public class OwnedGroupDetailsViewModel extends ViewModel {
    private JsonGroupDetails oldDetails = null;
    private String oldPhotoUrl = null;
    private String oldPersonalNote = null;
    private boolean groupV2;
    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupOwnerAndUidOrIdentifier;

    private String groupName;
    private String groupDescription;
    private String personalNote;

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

    public byte[] getBytesGroupOwnerAndUidOrIdentifier() {
        return bytesGroupOwnerAndUidOrIdentifier;
    }

    public void setBytesGroupOwnerAndUidOrIdentifier(byte[] bytesGroupOwnerAndUidOrIdentifier) {
        this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier;
        initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUidOrIdentifier, absolutePhotoUrl));
    }

    public boolean isGroupV2() {
        return groupV2;
    }

    public void setGroupV2(boolean groupV2) {
        this.groupV2 = groupV2;
        checkValid();
    }

    public void setOwnedGroupDetails(JsonGroupDetailsWithVersionAndPhoto groupDetails, String personalNote) {
        oldDetails = groupDetails.getGroupDetails();
        oldPhotoUrl = groupDetails.getPhotoUrl();
        oldPersonalNote = personalNote;

        setAbsolutePhotoUrl(App.absolutePathFromRelative(groupDetails.getPhotoUrl()));
        setGroupName(groupDetails.getGroupDetails().getName());
        setGroupDescription(groupDetails.getGroupDetails().getDescription());
        setPersonalNote(personalNote);
        checkValid();
    }

    public void setOwnedGroupDetailsV2(JsonGroupDetails groupDetails, String photoUrl, String personalNote) {
        oldDetails = groupDetails;
        oldPhotoUrl = photoUrl;
        oldPersonalNote = personalNote;

        setAbsolutePhotoUrl(App.absolutePathFromRelative(photoUrl));
        setGroupName(groupDetails.getName());
        setGroupDescription(groupDetails.getDescription());
        setPersonalNote(personalNote);
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
            initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUidOrIdentifier, absolutePhotoUrl));
        } else if (this.absolutePhotoUrl != null){
            initialViewContent.postValue(new InitialViewContent(bytesGroupOwnerAndUidOrIdentifier, null));
        }
        this.absolutePhotoUrl = absolutePhotoUrl;
    }

    public String getPersonalNote() {
        return personalNote;
    }

    public void setPersonalNote(String personalNote) {
        this.personalNote = personalNote;
    }

    private void checkValid() {
        boolean newValid = groupV2 || (groupName != null && groupName.trim().length() > 0);
        Boolean oldValid = valid.getValue();
        if (oldValid == null || (oldValid ^ newValid)) {
            valid.postValue(newValid);
        }
    }

    public boolean detailsChanged() {
        return !Objects.equals(getJsonGroupDetails(), oldDetails);
    }

    public boolean photoChanged() {
        return !Objects.equals(absolutePhotoUrl, App.absolutePathFromRelative(oldPhotoUrl));
    }

    public boolean personalNoteChanged() {
        return !Objects.equals(personalNote, oldPersonalNote);
    }

    public JsonGroupDetails getJsonGroupDetails() {
        return new JsonGroupDetails(groupName == null ? null : groupName.trim(), groupDescription);
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
