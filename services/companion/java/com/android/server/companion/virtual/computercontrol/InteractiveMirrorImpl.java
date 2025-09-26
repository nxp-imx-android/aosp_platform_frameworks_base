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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.RequiresNoPermission;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.gui.DropInputMode;
import android.view.SurfaceControl;

import java.util.function.Supplier;

/**
 * A representation of an interactive mirror of a Computer Control Session's Virtual Display.
 * <p>
 * The client application will render the {@code mMirrorSurface} from their UI.
 * <p>
 * NOTE: Since the application has access to the mirror {@link SurfaceControl}, this means they can
 * view all content in that mirror surface.
 */
final class InteractiveMirrorImpl extends IInteractiveMirror.Stub {

    private final SurfaceControl mMirrorSurface;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;

    InteractiveMirrorImpl(SurfaceControl mirrorSurface,
            Supplier<SurfaceControl.Transaction> transactionSupplier) {
        mMirrorSurface = mirrorSurface;
        mTransactionSupplier = transactionSupplier;
    }

    @RequiresNoPermission
    @Override
    public void setInteractive(boolean interactive) {
        try (var transaction = mTransactionSupplier.get()) {
            transaction.setDropInputMode(mMirrorSurface,
                    interactive ? DropInputMode.NONE : DropInputMode.ALL).apply();
        }
    }

    @RequiresNoPermission
    @Override
    public void close() {
        mMirrorSurface.release();
    }
}
