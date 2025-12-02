/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.test.ipcrendering;
import static android.graphics.Paint.Style.FILL;
import android.app.Activity;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.view.animation.LinearInterpolator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.text.TextPaint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Paint;
import android.util.Slog;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.R;

public class IPCRenderingTest extends Activity implements SurfaceHolder.Callback{
    private static final String TAG = "IPCRecordingCanvasTest";
    SurfaceView mSurfaceView;

    IpcCanvasContext mCanvasContext;

    private class IpcCanvasContext {
        private final RenderNode mRenderNode;
        private HardwareRenderer mHardwareRenderer;
        private RecordingCanvas mCanvas;
        private int mWidth;
        private int mHeight;

        IpcCanvasContext(SurfaceControl sc) {
            mRenderNode = RenderNode.create("HwuiCanvas", null);
            mRenderNode.setPosition(0, 0, 512, 512); // Explicitly set size
            mHardwareRenderer = new HardwareRenderer(true);
            mHardwareRenderer.setContentRoot(mRenderNode);
            mHardwareRenderer.setSurfaceControl(sc, null);
            mWidth = mHeight = 512;
        }

        void destroy() {
            mHardwareRenderer.destroy();
        }

        Canvas lockCanvas(int width, int height) {
            if (mCanvas != null) {
                throw new IllegalStateException("Surface was already locked!");
            }
            if (mWidth != width || mHeight != height) {
                mWidth = width;
                mHeight = height;
            }
            mCanvas = mRenderNode.beginRecording(width, height);
            return mCanvas;
        }

        void unlockAndPost(Canvas canvas) {
            if (canvas != mCanvas) {
                throw new IllegalArgumentException("canvas object must be the same instance that "
                        + "was previously returned by lockCanvas");
            }
            mRenderNode.endRecording();
            mCanvas = null;
            mHardwareRenderer.createRenderRequest()
                    .setVsyncTime(System.nanoTime())
                    .syncAndDraw();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setFitsSystemWindows(true);
        super.onCreate(savedInstanceState);
        mSurfaceView = new SurfaceView(this);
        content.addView(mSurfaceView, new LinearLayout.LayoutParams(
                500, 500, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        setContentView(content);
        mSurfaceView.setZOrderOnTop(true);
        mSurfaceView.getHolder().addCallback(this);
    }

    private void testRendering(Canvas c) {
        Paint redPaint = new Paint();
        redPaint.setColor(Color.RED);
        c.drawRect(0, 0, 512, 512, redPaint);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCanvasContext = new IpcCanvasContext(mSurfaceView.getSurfaceControl());
        Canvas c = mCanvasContext.lockCanvas(512, 512);
        testRendering(c);
        mCanvasContext.unlockAndPost(c);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
