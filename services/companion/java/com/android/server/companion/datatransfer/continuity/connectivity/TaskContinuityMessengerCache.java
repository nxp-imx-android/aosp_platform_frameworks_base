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

package com.android.server.companion.datatransfer.continuity.connectivity;

import android.annotation.NonNull;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.MultiUserResourceCache;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Maintains a cache of user-specific {@link TaskContinuityMessenger} instances, lazily
 * instantiating them as needed.
 */
public class TaskContinuityMessengerCache extends MultiUserResourceCache<TaskContinuityMessenger> {

    private final AssociationProfileManager mAssociationProfileManager;
    private final CompanionDeviceManager mCompanionDeviceManager;
    private final Executor mExecutor;

    public TaskContinuityMessengerCache(
            @NonNull CompanionDeviceManager companionDeviceManager, @NonNull Executor executor) {
        mAssociationProfileManager =
                new AssociationProfileManager(
                        Objects.requireNonNull(
                                LocalServices.getService(PackageManagerInternal.class)));
        mCompanionDeviceManager = Objects.requireNonNull(companionDeviceManager);
        mExecutor = Objects.requireNonNull(executor);
    }

    @Override
    protected TaskContinuityMessenger createResourceForUser(int userId) {
        return new TaskContinuityMessenger(userId, mCompanionDeviceManager, mExecutor,
                mAssociationProfileManager);
    }
}
