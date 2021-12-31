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

package io.olvid.messenger.fragments;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.PreviewUtils;

public class FullScreenImageFragment extends Fragment {
    public static final String IMAGE_PATH = "image_path";
    private String absoluteImagePath;

    private AppCompatActivity activity;
    private ImageView imageView;


    public static FullScreenImageFragment newInstance(String absoluteImagePath) {
        FullScreenImageFragment fragment = new FullScreenImageFragment();
        Bundle args = new Bundle();
        args.putString(IMAGE_PATH, absoluteImagePath);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
        Bundle arguments = getArguments();
        if (arguments != null) {
            absoluteImagePath = arguments.getString(IMAGE_PATH);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_full_screen_image, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setOnClickListener(v -> activity.onBackPressed());

        imageView = view.findViewById(R.id.image_view);

        App.runThread(() -> {
            Drawable drawable = getDrawable();
            if (drawable == null) {
                activity.runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_broken_image));
            } else {
                activity.runOnUiThread(() -> imageView.setImageDrawable(drawable));
            }
        });
    }

    private Drawable getDrawable() {
        if (absoluteImagePath == null || !new File(absoluteImagePath).exists()) {
            return null;
        }

        Resources resources = getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(new File(absoluteImagePath));
                return ImageDecoder.decodeDrawable(src, (ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source source) -> {
                    int subSampling = Math.max(info.getSize().getHeight() / metrics.heightPixels, info.getSize().getWidth() / metrics.widthPixels);
                    if (subSampling > 1) {
                        decoder.setTargetSampleSize(subSampling);
                    }
                });
            } catch (Exception e) {
                // on API28 emulator, decoding sometimes fails --> fallback to bitmap method
            }
        }

        // if we reached this point, we could not decode as a drawable --> decode as a bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(absoluteImagePath, options);
        int subSampling = Math.max(options.outHeight / metrics.heightPixels, options.outWidth / metrics.widthPixels);

        options = new BitmapFactory.Options();
        options.inSampleSize = subSampling;
        Bitmap bitmap = BitmapFactory.decodeFile(absoluteImagePath, options);
        try {
            ExifInterface exifInterface = new ExifInterface(absoluteImagePath);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
        } catch (IOException e) {
            Logger.d("Error creating ExifInterface for file " + absoluteImagePath);
        }

        return new BitmapDrawable(resources, bitmap);
    }
}
