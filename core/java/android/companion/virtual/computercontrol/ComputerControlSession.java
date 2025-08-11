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

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.os.RemoteException;
import android.view.Surface;

import java.util.Objects;

/**
 * A session for automated control of applications.
 *
 * <p>A session is associated with a single trusted virtual display, capable of hosting activities,
 * along with the input devices that allow input injection.</p>
 *
 * @hide
 */
public final class ComputerControlSession implements AutoCloseable {

    private final IComputerControlSession mSession;

    /** @hide */
    public ComputerControlSession(@NonNull IComputerControlSession session) {
        mSession = Objects.requireNonNull(session);
    }

    /** Returns the ID of the single trusted virtual display for this session. */
    public int getVirtualDisplayId() {
        try {
            return mSession.getVirtualDisplayId();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Injects a key event into the trusted virtual display. */
    public void sendKeyEvent(@NonNull VirtualKeyEvent event) {
        try {
            mSession.sendKeyEvent(Objects.requireNonNull(event));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Injects a touch event into the trusted virtual display. */
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) {
        try {
            mSession.sendTouchEvent(Objects.requireNonNull(event));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Creates an interactive virtual display, mirroring the trusted one. */
    @Nullable
    public InteractiveMirrorDisplay createInteractiveMirrorDisplay(
            @IntRange(from = 1) int width, @IntRange(from = 1) int height,
            @NonNull Surface surface) {
        Objects.requireNonNull(surface);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        try {
            IInteractiveMirrorDisplay display =
                    mSession.createInteractiveMirrorDisplay(width, height, surface);
            if (display == null) {
                return null;
            }
            return new InteractiveMirrorDisplay(display);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void close() {
        try {
            mSession.close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
