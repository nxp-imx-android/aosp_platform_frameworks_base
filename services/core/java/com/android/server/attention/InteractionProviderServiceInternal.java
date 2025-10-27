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
package com.android.server.attention;

import android.annotation.NonNull;
import android.os.RemoteCallbackList;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;

public class InteractionProviderServiceInternal extends InteractionProviderInternal {

    private final Object mInteractionProvidersLock = new Object();

    @GuardedBy("mInteractionProvidersLock")
    private final RemoteCallbackList<IInteractionProvider> mInteractionProviders =
            new RemoteCallbackList<>();

    @Override
    public boolean registerInteractionProvider(@NonNull IInteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProvidersLock) {
            return mInteractionProviders.register(provider);
        }
    }

    @Override
    public boolean unregisterInteractionProvider(@NonNull IInteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProvidersLock) {
            return mInteractionProviders.unregister(provider);
        }
    }
}
