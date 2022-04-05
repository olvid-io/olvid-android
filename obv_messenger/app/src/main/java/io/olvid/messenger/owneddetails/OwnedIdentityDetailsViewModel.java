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

import java.util.Objects;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.StringUtils;


public class OwnedIdentityDetailsViewModel extends ViewModel {
    private JsonIdentityDetailsWithVersionAndPhoto oldDetails = null;
    private String oldNickname = null;
    private boolean oldProfileHidden = false;
    private byte[] bytesOwnedIdentity;

    private String firstName;
    private String lastName;
    private String company;
    private String position;
    private String nickname;
    private boolean profileHidden;
    private byte[] password = null;
    private byte[] salt = null;

    private String absolutePhotoUrl; // absolute path
    private Uri takePictureUri;
    private boolean pictureLocked;
    private boolean detailsLocked;
    private boolean identityInactive;

    private final MutableLiveData<ValidStatus> valid = new MutableLiveData<>(ValidStatus.INVALID);
    private final MutableLiveData<InitialViewContent> initialViewContent = new MutableLiveData<>(null);

    public enum ValidStatus {
        INVALID,
        PUBLISH,
        SAVE,
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity,
                StringUtils.getInitial(((firstName==null)?"":firstName.trim()) + ((lastName==null)?"":lastName.trim())),
                absolutePhotoUrl));
    }

    public byte[] getBytesOwnedIdentity() {
        return bytesOwnedIdentity;
    }

    public void setTakePictureUri(Uri takePictureUri) {
        this.takePictureUri = takePictureUri;
    }

    public Uri getTakePictureUri() {
        return takePictureUri;
    }


    public void setOwnedIdentityDetails(JsonIdentityDetailsWithVersionAndPhoto identityDetails, String nickname, boolean profileHidden) {
        oldDetails = identityDetails;
        oldNickname = nickname;
        oldProfileHidden = profileHidden;

        setAbsolutePhotoUrl(App.absolutePathFromRelative(identityDetails.getPhotoUrl()));
        setFirstName(identityDetails.getIdentityDetails().getFirstName());
        setLastName(identityDetails.getIdentityDetails().getLastName());
        setCompany(identityDetails.getIdentityDetails().getCompany());
        setPosition(identityDetails.getIdentityDetails().getPosition());
        setNickname(nickname);
        setProfileHidden(profileHidden);
        checkValid();
    }

    public void setNickname(String nickname) {
        if (this.absolutePhotoUrl == null){
            String name = (nickname==null?"":nickname.trim()) + (firstName==null?"":firstName.trim()) + (lastName==null?"":lastName.trim());
            if (name.length() > 0) {
                initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, StringUtils.getInitial(name), null));
            } else {
                initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity," ", null));
            }
        }
        this.nickname = nickname;
    }

    public void setProfileHidden(boolean profileHidden) {
        this.profileHidden = profileHidden;
        if (!profileHidden) {
            password = null;
            salt = null;
        }
    }

    public LiveData<ValidStatus> getValid() {
        return valid;
    }

    public LiveData<InitialViewContent> getInitialViewContent() {
        return initialViewContent;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        if (absolutePhotoUrl == null && (nickname == null || nickname.trim().length() == 0)) {
            if (firstName != null && firstName.trim().length() > 0) {
                String newInitial = StringUtils.getInitial(firstName.trim());
                String oldInitial = (this.firstName == null || this.firstName.trim().length() == 0) ? null: StringUtils.getInitial(this.firstName.trim());
                if (!newInitial.equals(oldInitial)) {
                    initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, newInitial, null));
                }
            } else if (this.firstName != null && this.firstName.trim().length() > 0) {
                if (lastName != null && lastName.trim().length() > 0) {
                    initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, StringUtils.getInitial(lastName.trim()), null));
                } else {
                    initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity," ", null));
                }
            }
        }
        this.firstName = firstName;
        checkValid();
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        if (absolutePhotoUrl == null && (firstName == null || firstName.trim().length() == 0)) {
            if ((lastName != null && lastName.trim().length() > 0)) {
                String newInitial = StringUtils.getInitial(lastName.trim());
                String oldInitial = (this.lastName == null || this.lastName.trim().length() == 0) ? null : StringUtils.getInitial(this.lastName.trim());
                if (!newInitial.equals(oldInitial)) {
                    initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, newInitial, null));
                }
            } else if (this.lastName != null && this.lastName.trim().length() > 0)  {
                initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity," ", null));
            }
        }
        this.lastName = lastName;
        checkValid();
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
        checkValid();
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
        checkValid();
    }

    public void setPasswordAndSalt(byte[] password, byte[] salt) {
        this.password = password;
        this.salt = salt;
    }

    public String getAbsolutePhotoUrl() {
        return absolutePhotoUrl;
    }

    public void setAbsolutePhotoUrl(String absolutePhotoUrl) {
        if (absolutePhotoUrl != null) {
            initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, null, absolutePhotoUrl));
        } else if (this.absolutePhotoUrl != null){
            String name = (nickname==null?"":nickname.trim()) + (firstName==null?"":firstName.trim()) + (lastName==null?"":lastName.trim());
            if (name.length() > 0) {
                initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity, StringUtils.getInitial(name), null));
            } else {
                initialViewContent.postValue(new InitialViewContent(bytesOwnedIdentity," ", null));
            }
        }
        this.absolutePhotoUrl = absolutePhotoUrl;
        checkValid();
    }

    private void checkValid() {
        ValidStatus oldValid = valid.getValue();
        final ValidStatus newValid;
        boolean valid = (firstName != null && firstName.trim().length() > 0) ||
                (lastName != null && lastName.trim().length() > 0);
        if (valid) {
            if (detailsChanged() || photoChanged()) {
                newValid = ValidStatus.PUBLISH;
            } else {
                newValid = ValidStatus.SAVE;
            }
        } else {
            newValid = ValidStatus.INVALID;
        }
        if (oldValid == null || (oldValid != newValid)) {
            this.valid.postValue(newValid);
        }
    }

    public boolean detailsChanged() {
        if (detailsLocked) {
            return false;
        }
        if (oldDetails == null) {
            return true;
        }
        return !getJsonIdentityDetails().equals(oldDetails.getIdentityDetails());
    }

    public boolean photoChanged() {
        if (absolutePhotoUrl == null) {
            return oldDetails != null && oldDetails.getPhotoUrl() != null;
        } else {
            return oldDetails == null || !absolutePhotoUrl.equals(App.absolutePathFromRelative(oldDetails.getPhotoUrl()));
        }
    }

    public boolean nicknameChanged() {
        return !Objects.equals(nullOrTrim(oldNickname), nullOrTrim(nickname));
    }

    // this method returns true if the profile switched from hidden to not hidden (or the other way), but also if the profile remains hidden and the password was changed
    public boolean profileHiddenChanged() {
        return (oldProfileHidden ^ profileHidden) || (password != null);
    }

    public JsonIdentityDetails getJsonIdentityDetails() {
        return new JsonIdentityDetails(firstName, lastName, company, position);
    }

    public boolean isIdentityInactive() {
        return identityInactive;
    }

    public void setIdentityInactive(boolean identityInactive) {
        this.identityInactive = identityInactive;
    }

    public boolean getDetailsLocked() {
        return detailsLocked;
    }

    public String getNickname() {
        return nullOrTrim(nickname);
    }

    public boolean isProfileHidden() {
        return profileHidden;
    }

    public void setDetailsLocked(boolean detailsLocked) {
        this.detailsLocked = detailsLocked;
    }

    public void setPictureLocked(boolean pictureLocked) {
        this.pictureLocked = pictureLocked;
    }

    public boolean getPictureLocked() {
        return pictureLocked;
    }

    public byte[] getPassword() {
        return password;
    }

    public byte[] getSalt() {
        return salt;
    }

    private static String nullOrTrim(String in) {
        if (in == null) {
            return null;
        }
        String out = in.trim();
        if (out.length() == 0) {
            return null;
        }
        return out;
    }

    static class InitialViewContent {
        public final byte[] bytesOwnedIdentity;
        public final String initial;
        public final String absolutePhotoUrl;

        public InitialViewContent(byte[] bytesOwnedIdentity, String initial, String absolutePhotoUrl) {
            if (bytesOwnedIdentity == null) {
                this.bytesOwnedIdentity = new byte[0];
            } else {
                this.bytesOwnedIdentity = bytesOwnedIdentity;
            }
            this.initial = initial;
            this.absolutePhotoUrl = absolutePhotoUrl;
        }
    }
}
