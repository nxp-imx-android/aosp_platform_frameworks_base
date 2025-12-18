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

package android.app.permissionui;

import static android.app.permissionui.LocationButtonProviderFactory.LocationButtonProviderImpl;
import static android.app.permissionui.LocationButtonProviderFactory.LocationButtonSessionRecord;

import android.os.ParcelableException;

import java.util.concurrent.Executor;

/**
 * An internal binder adapter that bridges communication from the remote location button service
 * back to the application's {@link LocationButtonClient}.
 *
 * <p>This class implements the {@link ILocationButtonClient} AIDL interface, allowing it to
 * receive IPC calls from the system service. It then translates these low-level binder callbacks
 * into the high-level, developer-facing {@link LocationButtonClient} API.
 *
 * @hide
 * @see LocationButtonClient
 */
public class LocationButtonClientWrapper extends ILocationButtonClient.Stub {
    private final LocationButtonProviderImpl mProvider;
    private final LocationButtonClient mClient;
    private final Executor mClientExecutor;

    /**
     * Constructs a wrapper that links the remote service to the application's client.
     *
     * @param provider       The provider instance that initiated the session.
     * @param client         App's implementation of the {@link LocationButtonClient} interface.
     * @param clientExecutor The executor on which client callbacks will be delivered.
     */
    LocationButtonClientWrapper(LocationButtonProviderImpl provider, LocationButtonClient client,
            Executor clientExecutor) {
        mProvider = provider;
        mClient = client;
        mClientExecutor = clientExecutor;
    }

    /**
     * Handles the binder callback for successful session creation. This method unpacks the
     * response, creates a {@link LocationButtonSessionWrapper}, and posts the
     * {@link LocationButtonClient#onSessionOpened} callback to the client's executor.
     */
    @Override
    public void onSessionOpened(LocationButtonSessionResponse response) {
        mClientExecutor.execute(() -> mClient.onSessionOpened(
                new LocationButtonSessionWrapper(mProvider, response, mClient, this)));
        mProvider.addActiveSessionRecord(mClient,
                new LocationButtonSessionRecord(response.getSession(), mClientExecutor));
    }

    /**
     * Handles the binder callback for a precise location permission result. This invokes
     * {@link LocationButtonClient#onPermissionResult} callback with the precise location
     * permission grant result on the client's executor.
     */
    @Override
    public void onPermissionsResult(boolean isPermissionGranted) {
        mClientExecutor.execute(
                () -> mClient.onPermissionResult(isPermissionGranted));
    }

    /**
     * Handles the binder callback for a terminal session error. This method unpacks the
     * {@link ParcelableException}, and posts the {@link LocationButtonClient#onSessionError}
     * callback with the underlying cause to the client's executor.
     */
    @Override
    public void onSessionError(ParcelableException e) {
        mProvider.onSessionClosed(mClient);
        mClientExecutor.execute(() -> mClient.onSessionError(e.getCause()));
    }
}
