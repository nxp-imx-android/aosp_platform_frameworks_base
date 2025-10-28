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

package android.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.os.RemoteException;
import android.view.SurfaceControl;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * An interactive mirror of a computer control session display.
 *
 * @hide
 */
public final class InteractiveMirror implements AutoCloseable {

    /** The default value used by {@link #setInteractive(boolean)}. */
    public static final boolean DEFAULT_INTERACTIVE = false;

    private final IInteractiveMirror mMirror;
    private final SurfaceControl mMirrorSurface;

    /** @hide */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public InteractiveMirror(@NonNull IInteractiveMirror mirror,
            @NonNull SurfaceControl mirrorSurface) {
        mMirror = Objects.requireNonNull(mirror);
        mMirrorSurface = Objects.requireNonNull(mirrorSurface);
    }

    /**
     * Sets whether the user can interact with the contents of the mirror.
     *
     * @see #DEFAULT_INTERACTIVE
     */
    @RequiresNoPermission
    public void setInteractive(boolean interactive) {
        try {
            mMirror.setInteractive(interactive);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Resizes the mirror.
     */
    @RequiresNoPermission
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            mMirror.resize(width, height);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Returns the mirror surface associated with the interactive mirror. */
    @NonNull
    public SurfaceControl getMirrorSurface() {
        return mMirrorSurface;
    }

    @Override
    @RequiresNoPermission
    public void close() {
        try {
            mMirror.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
