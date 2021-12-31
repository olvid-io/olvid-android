/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.IOException;

import androidx.exifinterface.media.ExifInterface;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.entity.DiscussionCustomization;


public class ImageViewPreference extends Preference {
    ImageView widgetImageView = null;
    DiscussionCustomization.ColorJson colorJson;
    String imagePath;
    Integer color;

    public ImageViewPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWidgetLayoutResource(R.layout.preference_widget_imageview);
    }

    public ImageViewPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWidgetLayoutResource(R.layout.preference_widget_imageview);
    }

    public ImageViewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_imageview);
    }

    public ImageViewPreference(Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.preference_widget_imageview);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        widgetImageView = (ImageView) holder.findViewById(R.id.imageView);
        redraw();
    }

    public void setColor(DiscussionCustomization.ColorJson colorJson) {
        this.colorJson = colorJson;
        this.imagePath = null;
        this.color = null;
        redraw();
    }

    public void setImage(String imagePath) {
        this.colorJson = null;
        this.imagePath = imagePath;
        this.color = null;
        redraw();
    }

    public void setColor(Integer color) {
        this.colorJson = null;
        this.imagePath = null;
        this.color = color;
        redraw();
    }

    private void redraw() {
        if (widgetImageView == null) {
            return;
        }
        if (colorJson != null) {
            widgetImageView.setBackgroundColor(0xff000000 + colorJson.color);
            widgetImageView.setImageResource(R.drawable.pref_imageview_color_preview);
            widgetImageView.setImageAlpha(255 - (int)(colorJson.alpha*255));
        } else if (imagePath != null) {
            widgetImageView.setBackgroundColor(0x00ffffff);
            widgetImageView.setImageAlpha(255);
            App.runThread(()->{
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                try {
                    ExifInterface exifInterface = new ExifInterface(imagePath);
                    int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
                } catch (IOException e) {
                    Logger.d("Error creating ExifInterface for file " + imagePath);
                }
                final Bitmap finalBitmap = bitmap;
                new Handler(Looper.getMainLooper()).post(()->widgetImageView.setImageBitmap(finalBitmap));
            });
        } else if (color != null) {
            widgetImageView.setBackgroundColor(0xff000000 + color);
            widgetImageView.setImageDrawable(null);
        } else {
            widgetImageView.setBackgroundColor(0x00ffffff);
            widgetImageView.setImageAlpha(255);
            widgetImageView.setImageResource(R.drawable.pref_imageview_nothing);
        }
    }
}
