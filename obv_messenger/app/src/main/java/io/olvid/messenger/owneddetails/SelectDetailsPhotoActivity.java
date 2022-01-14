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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LockableActivity;


public class SelectDetailsPhotoActivity extends LockableActivity implements View.OnTouchListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    public static final String CROPPED_JPEG_RETURN_INTENT_EXTRA = "cropped_jpeg";

    SelectDetailsPhotoViewModel viewModel;

    private ImageView photoImageView;
    private SeekBar brightnessSeekBar;
    private SeekBar contrastSeekBar;
    private SeekBar saturationSeekBar;
    private SeekBar temperatureSeekBar;

    private final Matrix currentMatrix = new Matrix();
    private RectF overlayRect = new RectF();

    private final PointF startPoint = new PointF();
    private final PointF bitmapMiddlePoint = new PointF();
    private float initialDist;
    private final RectF initialBitmapZone = new RectF();
    private final RectF newBitmapZone = new RectF();
    private float initialScale;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;



    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        viewModel = new ViewModelProvider(this).get(SelectDetailsPhotoViewModel.class);

        setContentView(R.layout.activity_select_details_photo);

        viewModel.getPhotoBitmap().observe(this, this::resetImage);

        photoImageView = findViewById(R.id.photo_image_view);
        photoImageView.setOnTouchListener(this);

        ImageView backButton = findViewById(R.id.button_back);
        backButton.setOnClickListener(this);
        ImageView resetButton = findViewById(R.id.button_reset);
        resetButton.setOnClickListener(this);
        ImageView okButton = findViewById(R.id.button_accept);
        okButton.setOnClickListener(this);
        ImageView rotateButton = findViewById(R.id.button_rotate);
        rotateButton.setOnClickListener(this);
        brightnessSeekBar = findViewById(R.id.brightness_seekbar);
        brightnessSeekBar.setOnSeekBarChangeListener(this);
        contrastSeekBar = findViewById(R.id.contrast_seekbar);
        contrastSeekBar.setOnSeekBarChangeListener(this);
        temperatureSeekBar = findViewById(R.id.temperature_seekbar);
        temperatureSeekBar.setOnSeekBarChangeListener(this);
        saturationSeekBar = findViewById(R.id.saturation_seekbar);
        saturationSeekBar.setOnSeekBarChangeListener(this);
        ImageView overlay = findViewById(R.id.overlay_image_view);
        overlay.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            overlayRect = new RectF(left, top, right, bottom);
            fitBitmapZoneToOverlay();
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.getData() == null) {
            onBackPressed();
            return;
        }

        try {
            viewModel.setPhotoUri(this.getContentResolver(), intent.getData());
        } catch (IOException e) {
            onBackPressed();
        }
    }

    private void resetImage(Bitmap photoBitmap) {
        if (photoBitmap != null) {
            bitmapWidth = photoBitmap.getWidth();
            bitmapHeight = photoBitmap.getHeight();
            photoImageView.setImageBitmap(photoBitmap);
            fitBitmapZoneToOverlay();
        }
    }

    private void fitBitmapZoneToOverlay() {
        RectF bitmapZone = viewModel.getBitmapZone();
        currentMatrix.setRectToRect(bitmapZone, overlayRect, Matrix.ScaleToFit.CENTER);
        photoImageView.setImageMatrix(currentMatrix);
    }

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    private float distance(float x0, float y0, float x1, float y1) {
        float x = x1 - x0;
        float y = y1 - y0;
        return (float) Math.sqrt(x * x + y * y);
    }
    private void bitmapMiddle(PointF point, RectF initialBitmapZone, float x0, float y0, float x1, float y1) {
        float x = (x0 + x1)/2;
        float y = (y0 + y1)/2;
        float alphaX = (x-overlayRect.left)/(overlayRect.right-overlayRect.left);
        float alphaY = (y-overlayRect.top)/(overlayRect.bottom-overlayRect.top);
        point.set(alphaX*(initialBitmapZone.right-initialBitmapZone.left)+initialBitmapZone.left, alphaY*(initialBitmapZone.bottom-initialBitmapZone.top)+initialBitmapZone.top);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                initialBitmapZone.set(viewModel.getBitmapZone());
                initialScale = (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left);
                startPoint.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (event.getPointerCount() > 2) {
                    mode = NONE;
                } else {
                    initialDist = distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                    if (initialDist > 10f) {
                        initialBitmapZone.set(viewModel.getBitmapZone());
                        initialScale = (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left);
                        bitmapMiddle(bitmapMiddlePoint, initialBitmapZone, event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                        mode = ZOOM;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                mode = NONE;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                switch (event.getPointerCount()) {
                    case 3: {
                        float x0, x1, y0, y1;
                        switch (event.getActionIndex()) {
                            case 0:
                                x0 = event.getX(1);
                                x1 = event.getX(2);
                                y0 = event.getY(1);
                                y1 = event.getY(2);
                                break;
                            case 1:
                                x0 = event.getX(0);
                                x1 = event.getX(2);
                                y0 = event.getY(0);
                                y1 = event.getY(2);
                                break;
                            case 2:
                            default:
                                x0 = event.getX(0);
                                x1 = event.getX(1);
                                y0 = event.getY(0);
                                y1 = event.getY(1);
                                break;
                        }
                        initialDist = distance(x0, y0, x1, y1);
                        if (initialDist > 10f) {
                            initialBitmapZone.set(viewModel.getBitmapZone());
                            initialScale = (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left);
                            bitmapMiddle(bitmapMiddlePoint, initialBitmapZone, x0, y0, x1, y1);
                            mode = ZOOM;
                        }
                        break;
                    }
                    case 2: {
                        initialBitmapZone.set(viewModel.getBitmapZone());
                        initialScale = (overlayRect.right - overlayRect.left) / (initialBitmapZone.right - initialBitmapZone.left);
                        if (event.getActionIndex() == 0) {
                            startPoint.set(event.getX(1), event.getY(1));
                        } else {
                            startPoint.set(event.getX(0), event.getY(0));
                        }
                        mode = DRAG;
                        break;
                    }
                    default: {
                        mode = NONE;
                        break;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == DRAG) {
                    newBitmapZone.set(initialBitmapZone.left - (event.getX() - startPoint.x) / initialScale,
                            initialBitmapZone.top - (event.getY() - startPoint.y) / initialScale,
                            initialBitmapZone.right - (event.getX() - startPoint.x) / initialScale,
                            initialBitmapZone.bottom - (event.getY() - startPoint.y) / initialScale);
                    if (newBitmapZone.left < 0) {
                        newBitmapZone.right -= newBitmapZone.left;
                        newBitmapZone.left = 0;
                    } else if (newBitmapZone.right > bitmapWidth) {
                        newBitmapZone.left -= newBitmapZone.right - bitmapWidth;
                        newBitmapZone.right = bitmapWidth;
                    }
                    if (newBitmapZone.top < 0) {
                        newBitmapZone.bottom -= newBitmapZone.top;
                        newBitmapZone.top = 0;
                    } else if (newBitmapZone.bottom > bitmapHeight) {
                        newBitmapZone.top -= newBitmapZone.bottom - bitmapHeight;
                        newBitmapZone.bottom = bitmapHeight;
                    }
                    viewModel.setBitmapZone(newBitmapZone);
                    fitBitmapZoneToOverlay();
                } else if (mode == ZOOM) {
                    float newDist = distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                    if (newDist > 10f) {
                        float newScale = initialScale * newDist / initialDist;
                        float zoneWidth = (overlayRect.right - overlayRect.left) / newScale;
                        if (zoneWidth > bitmapWidth || zoneWidth > bitmapHeight) {
                            if (bitmapWidth < bitmapHeight) {
                                newScale = (overlayRect.right - overlayRect.left) / bitmapWidth;
                            } else {
                                newScale = (overlayRect.right - overlayRect.left) / bitmapHeight;
                            }
                        }
                        float ratio = initialScale / newScale;

                        newBitmapZone.set((initialBitmapZone.left - bitmapMiddlePoint.x) * ratio + bitmapMiddlePoint.x,
                                (initialBitmapZone.top - bitmapMiddlePoint.y) * ratio + bitmapMiddlePoint.y,
                                (initialBitmapZone.right - bitmapMiddlePoint.x) * ratio + bitmapMiddlePoint.x,
                                (initialBitmapZone.bottom - bitmapMiddlePoint.y) * ratio + bitmapMiddlePoint.y);
                        if (newBitmapZone.left < 0) {
                            newBitmapZone.right -= newBitmapZone.left;
                            newBitmapZone.left = 0;
                        } else if (newBitmapZone.right > bitmapWidth) {
                            newBitmapZone.left -= newBitmapZone.right - bitmapWidth;
                            newBitmapZone.right = bitmapWidth;
                        }
                        if (newBitmapZone.top < 0) {
                            newBitmapZone.bottom -= newBitmapZone.top;
                            newBitmapZone.top = 0;
                        } else if (newBitmapZone.bottom > bitmapHeight) {
                            newBitmapZone.top -= newBitmapZone.bottom - bitmapHeight;
                            newBitmapZone.bottom = bitmapHeight;
                        }
                        viewModel.setBitmapZone(newBitmapZone);
                        fitBitmapZoneToOverlay();
                    }

                    fitBitmapZoneToOverlay();
                }
                break;
            }
        }
        return true;
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_back) {
            onBackPressed();
        } else if (id == R.id.button_rotate) {
            viewModel.rotate90();
        } else if (id == R.id.button_reset) {
            viewModel.resetBitmapZone();
            viewModel.setBrightness(0);
            viewModel.setContrast(0);
            viewModel.setSaturation(0);
            viewModel.setTemperature(0);
            brightnessSeekBar.setProgress(255);
            contrastSeekBar.setProgress(255);
            saturationSeekBar.setProgress(255);
            temperatureSeekBar.setProgress(255);
            photoImageView.setColorFilter(viewModel.getColorMatrix());
            fitBitmapZoneToOverlay();
        } else if (id == R.id.button_accept) {// extract the bitmap and return it
            Bitmap bitmap = viewModel.getFullBitmap();
            int scaled = viewModel.getScaled();
            if (bitmap != null) {
                Rect bitmapZone = new Rect();
                RectF unscaledZone = viewModel.getBitmapZone();
                new RectF(unscaledZone.left*scaled, unscaledZone.top*scaled, unscaledZone.right*scaled, unscaledZone.bottom*scaled).roundOut(bitmapZone);

                int size = bitmapZone.right - bitmapZone.left;
                if (size > 1080) {
                    size = 1080;
                }
                Bitmap cropped = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(cropped);
                Paint paint = new Paint();
                paint.setColorFilter(viewModel.getColorMatrix());
                canvas.drawBitmap(bitmap, bitmapZone, new Rect(0, 0, size, size), paint);

                File photoDir = new File(getCacheDir(), App.CAMERA_PICTURE_FOLDER);
                File photoFile = new File(photoDir, new SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(new Date()) + "_cropped.jpg");
                try (FileOutputStream out = new FileOutputStream(photoFile)) {
                    cropped.compress(Bitmap.CompressFormat.JPEG, 75, out);
                    out.flush();
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(CROPPED_JPEG_RETURN_INTENT_EXTRA, photoFile.getAbsolutePath());
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                } catch (Exception e) {
                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                    e.printStackTrace();
                }
            } else {
                App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    public void onBackPressed() {
        Intent returnIntent = new Intent();
        setResult(Activity.RESULT_CANCELED, returnIntent);
        finish();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar.getId() == R.id.brightness_seekbar) {
            viewModel.setBrightness(progress-255);
        } else if (seekBar.getId() == R.id.contrast_seekbar) {
            viewModel.setContrast(progress-255);
        } else if (seekBar.getId() == R.id.temperature_seekbar) {
            viewModel.setTemperature(progress-255);
        } else if (seekBar.getId() == R.id.saturation_seekbar) {
            viewModel.setSaturation(progress-255);
        }
        photoImageView.setColorFilter(viewModel.getColorMatrix());
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // do nothing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // do nothing
    }
}
