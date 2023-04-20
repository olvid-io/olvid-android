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

package io.olvid.messenger.gallery;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GalleryImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static final int DOUBLE_CLICK_DURATION = 500;
    private static final int CLICK_DURATION = 200;

    private enum Mode {
        NONE,
        DRAG,
        ZOOM,
    }

    // drag and zoom control
    private Mode mode = Mode.NONE;
    private final PointF dragStartPoint = new PointF();
    private final PointF zoomBitmapMiddle = new PointF();
    private float zoomInitialDist;
    private final PointF initialBitmapCenterPoint = new PointF();
    private float initialBitmapScale;
    private FlingAnimation flingAnimation;

    // image scale and offset
    private final Matrix matrix = new Matrix();
    private boolean autoFit = true;
    private final PointF bitmapCenterPoint = new PointF();
    private float bitmapScale; // with a bitmapScale of 3, 3 screen pixels are used to display 1 bitmap pixel
    private int bitmapWidth;
    private int bitmapHeight;

    private boolean draggable = false;
    private ParentViewPagerUserInputController parentViewPagerUserInputController = null;
    private Runnable singleTapUpCallback = null;


    private final Interpolator interpolator = new AccelerateDecelerateInterpolator();

    // click / double click detection
    private boolean clicking = false;
    private boolean doubleClicking = false;
    private long doubleClickFirstTimestamp = 0;
    private final Handler singleTapHandler = new Handler(Looper.getMainLooper());

    private final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (draggable) {
                if (flingAnimation != null) {
                    flingAnimation.stopped = true;
                }
                flingAnimation = new FlingAnimation(velocityX, velocityY);
                postOnAnimation(flingAnimation);
                return true;
            }
            return false;
        }
    });

    public GalleryImageView(@NonNull Context context) {
        super(context);
    }

    public GalleryImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public GalleryImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        recomputeMatrix(false, true, false);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            bitmapWidth = drawable.getIntrinsicWidth();
            bitmapHeight = drawable.getIntrinsicHeight();
        } else {
            bitmapWidth = -1;
            bitmapHeight = -1;
        }
        recomputeMatrix(true, false, false);
    }

    public void setParentViewPagerUserInputController(ParentViewPagerUserInputController parentViewPagerUserInputController) {
        this.parentViewPagerUserInputController = parentViewPagerUserInputController;
    }

    void setSingleTapUpCallback(Runnable singleTapUpCallback) {
        this.singleTapUpCallback = singleTapUpCallback;
    }


    private void recomputeMatrix(boolean forceAutoFit, boolean maintainAutoFit, boolean animate) {
        if (bitmapWidth == -1 || bitmapHeight == -1) {
            draggable = false;
            return;
        }
        if (forceAutoFit || (maintainAutoFit && autoFit)) {
            autoFit = true;
            bitmapCenterPoint.set(bitmapWidth / 2.f, bitmapHeight / 2.f);
            bitmapScale = Math.min((float) getWidth() / bitmapWidth , (float) getHeight() / bitmapHeight);
            draggable = false;
        } else {
            /////////
            // MIN SCALE:
            // - for large images: fit screen
            // - for small ones: 1px for 1px
            float minScale = Math.min(1.f, Math.min((float) getWidth() / bitmapWidth , (float) getHeight() / bitmapHeight));
            if (bitmapScale < minScale) {
                bitmapScale = minScale;
            }

            ////////
            // MIN BOUNDS:
            // - screen viewport never goes outside image
            // - for small images/on the small axis, center the viewport
            float minBitmapCenterX = Math.min(bitmapWidth/2.f, getWidth()/2.f/bitmapScale);
            float minBitmapCenterY = Math.min(bitmapHeight/2.f, getHeight()/2.f/bitmapScale);

            bitmapCenterPoint.set(
                    Math.max(Math.min(bitmapCenterPoint.x, bitmapWidth-minBitmapCenterX), minBitmapCenterX),
                    Math.max(Math.min(bitmapCenterPoint.y, bitmapHeight-minBitmapCenterY), minBitmapCenterY)
            );

            draggable = (bitmapScale * bitmapWidth > getWidth()+1) || (bitmapScale*bitmapHeight > getHeight()+1);
        }

        matrix.reset();
        matrix.postTranslate(-bitmapCenterPoint.x, -bitmapCenterPoint.y);
        matrix.postScale(bitmapScale, bitmapScale);
        matrix.postTranslate(getWidth() / 2.f, getHeight() / 2.f);

        if (animate) {
            float[] oldMatrixValues = new float[9];
            float[] newMatrixValues = new float[9];
            float[] halfValues = new float[9];
            halfValues[8] = 1f;
            getImageMatrix().getValues(oldMatrixValues);
            matrix.getValues(newMatrixValues);
            final long startTime = System.currentTimeMillis();
            final long duration = 200;
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    float progress = (float) (System.currentTimeMillis() - startTime) / duration;
                    if (progress > 1) {
                        matrix.setValues(newMatrixValues);
                    } else {
                        float ratio = interpolator.getInterpolation(progress);
                        halfValues[0] = oldMatrixValues[0] + ratio * (newMatrixValues[0] - oldMatrixValues[0]);
                        halfValues[2] = oldMatrixValues[2] + ratio * (newMatrixValues[2] - oldMatrixValues[2]);
                        halfValues[4] = oldMatrixValues[4] + ratio * (newMatrixValues[4] - oldMatrixValues[4]);
                        halfValues[5] = oldMatrixValues[5] + ratio * (newMatrixValues[5] - oldMatrixValues[5]);
                        matrix.setValues(halfValues);
                        postOnAnimation(this);
                    }
                    setImageMatrix(matrix);
                }
            });
        } else {
            setImageMatrix(matrix);
        }
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (parentViewPagerUserInputController != null) {
            switch (mode) {
                case NONE:
                    parentViewPagerUserInputController.setParentViewPagerUserInputEnabled(true);
                    break;
                case DRAG:
                    parentViewPagerUserInputController.setParentViewPagerUserInputEnabled(!draggable);
                    break;
                case ZOOM:
                    autoFit = false;
                    parentViewPagerUserInputController.setParentViewPagerUserInputEnabled(false);
                    break;
            }
        }
    }



    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL: {
                setMode(Mode.NONE);
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (clicking
                        && event.getEventTime() - event.getDownTime() < CLICK_DURATION
                        && distance(dragStartPoint.x, dragStartPoint.y, event.getX(), event.getY()) < 10f) {
                    // click detected
                    if (doubleClicking) {
                        // double click detected
                        if (autoFit) {
                            autoFit = false;
                            bitmapCenterPoint.set(
                                    bitmapWidth / 2.f + (event.getX() - getWidth() / 2.f) / bitmapScale,
                                    bitmapHeight / 2.f + (event.getY() - getHeight() / 2.f) / bitmapScale
                            );
                            bitmapScale = 1;
                            recomputeMatrix(false, false, true);
                        } else {
                            recomputeMatrix(true, false, true);
                        }
                    } else {
                        // simple click --> start timer
                        if (singleTapUpCallback != null) {
                            singleTapHandler.postDelayed(singleTapUpCallback, DOUBLE_CLICK_DURATION + event.getDownTime() - event.getEventTime());
                        }
                        doubleClickFirstTimestamp = event.getDownTime();
                    }
                }
                setMode(Mode.NONE);
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                if (flingAnimation != null) {
                    flingAnimation.stopped = true;
                    flingAnimation = null;
                }
                clicking = true;
                doubleClicking = event.getEventTime() - doubleClickFirstTimestamp < DOUBLE_CLICK_DURATION
                        && distance(dragStartPoint.x, dragStartPoint.y, event.getX(), event.getY()) < 100f;
                if (doubleClicking && singleTapUpCallback != null) {
                    singleTapHandler.removeCallbacks(singleTapUpCallback);
                }

                initialBitmapCenterPoint.set(bitmapCenterPoint.x, bitmapCenterPoint.y);
                dragStartPoint.set(event.getX(), event.getY());
                setMode(Mode.DRAG);

                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                clicking = false;
                if (event.getPointerCount() > 2) {
                    setMode(Mode.NONE);
                } else if (event.getPointerCount() == 2) {
                    zoomInitialDist = distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1));

                    if (zoomInitialDist > 10f) {
                        initialBitmapScale = bitmapScale;
                        initialBitmapCenterPoint.set(bitmapCenterPoint.x, bitmapCenterPoint.y);
                        computeBitmapMiddle(zoomBitmapMiddle, event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                        setMode(Mode.ZOOM);
                    }
                }
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
                        zoomInitialDist = distance(x0, y0, x1, y1);
                        if (zoomInitialDist > 10f) {
                            initialBitmapScale = bitmapScale;
                            initialBitmapCenterPoint.set(bitmapCenterPoint.x, bitmapCenterPoint.y);
                            computeBitmapMiddle(zoomBitmapMiddle, x0, y0, x1, y1);
                            setMode(Mode.ZOOM);
                        }
                        break;
                    }
                    case 2: {
                        initialBitmapCenterPoint.set(bitmapCenterPoint.x, bitmapCenterPoint.y);

                        if (event.getActionIndex() == 0) {
                            dragStartPoint.set(event.getX(1), event.getY(1));
                        } else {
                            dragStartPoint.set(event.getX(0), event.getY(0));
                        }
                        setMode(Mode.DRAG);
                        break;
                    }
                    default: {
                        setMode(Mode.NONE);
                        break;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == Mode.DRAG) {
                    if (distance(event.getX(), event.getY(), dragStartPoint.x, dragStartPoint.y) > 10f) {
                        clicking = false;
                    }
                    bitmapCenterPoint.set(initialBitmapCenterPoint.x - (event.getX() - dragStartPoint.x)/bitmapScale, initialBitmapCenterPoint.y - (event.getY() - dragStartPoint.y)/bitmapScale);
                    recomputeMatrix(false, false, false);
                } else if (mode == Mode.ZOOM) {
                    float zoomNewDist = distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                    if (zoomNewDist > 10f) {
                        bitmapScale = initialBitmapScale * zoomNewDist / zoomInitialDist;
                        bitmapCenterPoint.set(
                                zoomBitmapMiddle.x + (initialBitmapCenterPoint.x - zoomBitmapMiddle.x) * initialBitmapScale / bitmapScale,
                                zoomBitmapMiddle.y + (initialBitmapCenterPoint.y - zoomBitmapMiddle.y) * initialBitmapScale / bitmapScale
                        );
                        recomputeMatrix(false, false, false);
                    }
                }
                break;
            }
        }
        return true;
    }

    private float distance(float x0, float y0, float x1, float y1) {
        float x = x1 - x0;
        float y = y1 - y0;
        return (float) Math.sqrt(x * x + y * y);
    }

    private void computeBitmapMiddle(PointF outputPoint, float x0, float y0, float x1, float y1) {
        float x = (x0 + x1) / 2;
        float y = (y0 + y1) / 2;
        float offsetX = x - getWidth() / 2.f;
        float offsetY = y - getHeight() / 2.f;
        outputPoint.set(bitmapCenterPoint.x + offsetX / bitmapScale, bitmapCenterPoint.y + offsetY / bitmapScale);
    }

    private class FlingAnimation implements Runnable {
        public static final double INERTIA = .1;
        public static final int STOP_THRESHOLD = 200;

        boolean stopped;
        float velocityX;
        float velocityY;
        long previousTimestamp;

        public FlingAnimation(float velocityX, float velocityY) {
            this.stopped = false;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.previousTimestamp = System.currentTimeMillis();
        }

        @Override
        public void run() {
            if (!stopped) {
                long newTimestamp = System.currentTimeMillis();
                double elapsed = (newTimestamp - previousTimestamp) / 1000.;


                bitmapCenterPoint.set((float) (bitmapCenterPoint.x - elapsed * velocityX/bitmapScale), (float) (bitmapCenterPoint.y - elapsed * velocityY/bitmapScale));
                recomputeMatrix(false, false, false);

                double ratio = Math.pow(INERTIA,  elapsed);
                velocityX *= ratio;
                velocityY *= ratio;
                if (velocityX*velocityX + velocityY*velocityY > STOP_THRESHOLD) {
                    previousTimestamp = newTimestamp;
                    postOnAnimation(this);
                }
            }
        }
    }

    public interface ParentViewPagerUserInputController {
        void setParentViewPagerUserInputEnabled(boolean enabled);
    }
}
