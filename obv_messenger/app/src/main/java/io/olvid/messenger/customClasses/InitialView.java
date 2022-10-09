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

package io.olvid.messenger.customClasses;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel;

public class InitialView extends View {
    private byte[] bytes;
    private String initial;
    private String photoUrl; // absolute path
    private boolean keycloakCertified = false;
    private boolean locked = false;
    private boolean inactive = false;
    private boolean notOneToOne = false;
    private Integer contactTrustLevel = null;

    private Paint backgroundPaint;
    private Paint insidePaint;
    private Bitmap overlayBitmap;

    private int width;
    private int height;
    private int size = 0;
    private float top;
    private float left;
    private Bitmap bitmap;
    private float insideX;
    private float insideY;


    public InitialView(Context context) {
        super(context);
    }

    public InitialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            setInitial(new byte[]{0,1,35}, "A");
            setKeycloakCertified(true);
        }
    }

    public InitialView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public InitialView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setContact(Contact contact) {
        boolean changed = false;
        if (!Arrays.equals(contact.bytesContactIdentity, bytes)) {
            bytes = contact.bytesContactIdentity;
            changed = true;
        }
        String contactInitial = StringUtils.getInitial(contact.getCustomDisplayName());
        if (!Objects.equals(initial, contactInitial)) {
            initial = contactInitial;
            changed = true;
        }
        String contactPhotoUrl = App.absolutePathFromRelative(contact.getCustomPhotoUrl());
        if (!Objects.equals(photoUrl, contactPhotoUrl)) {
            photoUrl = contactPhotoUrl;
            changed = true;
        }
        if (keycloakCertified != contact.keycloakManaged) {
            keycloakCertified = contact.keycloakManaged;
            changed = true;
        }
        if (inactive == contact.active) { // We are indeed checking that the value changed ;)
            inactive = !contact.active;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        if (notOneToOne == contact.oneToOne) { // We are indeed checking that the value changed ;)
            notOneToOne = !contact.oneToOne;
            changed = true;
        }
        if (contactTrustLevel == null || contactTrustLevel != contact.trustLevel) {
            contactTrustLevel = contact.trustLevel;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setOwnedIdentity(OwnedIdentity ownedIdentity) {
        boolean changed = false;
        if (!Arrays.equals(ownedIdentity.bytesOwnedIdentity, bytes)) {
            bytes = ownedIdentity.bytesOwnedIdentity;
            changed = true;
        }
        String contactInitial = StringUtils.getInitial(ownedIdentity.getCustomDisplayName());
        if (!Objects.equals(initial, contactInitial)) {
            initial = contactInitial;
            changed = true;
        }
        String contactPhotoUrl = App.absolutePathFromRelative(ownedIdentity.photoUrl);
        if (!Objects.equals(photoUrl, contactPhotoUrl)) {
            photoUrl = contactPhotoUrl;
            changed = true;
        }
        if (keycloakCertified != ownedIdentity.keycloakManaged) {
            keycloakCertified = ownedIdentity.keycloakManaged;
            changed = true;
        }
        if (inactive == ownedIdentity.active) { // We are indeed checking that the value changed ;)
            inactive = !ownedIdentity.active;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        if (notOneToOne) { // We are indeed checking that the value changed ;)
            notOneToOne = false;
            changed = true;
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setGroup(Group group) {
        boolean changed = false;
        if (!Arrays.equals(group.bytesGroupOwnerAndUid, bytes)) {
            bytes = group.bytesGroupOwnerAndUid;
            changed = true;
        }
        if (initial != null) {
            initial = null;
            changed = true;
        }
        String groupPhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl());
        if (!Objects.equals(photoUrl, groupPhotoUrl)) {
            photoUrl = groupPhotoUrl;
            changed = true;
        }
        if (keycloakCertified) {
            keycloakCertified = false;
            changed = true;
        }
        if (inactive) {
            inactive = false;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        if (notOneToOne) {
            notOneToOne = false;
            changed = true;
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setGroup2(Group2 group) {
        boolean changed = false;
        if (!Arrays.equals(group.bytesGroupIdentifier, bytes)) {
            bytes = group.bytesGroupIdentifier;
            changed = true;
        }
        if (initial != null) {
            initial = null;
            changed = true;
        }
        String groupPhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl());
        if (!Objects.equals(photoUrl, groupPhotoUrl)) {
            photoUrl = groupPhotoUrl;
            changed = true;
        }
        if (keycloakCertified != group.keycloakManaged) {
            keycloakCertified = group.keycloakManaged;
            changed = true;
        }
        if (inactive) {
            inactive = false;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        if (notOneToOne) {
            notOneToOne = false;
            changed = true;
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }


    public void setDiscussion(Discussion discussion) {
        boolean changed = false;
        switch (discussion.status) {
            case Discussion.STATUS_LOCKED: {
                if (!locked) {
                    locked = true;
                    changed = true;
                }
                if (bytes == null || bytes.length != 0) {
                    bytes = new byte[0];
                    changed = true;
                }
                if (initial == null || initial.length() != 0) {
                    initial = "";
                    changed = true;
                }
                break;
            }
            case Discussion.STATUS_NORMAL:
            default: {
                if (locked) {
                    locked = false;
                    changed = true;
                }
                switch (discussion.discussionType) {
                    case Discussion.TYPE_CONTACT: {
                        String discussionInitial = StringUtils.getInitial(discussion.title);
                        if (!Objects.equals(initial, discussionInitial)) {
                            initial = discussionInitial;
                            changed = true;
                        }

                        break;
                    }
                    case Discussion.TYPE_GROUP:
                    case Discussion.TYPE_GROUP_V2: {
                        if (initial != null) {
                            initial = null;
                            changed = true;
                        }
                        break;
                    }
                    default:
                        Logger.e("Unknown discussion type");
                        return;
                }
                if (!Arrays.equals(bytes, discussion.bytesDiscussionIdentifier)) {
                    bytes = discussion.bytesDiscussionIdentifier;
                    changed = true;
                }
                break;
            }
        }

        String discussionPhotoUrl = App.absolutePathFromRelative(discussion.photoUrl);
        if (!Objects.equals(photoUrl, discussionPhotoUrl)) {
            photoUrl = discussionPhotoUrl;
            changed = true;
        }
        if (keycloakCertified != discussion.keycloakManaged) {
            keycloakCertified = discussion.keycloakManaged;
            changed = true;
        }
        if (inactive == discussion.active) { // We are indeed checking that the value changed ;)
            inactive = !discussion.active;
            changed = true;
        }
        if (!Objects.equals(contactTrustLevel, discussion.trustLevel)) {
            contactTrustLevel = discussion.trustLevel;
            changed = true;
        }
        if (notOneToOne) {
            notOneToOne = false;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }


    public void setDiscussion(FilteredDiscussionListViewModel.SearchableDiscussion discussion) {
        boolean changed = false;
        if (!Arrays.equals(bytes, discussion.byteIdentifier)) {
            bytes = discussion.byteIdentifier;
            changed = true;
        }
        if (discussion.isGroupDiscussion) {
            if (initial != null) {
                initial = null;
                changed = true;
            }
            if (locked) {
                locked = false;
                changed = true;
            }
        } else if (discussion.byteIdentifier.length != 0) {
            String discussionInitial = StringUtils.getInitial(discussion.title);
            if (!Objects.equals(initial, discussionInitial)) {
                initial = discussionInitial;
                changed = true;
            }
            if (locked) {
                locked = false;
                changed = true;
            }
        } else {
            if (initial == null || initial.length() != 0) {
                initial = "";
                changed = true;
            }
            if (!locked) {
                locked = true;
                changed = true;
            }
        }
        String discussionPhotoUrl = App.absolutePathFromRelative(discussion.photoUrl);
        if (!Objects.equals(photoUrl, discussionPhotoUrl)) {
            photoUrl = discussionPhotoUrl;
            changed = true;
        }
        if (keycloakCertified != discussion.keycloakManaged) {
            keycloakCertified = discussion.keycloakManaged;
            changed = true;
        }
        if (inactive == discussion.active) { // We are indeed checking that the value changed ;)
            inactive = !discussion.active;
            changed = true;
        }
        if (notOneToOne) {
            notOneToOne = false;
            changed = true;
        }
        if (contactTrustLevel != null) {
            // we could properly set this for ontToOne discussions, but this is not worth the added work!
            contactTrustLevel = null;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setFromCache(@NonNull byte[] bytesIdentifier) {
        boolean changed = false;
        String displayName;
        if (Arrays.equals(bytesIdentifier, AppSingleton.getBytesCurrentIdentity())) {
            OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
            if (ownedIdentity != null) {
                displayName = ownedIdentity.getCustomDisplayName();
            } else {
                displayName = AppSingleton.getContactCustomDisplayName(bytesIdentifier);
            }
        } else {
            displayName = AppSingleton.getContactCustomDisplayName(bytesIdentifier);
        }
        String contactInitial;
        byte[] bytes;
        if (displayName == null) {
            contactInitial = "?";
            bytes = new byte[0];
        } else {
            contactInitial = StringUtils.getInitial(displayName);
            bytes = bytesIdentifier;
        }

        if (!Arrays.equals(this.bytes, bytes)) {
            this.bytes = bytes;
            changed = true;
        }
        if (!Objects.equals(initial, contactInitial)) {
            initial = contactInitial;
            changed = true;
        }
        String contactPhotoUrl = App.absolutePathFromRelative(AppSingleton.getContactPhotoUrl(bytesIdentifier));
        if (!Objects.equals(photoUrl, contactPhotoUrl)) {
            photoUrl = contactPhotoUrl;
            changed = true;
        }
        boolean keycloak = AppSingleton.getContactKeycloakManaged(bytesIdentifier);
        if (keycloakCertified != keycloak) {
            keycloakCertified = keycloak;
            changed = true;
        }
        boolean inact = AppSingleton.getContactInactive(bytesIdentifier);
        if (inactive != inact) {
            inactive = inact;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        boolean oneToOne = AppSingleton.getContactOneToOne(bytesIdentifier);
        if (notOneToOne == oneToOne) { // We are indeed checking that the value changed ;)
            notOneToOne = !oneToOne;
            changed = true;
        }
        Integer trustLevel = AppSingleton.getContactTrustLevel(bytesIdentifier);
        if (!Objects.equals(contactTrustLevel, trustLevel)) {
            contactTrustLevel = trustLevel;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setUnknown() {
        boolean changed = false;
        if (bytes == null || bytes.length != 0) {
            bytes = new byte[0];
            changed = true;
        }
        if (!Objects.equals(initial, "?")) {
            initial = "?";
            changed = true;
        }
        if (photoUrl != null) {
            photoUrl = null;
            changed = true;
        }
        if (keycloakCertified) {
            keycloakCertified = false;
            changed = true;
        }
        if (inactive) {
            inactive = false;
            changed = true;
        }
        if (locked) {
            locked = false;
            changed = true;
        }
        if (notOneToOne) {
            notOneToOne = false;
            changed = true;
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null;
            changed = true;
        }
        if (changed) {
            bitmap = null;
            init();
        }
    }

    public void setGroup(byte[] groupId) {
        this.bitmap = null;
        this.photoUrl = null;
        this.bytes = groupId;
        this.initial = null;
        init();
    }

    public void setInitial(byte[] identityBytes, String initial) {
        this.bitmap = null;
        this.photoUrl = null;
        this.bytes = identityBytes;
        this.initial = initial;
        init();
    }

    public void setPhotoUrl(byte[] bytes, String relativePathPhotoUrl) {
        this.bitmap = null;
        this.photoUrl = App.absolutePathFromRelative(relativePathPhotoUrl);
        this.bytes = bytes;
        init();
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setAbsolutePhotoUrl(byte[] bytes, String absolutePhotoUrl) {
        this.bitmap = null;
        this.photoUrl = absolutePhotoUrl;
        this.bytes = bytes;
        this.initial = null;
        init();
    }

    public void setKeycloakCertified(boolean keycloakCertified) {
        if (this.keycloakCertified != keycloakCertified) {
            this.bitmap = null;
            this.keycloakCertified = keycloakCertified;
            init();
        }
    }

    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            this.locked = locked;
            init();
        }
    }

    public void setInactive(boolean inactive) {
        if (this.inactive != inactive) {
            this.inactive = inactive;
            init();
        }
    }

    public void setNotOneToOne() {
        if (notOneToOne) {
            notOneToOne = false;
            init();
        }
    }

    public void setNullTrustLevel() {
        if (contactTrustLevel != null) {
            contactTrustLevel = null;
            init();
        }
    }



    private void init() {
        if (bytes == null || size == 0) {
            return;
        }

        invalidate();

        top = (height - size) * .5f;
        left = (width - size) * .5f;

        if (photoUrl != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoUrl, options);
            int photoSize = Math.min(options.outWidth, options.outHeight);
            if (photoSize != -1) {
                int subSampling = photoSize / this.size;
                options = new BitmapFactory.Options();
                options.inSampleSize = subSampling;
                Bitmap squareBitmap = BitmapFactory.decodeFile(photoUrl, options);
                if (squareBitmap != null) {
                    try {
                        ExifInterface exifInterface = new ExifInterface(photoUrl);
                        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation);
                    } catch (IOException e) {
                        // exif error, do nothing
                    }
                    bitmap = Bitmap.createBitmap(this.size, this.size, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);

                    RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), squareBitmap);
                    roundedDrawable.setCornerRadius(this.size / 2f);
                    roundedDrawable.setBounds(0, 0, this.size, this.size);
                    if (locked || inactive) {
                        ColorMatrix colorMatrix = new ColorMatrix();
                        colorMatrix.setSaturation(.5f);
                        roundedDrawable.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                    }
                    roundedDrawable.draw(canvas);

                    if (contactTrustLevel != null && SettingsActivity.showTrustLevels()) {
                        int dotSize = (int) (.3f * this.size);
                        Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        clearPaint.setColor(Color.TRANSPARENT);
                        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        canvas.drawOval(new RectF(this.size - dotSize, this.size - dotSize, this.size, this.size), clearPaint);
                        Paint dotPaint = getTrustPaint(getContext(), contactTrustLevel);
                        canvas.drawOval(new RectF(this.size - .875f * dotSize, this.size - .875f * dotSize, this.size - .125f * dotSize, this.size - .125f * dotSize), dotPaint);
                        if (notOneToOne) {
                            canvas.drawOval(new RectF(this.size - .65f * dotSize, this.size - .65f * dotSize, this.size - .35f * dotSize, this.size - .35f * dotSize), clearPaint);
                        }
                    }

                    if (locked) {
                        int lockSize = (int) (.3f*this.size);
                        Bitmap keycloakBitmap = Bitmap.createBitmap(lockSize, lockSize, Bitmap.Config.ARGB_8888);
                        Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                        Drawable lockDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_lock_circled, null);
                        if (lockDrawable != null) {
                            lockDrawable.setBounds(0, 0, lockSize, lockSize);
                            lockDrawable.draw(keycloakCanvas);
                        }
                        canvas.drawBitmap(keycloakBitmap, this.size-lockSize,  0, null);
                    } else if (inactive) {
                        int blockedSize = (int) (.8f*this.size);
                        Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                        Canvas blockedCanvas = new Canvas(blockedBitmap);
                        Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                        if (blockedDrawable != null) {
                            blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                            blockedDrawable.draw(blockedCanvas);
                        }
                        canvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
                    } else if (keycloakCertified) {
                        int keycloakSize = (int) (.3f*this.size);
                        Bitmap keycloakBitmap = Bitmap.createBitmap(keycloakSize, keycloakSize, Bitmap.Config.ARGB_8888);
                        Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                        Drawable keycloakDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_keycloak_certified, null);
                        if (keycloakDrawable != null) {
                            keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize);
                            keycloakDrawable.draw(keycloakCanvas);
                        }
                        canvas.drawBitmap(keycloakBitmap, this.size-keycloakSize,  0, null);
                    }

                    return;
                }
            }
        }

        int lightColor = getLightColor(bytes);
        int darkColor = getDarkColor(bytes);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(lightColor);

        insidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        insidePaint.setStyle(Paint.Style.FILL);
        insidePaint.setColor(darkColor);
        insidePaint.setTextSize(size * .6f);
        insidePaint.setTextAlign(Paint.Align.CENTER);

        insidePaint.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));

        if (locked) {
            int bitmapSize = (int) (size * .45f);
            overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(overlayBitmap);
            Drawable overlayDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_lock, null);
            if (overlayDrawable != null) {
                overlayDrawable.setColorFilter(new PorterDuffColorFilter(darkColor, PorterDuff.Mode.SRC_IN));
                overlayDrawable.setBounds(0, 0, bitmapSize, bitmapSize);
                overlayDrawable.draw(bitmapCanvas);
            }

            insideX = (size - bitmapSize) * .5f;
            insideY = (size - bitmapSize) * .5f;
        } else {
            if (initial == null) {
                int bitmapSize = (int) (size * .75f);
                overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(overlayBitmap);
                Drawable groupDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_group, null);
                if (groupDrawable != null) {
                    groupDrawable.setColorFilter(new PorterDuffColorFilter(darkColor, PorterDuff.Mode.SRC_IN));
                    groupDrawable.setBounds(0, 0, bitmapSize, bitmapSize);
                    groupDrawable.draw(bitmapCanvas);
                }

                insideX = (size - bitmapSize) * .5f;
                insideY = (size - bitmapSize) * .5f;
            } else {
                overlayBitmap = null;
                insideX = size * .5f;
                insideY = size * .5f - (insidePaint.descent() + insidePaint.ascent()) / 2f;
            }
        }
    }

    private int getLightColor(byte[] bytes) {
        int hue = hueFromBytes(bytes);
        if (bytes.length == 0){
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.5f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.9f});
            }
        } else {
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0.6f, 0.7f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0.8f, 0.9f});
            }
        }
    }

    private int getDarkColor(byte[] bytes) {
        int hue = hueFromBytes(bytes);
        if (bytes.length == 0){
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.4f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0, 0.7f});
            }
        } else {
            if ((getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{hue, 0.5f, 0.5f});
            } else {
                return ColorUtils.HSLToColor(new float[]{hue, 0.7f, 0.7f});
            }
        }
    }

    public static int getTextColor(Context context, byte[] bytes, Integer hue) {
        int computedHue = (hue != null) ? hue : hueFromBytes(bytes);
        if (bytes.length == 0) {
            return ColorUtils.HSLToColor(new float[]{computedHue, 0, 0.4f});
        } else {
            if ((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0.5f, 0.4f});
            } else {
                return ColorUtils.HSLToColor(new float[]{computedHue, 0.7f, 0.4f});
            }
        }
    }

    private static Paint getTrustPaint(Context context, int contactTrustLevel) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (contactTrustLevel) {
            case 4:
                paint.setColor(ContextCompat.getColor(context, R.color.green));
                break;
            case 3:
                paint.setColor(ContextCompat.getColor(context, R.color.olvid_gradient_light));
                break;
            case 2:
            case 1:
                paint.setColor(ContextCompat.getColor(context, R.color.orange));
                break;
            default:
                paint.setColor(ContextCompat.getColor(context, R.color.red));
                break;
        }
        return paint;
    }

    public static int hueFromBytes(byte[] bytes) {
        if (bytes == null) {
            return 0;
        }
        int result = 1;
        for (byte element : bytes) {
            result = 31 * result + element;
        }
        return ((result & 0xff)*360)/256;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        setSize(w, h);
    }

    public void setSize(int w, int h) {
        bitmap = null;
        width = w;
        height = h;
        size = Math.min(width, height);
        init();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawOnCanvas(canvas);
    }

    public void drawOnCanvas(Canvas canvas) {
        try {
            if (bitmap == null) {
                if (backgroundPaint == null) {
                    return;
                }
                Bitmap localBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(localBitmap);

                bitmapCanvas.drawOval(new RectF(0, 0, size, size), backgroundPaint);

                if (contactTrustLevel != null && SettingsActivity.showTrustLevels()) {
                    int dotSize = (int) (.3f * this.size);
                    Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    clearPaint.setColor(Color.TRANSPARENT);
                    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    bitmapCanvas.drawOval(new RectF(this.size - dotSize, this.size - dotSize, this.size, this.size), clearPaint);
                    Paint dotPaint = getTrustPaint(getContext(), contactTrustLevel);
                    bitmapCanvas.drawOval(new RectF(this.size - .875f * dotSize, this.size - .875f * dotSize, this.size - .125f * dotSize, this.size - .125f * dotSize), dotPaint);
                    if (notOneToOne) {
                        bitmapCanvas.drawOval(new RectF(this.size - .65f * dotSize, this.size - .65f * dotSize, this.size - .35f * dotSize, this.size - .35f * dotSize), clearPaint);
                    }
                }


                if (overlayBitmap != null) {
                    bitmapCanvas.drawBitmap(overlayBitmap, insideX, insideY, insidePaint);
                } else if (initial != null) {
                    bitmapCanvas.drawText(initial, insideX, insideY, insidePaint);
                }

                if (inactive) {
                    int blockedSize = (int) (.8f*this.size);
                    Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                    Canvas blockedCanvas = new Canvas(blockedBitmap);
                    Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                    if (blockedDrawable != null) {
                        blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                        blockedDrawable.draw(blockedCanvas);
                    }
                    bitmapCanvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
                } else if (keycloakCertified) {
                    int keycloakSize = (int) (.3f * size);
                    Bitmap keycloakBitmap = Bitmap.createBitmap(keycloakSize, keycloakSize, Bitmap.Config.ARGB_8888);
                    Canvas keycloakCanvas = new Canvas(keycloakBitmap);
                    Drawable keycloakDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_keycloak_certified, null);
                    if (keycloakDrawable != null) {
                        keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize);
                        keycloakDrawable.draw(keycloakCanvas);
                    }
                    bitmapCanvas.drawBitmap(keycloakBitmap, size - keycloakSize, 0, null);
                }

                bitmap = localBitmap;
            }

            canvas.drawBitmap(bitmap, left, top, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Bitmap getAdaptiveBitmap() {
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.shortcut_icon_size);
        int innerSize = getContext().getResources().getDimensionPixelSize(R.dimen.inner_shortcut_icon_size);
        int padding = (size-innerSize)/2;
        setSize(innerSize, innerSize);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (backgroundPaint != null) {
            canvas.drawPaint(backgroundPaint);
        } else {
            Paint blackBackgroundPaint = new Paint();
            blackBackgroundPaint.setColor(Color.BLACK);
            canvas.drawPaint(blackBackgroundPaint);
        }

        if (photoUrl != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoUrl, options);
            int bitmapSize = Math.min(options.outWidth, options.outHeight);
            if (bitmapSize != -1) {
                int subSampling = bitmapSize / innerSize;
                options = new BitmapFactory.Options();
                options.inSampleSize = subSampling;
                Bitmap squareBitmap = BitmapFactory.decodeFile(photoUrl, options);
                if (squareBitmap != null) {
                    try {
                        ExifInterface exifInterface = new ExifInterface(photoUrl);
                        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation);
                    } catch (IOException e) {
                        // do nothing --> no rotation
                    }
                    if (locked || inactive) {
                        ColorMatrix colorMatrix = new ColorMatrix();
                        colorMatrix.setSaturation(.5f);
                        Paint matrixPaint = new Paint();
                        matrixPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                        //noinspection DuplicateExpressions
                        canvas.drawBitmap(squareBitmap, null, new Rect(padding, padding, innerSize + padding, innerSize + padding), matrixPaint);
                    } else {
                        //noinspection DuplicateExpressions
                        canvas.drawBitmap(squareBitmap, null, new Rect(padding, padding, innerSize + padding, innerSize + padding), null);
                    }
                }
            }
        } else {
            if (overlayBitmap != null) {
                canvas.drawBitmap(overlayBitmap, padding + insideX, padding + insideY, insidePaint);
            } else {
                canvas.drawText(initial, padding + insideX, padding + insideY, insidePaint);
            }

            if (inactive) {
                int blockedSize = (int) (.8f*this.size);
                Bitmap blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, Bitmap.Config.ARGB_8888);
                Canvas blockedCanvas = new Canvas(blockedBitmap);
                Drawable blockedDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_block_outlined, null);
                if (blockedDrawable != null) {
                    blockedDrawable.setBounds(0, 0, blockedSize, blockedSize);
                    blockedDrawable.draw(blockedCanvas);
                }
                canvas.drawBitmap(blockedBitmap, (this.size-blockedSize)/2f,  (this.size-blockedSize)/2f, null);
            }
        }
        return bitmap;
    }
}
