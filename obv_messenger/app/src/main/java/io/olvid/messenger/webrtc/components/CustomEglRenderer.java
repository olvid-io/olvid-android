/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.webrtc.components;


import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.EglThread;
import org.webrtc.GlTextureFrameBuffer;
import org.webrtc.GlUtil;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoFrameDrawer;
import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;

/**
 * This class is an adaptation of the EglRenderer bundled in webrtc.
 * Compared to the original one, it can handle pan and zoom through the ScaleAndOffsetControl
 */
public class CustomEglRenderer implements VideoSink, ScaleAndOffsetControl.ScaleAndOffsetControlListener {
    private class EglSurfaceCreation implements Runnable {
        private Object surface;

        public synchronized void setSurface(Object surface) {
            this.surface = surface;
        }

        @Override
        public synchronized void run() {
            if (surface != null && eglBase != null && !eglBase.hasSurface()) {
                if (surface instanceof Surface) {
                    eglBase.createSurface((Surface) surface);
                } else if (surface instanceof SurfaceTexture) {
                    eglBase.createSurface((SurfaceTexture) surface);
                } else {
                    throw new IllegalStateException("Invalid surface: " + surface);
                }
                eglBase.makeCurrent();
                // Necessary for YUV frames with odd width.
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            }
        }
    }

    protected final String name;

    // `eglThread` is used for rendering, and is synchronized on `threadLock`.
    private final Object threadLock = new Object();
    @GuardedBy("threadLock") @Nullable private EglThread eglThread;

    private final Runnable eglExceptionCallback = new Runnable() {
        @Override
        public void run() {
            synchronized (threadLock) {
                eglThread = null;
            }
        }
    };


    // EGL and GL resources for drawing YUV/OES textures. After initialization, these are only
    // accessed from the render thread.
    @Nullable private EglBase eglBase;
    private final VideoFrameDrawer frameDrawer;
    @Nullable private RendererCommon.GlDrawer drawer;
    private boolean usePresentationTimeStamp;
    private final Matrix drawMatrix = new Matrix();

    // Pending frame to render. Serves as a queue with size 1. Synchronized on `frameLock`.
    private final Object frameLock = new Object();
    @Nullable private VideoFrame pendingFrame;

    // These variables are synchronized on `layoutLock`.
    private final Object layoutLock = new Object();
    private float layoutAspectRatio;
    // If true, mirrors the video stream horizontally.
    private boolean mirrorHorizontally;
    // If true, mirrors the video stream vertically.
    private boolean mirrorVertically;


    // Used for bitmap capturing.
    private final GlTextureFrameBuffer bitmapTextureFramebuffer =
            new GlTextureFrameBuffer(GLES20.GL_RGBA);

    private final EglSurfaceCreation eglSurfaceCreationRunnable = new EglSurfaceCreation();

    /**
     * Standard constructor. The name will be included when logging. In order to render something,
     * you must first call init() and createEglSurface.
     */
    public CustomEglRenderer(String name, @Nullable ScaleAndOffsetControl scaleAndOffsetControl) {
        this.name = name;
        this.frameDrawer = new VideoFrameDrawer();
        if (scaleAndOffsetControl != null) {
            scaleAndOffsetControl.setListener(this);
        }
    }

    public void init(
            EglThread eglThread, RendererCommon.GlDrawer drawer, boolean usePresentationTimeStamp) {
        synchronized (threadLock) {
            if (this.eglThread != null) {
                throw new IllegalStateException(name + "Already initialized");
            }

            this.eglThread = eglThread;
            this.drawer = drawer;
            this.usePresentationTimeStamp = usePresentationTimeStamp;

            eglThread.addExceptionCallback(eglExceptionCallback);

            eglBase = eglThread.createEglBaseWithSharedConnection();
            eglThread.getHandler().post(eglSurfaceCreationRunnable);
        }
    }

    /**
     * Initialize this class, sharing resources with `sharedContext`. The custom `drawer` will be used
     * for drawing frames on the EGLSurface. This class is responsible for calling release() on
     * `drawer`. It is allowed to call init() to reinitialize the renderer after a previous
     * init()/release() cycle. If usePresentationTimeStamp is true, eglPresentationTimeANDROID will be
     * set with the frame timestamps, which specifies desired presentation time and might be useful
     * for e.g. syncing audio and video.
     */
    public void init(@Nullable final EglBase.Context sharedContext, final int[] configAttributes,
                     RendererCommon.GlDrawer drawer, boolean usePresentationTimeStamp) {
        EglThread thread =
                EglThread.create(/* releaseMonitor= */ null, sharedContext, configAttributes);
        init(thread, drawer, usePresentationTimeStamp);
    }

    /**
     * Same as above with usePresentationTimeStamp set to false.
     *
     * @see #init(EglBase.Context, int[], RendererCommon.GlDrawer, boolean)
     */
    public void init(@Nullable final EglBase.Context sharedContext, final int[] configAttributes,
                     RendererCommon.GlDrawer drawer) {
        init(sharedContext, configAttributes, drawer, /* usePresentationTimeStamp= */ false);
    }

    public void createEglSurface(Surface surface) {
        createEglSurfaceInternal(surface);
    }

    public void createEglSurface(SurfaceTexture surfaceTexture) {
        createEglSurfaceInternal(surfaceTexture);
    }

    private void createEglSurfaceInternal(Object surface) {
        eglSurfaceCreationRunnable.setSurface(surface);
        postToRenderThread(eglSurfaceCreationRunnable);
    }

    /**
     * Block until any pending frame is returned and all GL resources released, even if an interrupt
     * occurs. If an interrupt occurs during release(), the interrupt flag will be set. This function
     * should be called before the Activity is destroyed and the EGLContext is still valid. If you
     * don't call this function, the GL resources might leak.
     */
    public void release() {
        final CountDownLatch eglCleanupBarrier = new CountDownLatch(1);
        synchronized (threadLock) {
            if (eglThread == null) {
                return;
            }
            eglThread.removeExceptionCallback(eglExceptionCallback);

            // Release EGL and GL resources on render thread.
            eglThread.getHandler().postAtFrontOfQueue(() -> {
                // Detach current shader program.
                synchronized (EglBase.lock) {
                    GLES20.glUseProgram(/* program= */ 0);
                }
                if (drawer != null) {
                    drawer.release();
                    drawer = null;
                }
                frameDrawer.release();
                bitmapTextureFramebuffer.release();

                if (eglBase != null) {
                    eglBase.detachCurrent();
                    eglBase.release();
                    eglBase = null;
                }

                eglCleanupBarrier.countDown();
            });

            // Don't accept any more frames or messages to the render thread.
            eglThread.release();
            eglThread = null;
        }
        // Make sure the EGL/GL cleanup posted above is executed.
        ThreadUtils.awaitUninterruptibly(eglCleanupBarrier);
        synchronized (frameLock) {
            if (pendingFrame != null) {
                pendingFrame.release();
                pendingFrame = null;
            }
        }
    }


    /**
     * Set if the video stream should be mirrored horizontally or not.
     */
    public void setMirror(final boolean mirror) {
        synchronized (layoutLock) {
            this.mirrorHorizontally = mirror;
        }
    }

    /**
     * Set if the video stream should be mirrored vertically or not.
     */
    public void setMirrorVertically(final boolean mirrorVertically) {
        synchronized (layoutLock) {
            this.mirrorVertically = mirrorVertically;
        }
    }

    /**
     * Set layout aspect ratio. This is used to crop frames when rendering to avoid stretched video.
     * Set this to 0 to disable cropping.
     */
    public void setLayoutAspectRatio(float layoutAspectRatio) {
        synchronized (layoutLock) {
            this.layoutAspectRatio = layoutAspectRatio;
        }
    }



    // VideoSink interface.
    @Override
    public void onFrame(VideoFrame frame) {
        synchronized (threadLock) {
            if (eglThread == null) {
                return;
            }
            synchronized (frameLock) {
                if (pendingFrame != null) {
                    pendingFrame.release();
                }
                pendingFrame = frame;
                pendingFrame.retain();
                eglThread.getHandler().post(this::renderFrameOnRenderThread);
            }
        }
    }

    /**
     * Release EGL surface. This function will block until the EGL surface is released.
     */
    public void releaseEglSurface(final Runnable completionCallback) {
        // Ensure that the render thread is no longer touching the Surface before returning from this
        // function.
        eglSurfaceCreationRunnable.setSurface(null /* surface */);
        synchronized (threadLock) {
            if (eglThread != null) {
                eglThread.getHandler().removeCallbacks(eglSurfaceCreationRunnable);
                eglThread.getHandler().postAtFrontOfQueue(() -> {
                    if (eglBase != null) {
                        eglBase.detachCurrent();
                        eglBase.releaseSurface();
                    }
                    completionCallback.run();
                });
                return;
            }
        }
        completionCallback.run();
    }

    /**
     * Private helper function to post tasks safely.
     */
    private void postToRenderThread(Runnable runnable) {
        synchronized (threadLock) {
            if (eglThread != null) {
                eglThread.getHandler().post(runnable);
            }
        }
    }

    private void clearSurfaceOnRenderThread(float r, float g, float b, float a) {
        if (eglBase != null && eglBase.hasSurface()) {
            eglBase.makeCurrent();
            GLES20.glClearColor(r, g, b, a);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            eglBase.swapBuffers();
        }
    }

    /**
     * Post a task to clear the surface to a transparent uniform color.
     */
    public void clearImage() {
        clearImage(0 /* red */, 0 /* green */, 0 /* blue */, 0 /* alpha */);
    }

    /**
     * Post a task to clear the surface to a specific color.
     */
    public void clearImage(final float r, final float g, final float b, final float a) {
        synchronized (threadLock) {
            if (eglThread == null) {
                return;
            }
            eglThread.getHandler().postAtFrontOfQueue(() -> clearSurfaceOnRenderThread(r, g, b, a));
        }
    }

    private void swapBuffersOnRenderThread(final VideoFrame frame) {
        synchronized (threadLock) {
            if (eglThread != null) {
                eglThread.scheduleRenderUpdate(
                        runsInline -> {
                            if (!runsInline) {
                                if (eglBase == null || !eglBase.hasSurface()) {
                                    return;
                                }
                                eglBase.makeCurrent();
                            }

                            if (usePresentationTimeStamp) {
                                eglBase.swapBuffers(frame.getTimestampNs());
                            } else {
                                eglBase.swapBuffers();
                            }
                        });
            }
        }
    }

    /**
     * Renders and releases `pendingFrame`.
     */
    private void renderFrameOnRenderThread() {
        // Fetch and render `pendingFrame`.
        final VideoFrame frame;
        synchronized (frameLock) {
            if (pendingFrame == null) {
                return;
            }
            frame = pendingFrame;
            pendingFrame = null;
        }
        if (eglBase == null || !eglBase.hasSurface()) {
            frame.release();
            return;
        }
        eglBase.makeCurrent();

        final float frameAspectRatio = frame.getRotatedWidth() / (float) frame.getRotatedHeight();
        final float offsetRatioX;
        final float offsetRatioY;
        final float scaleX;
        final float scaleY;
        final int gapX;
        final int gapY;
        synchronized (layoutLock) {
            if (lastFrameRatio - frameAspectRatio > 0.05
                    || frameAspectRatio - lastFrameRatio > 0.05
                    || lastEglWidth != eglBase.surfaceWidth()
                    || lastEglHeight != eglBase.surfaceHeight()) {
                lastFrameRatio = frameAspectRatio;
                lastEglWidth = eglBase.surfaceWidth();
                lastEglHeight = eglBase.surfaceHeight();
                coerceZoomAndOffset();
            }
            offsetRatioX = this.offsetX/lastEglWidth;
            offsetRatioY = this.offsetY/lastEglHeight;
            scaleX = this.scaleX;
            scaleY = this.scaleY;
            gapX = (int) (this.gapRatioX*lastEglWidth);
            gapY = (int) (this.gapRatioY*lastEglHeight);
        }

        drawMatrix.reset();
        drawMatrix.preTranslate(0.5f, 0.5f);
        drawMatrix.preScale(mirrorHorizontally ? -1f : 1f, mirrorVertically ? -1f : 1f);
        drawMatrix.preScale(scaleX, scaleY);
        drawMatrix.preTranslate(-0.5f - offsetRatioX, -0.5f + offsetRatioY);


        try {
            GLES20.glClearColor(0 /* red */, 0 /* green */, 0 /* blue */, 0 /* alpha */);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            frameDrawer.drawFrame(frame, drawer, drawMatrix, gapX, gapY, lastEglWidth - 2*gapX, lastEglHeight - 2*gapY);

            swapBuffersOnRenderThread(frame);
        } catch (GlUtil.GlOutOfMemoryException e) {
            // Attempt to free up some resources.
            drawer.release();
            frameDrawer.release();
            bitmapTextureFramebuffer.release();
            // Continue here on purpose and retry again for next frame. In worst case, this is a
            // continuous problem and no more frames will be drawn.
        } finally {
            frame.release();
        }
    }

    float lastFrameRatio = 1f;
    int lastEglWidth = 0;
    int lastEglHeight = 0;

    /**
     * Set to true to fit the whole image, false to fill, null for custom zoom and offset
     */
    Boolean fit = false;

    /**
     * 1 is the fill zoom
     */
    float zoom = 1;

    /**
     * Offsets are in pixels, and are converted to frame percent during frame render matrix computation
     */
    float offsetX = 0;
    float offsetY = 0;

    float gapRatioX = 0f;
    float gapRatioY = 0f;

    float scaleX = 1f;
    float scaleY = 1f;

    @Override
    public void onFit() {
        synchronized (layoutLock) {
            fit = true;
            zoom = (layoutAspectRatio > lastFrameRatio) ? lastFrameRatio / layoutAspectRatio : layoutAspectRatio / lastFrameRatio;
            offsetX = 0;
            offsetY = 0;
            coerceZoomAndOffset();
        }
    }

    @Override
    public void onFill() {
        synchronized (layoutLock) {
            fit = false;
            zoom = 1;
            offsetX = 0;
            offsetY = 0;
            coerceZoomAndOffset();
        }
    }

    @Override
    public void onTransformation(float zoomChange, float offsetChangeX, float offsetChangeY) {
        synchronized (layoutLock) {
            fit = null;
            zoom *= zoomChange;
            offsetX += offsetChangeX;
            offsetY += offsetChangeY;
            coerceZoomAndOffset();
        }
    }

    private void coerceZoomAndOffset() {
        synchronized (layoutLock) {
            if (fit != null) {
                if (fit) {
                    zoom = (layoutAspectRatio > lastFrameRatio) ? lastFrameRatio / layoutAspectRatio : layoutAspectRatio / lastFrameRatio;
                    scaleX = layoutAspectRatio / lastFrameRatio / zoom;
                    scaleY = Math.min(1f/zoom, 1);
                } else {
                    zoom = 1;
                    offsetX = 0;
                    offsetY = 0;
                }
            }

            float minZoom = (layoutAspectRatio > lastFrameRatio)  ? lastFrameRatio / layoutAspectRatio : layoutAspectRatio / lastFrameRatio;
            if (zoom < minZoom) { zoom = minZoom; }
            if (zoom > 3f) { zoom = 3f; }

            if (lastFrameRatio > layoutAspectRatio) {
                scaleX = layoutAspectRatio / lastFrameRatio / zoom;
                scaleY = Math.min(1f/zoom, 1);
                gapRatioX = 0;
                gapRatioY = Math.max(0, .5f-.5f*zoom);
                float maxOffsetX = (zoom/minZoom - 1)*lastEglWidth/2;
                if (offsetX > maxOffsetX) { offsetX = maxOffsetX; }
                if (offsetX < -maxOffsetX) { offsetX = -maxOffsetX; }
                float maxOffsetY = Math.max(0, (zoom -1)*lastEglHeight/2);
                if (offsetY > maxOffsetY) { offsetY = maxOffsetY; }
                if (offsetY < -maxOffsetY) { offsetY = -maxOffsetY; }
            } else {
                scaleX = Math.min(1f/zoom, 1);
                scaleY = lastFrameRatio / layoutAspectRatio / zoom;
                gapRatioX = Math.max(0, .5f-.5f*zoom);
                gapRatioY = 0;
                float maxOffsetX = Math.max(0, (zoom -1)*lastEglWidth/2);
                if (offsetX > maxOffsetX) { offsetX = maxOffsetX; }
                if (offsetX < -maxOffsetX) { offsetX = -maxOffsetX; }
                float maxOffsetY = (zoom/minZoom - 1)*lastEglHeight/2;
                if (offsetY > maxOffsetY) { offsetY = maxOffsetY; }
                if (offsetY < -maxOffsetY) { offsetY = -maxOffsetY; }
            }
        }
    }
}
