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

package android.telecom;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.flags.Flags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.telecom.ITelecomLoader;
import com.android.internal.telecom.ITelecomService;

/**
 * This class handles wrapping the IBinder from the APEX provided TelecomService into an
 * API that is usable in the platform.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
@FlaggedApi(Flags.FLAG_TELECOM_MAINLINE_API)
public abstract class TelecomLoaderServiceConnection implements ServiceConnection {

    /**
     * The Connection to the TelecomService implementation.
     */
    public interface TelecomLoader {
        /**
         * Creates the TelecomService and returns the resulting IBinder containing the
         * implementation.
         * The resulting IBinder should be registered as the {@link Context#TELECOM_SERVICE}.
         *
         * @param sysUiPackageName The package name of the application handling the System UI
         * @return The IBinder containing the implementation of the TelecomService or null if there
         * was an error creating the service.
         */
        @Nullable
        IBinder createTelecomService(@NonNull String sysUiPackageName);
    }

    private record LoaderConnection(ITelecomLoader mLoader) implements TelecomLoader {
        @Override
        public @Nullable IBinder createTelecomService(@NonNull String sysUiPackageName) {
            try {
                ITelecomService service = mLoader.createTelecomService(sysUiPackageName);
                if (service == null) {
                    Log.w(this, "onCreateTelecomService: Couldn't create TelecomService");
                    return null;
                }
                return service.asBinder();
            } catch (RemoteException e) {
                Log.w(this, "onCreateTelecomService Exception: " + e);
                return null;
            }
        }
    }

    @Override
    public final void onServiceConnected(@Nullable ComponentName name, @Nullable IBinder service) {
        if (service == null) {
            Log.w(this, "onServiceConnected: null service IBinder");
            return;
        }
        ITelecomLoader loader = ITelecomLoader.Stub.asInterface(service);
        onConnected(new LoaderConnection(loader));
    }

    @Override
    public final void onServiceDisconnected(@Nullable ComponentName name) {
        onDisconnected();
    }

    /**
     * The framework has connected to the APEX provided TelecomService.
     * @param connection The connection to be used to communicate with the TelecomService
     */
    public abstract void onConnected(@NonNull TelecomLoader connection);

    /**
     * The framework has disconnected from the APEX provided TelecomService.
     */
    public void onDisconnected() { }
}
