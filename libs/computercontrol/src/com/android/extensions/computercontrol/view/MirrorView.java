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

package com.android.extensions.computercontrol.view;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.extensions.computercontrol.ComputerControlSession;
import com.android.extensions.computercontrol.InteractiveMirror;

/**
 * A view which allows interactive mirroring of a given {@link ComputerControlSession}.
 */
public class MirrorView extends FrameLayout {
    private MirrorHelper mMirrorHelper;
    private OnTouchListener mOnTouchListener = null;

    public MirrorView(Context context) {
        super(context);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    /**
     * Sets the {@link ComputerControlSession} to be mirrored, or {@code null} if nothing needs to
     * be shown.
     */
    public void setComputerControlSession(@Nullable ComputerControlSession computerControlSession) {
        mMirrorHelper.setComputerControlSession(computerControlSession);
    }

    /**
     * Sets whether user input can control the session being mirrored. By default, this is set to
     * {@code false}.
     * <p>
     * Note that when this is set to {@code true}, {@link #onTouchEvent(MotionEvent)} would not get
     * called, and the return value of
     * {@link android.view.View.OnTouchListener#onTouch(View, MotionEvent)} of any
     * {@link android.view.View.OnTouchListener} would be ignored. However, any
     * {@link android.view.View.OnTouchListener} set through
     * {@link #setOnTouchListener(OnTouchListener)} would still be invoked.
     */
    public void setInteractive(boolean interactive) {
        mMirrorHelper.setInteractive(interactive);
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        // Don't call super.setOnTouchListener as that would overwrite our own listener. Instead,
        // store this so it can be invoked from our own listener.
        mOnTouchListener = listener;
    }

    private void init(@NonNull Context context) {
        TextureView textureView = new TextureView(context);
        addView(textureView,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mMirrorHelper = new MirrorHelper();
        textureView.setSurfaceTextureListener(mMirrorHelper);

        super.setOnTouchListener((v, event) -> {
            boolean handled = mOnTouchListener != null && mOnTouchListener.onTouch(v, event);
            handled |= mMirrorHelper.handleTouch(event);
            return handled;
        });
    }

    private static final class MirrorHelper implements TextureView.SurfaceTextureListener {
        private final HandlerThread mHandlerThread;

        // The following members are always written on the main thread, and always read from the
        // single thread of the executor.
        private volatile ComputerControlSession mComputerControlSession = null;
        private volatile Surface mSurface = null;
        private volatile int mWidth = 0;
        private volatile int mHeight = 0;

        // This is always read and written from the single thread of the executor.
        private volatile InteractiveMirror mInteractiveMirror = null;

        private boolean mInteractive = false;

        MirrorHelper() {
            mHandlerThread = new HandlerThread("mirrorHelper");
            mHandlerThread.start();
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width,
                int height) {
            mSurface = new Surface(surfaceTexture);
            mWidth = width;
            mHeight = height;
            createOrResizeMirrorIfPossible();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width,
                int height) {
            mWidth = width;
            mHeight = height;
            createOrResizeMirrorIfPossible();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            mSurface.release();
            mSurface = null;
            destroyMirror();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}

        void setComputerControlSession(@Nullable ComputerControlSession computerControlSession) {
            if (mComputerControlSession != null) {
                destroyMirror();
            }
            mComputerControlSession = computerControlSession;
            createOrResizeMirrorIfPossible();
        }

        void setInteractive(boolean interactive) {
            mInteractive = interactive;
        }

        boolean handleTouch(@NonNull MotionEvent event) {
            if (!mInteractive) {
                return false;
            }
            if (!event.getDevice().supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                return false;
            }
            if (mInteractiveMirror == null) {
                return false;
            }
            MotionEvent copy = event.copy();
            mHandlerThread.getThreadExecutor().execute(() -> {
                mInteractiveMirror.sendTouchEvent(copy);
                copy.recycle();
            });
            return true;
        }

        private void createOrResizeMirrorIfPossible() {
            mHandlerThread.getThreadExecutor().execute(() -> {
                if (mComputerControlSession == null || mSurface == null) {
                    return;
                }

                if (mInteractiveMirror != null) {
                    // This indicates that a mirror is already there, and only needs to be resized.
                    mInteractiveMirror.resize(mWidth, mHeight);
                    return;
                }

                // Start mirroring.
                mInteractiveMirror =
                        mComputerControlSession.createInteractiveMirror(mWidth, mHeight, mSurface);
            });
        }

        private void destroyMirror() {
            mHandlerThread.getThreadExecutor().execute(() -> {
                if (mInteractiveMirror != null) {
                    // Stop mirroring.
                    mInteractiveMirror.close();
                    mInteractiveMirror = null;
                }
            });
        }
    }
}
