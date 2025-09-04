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

package android.app.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import java.util.List;

/**
 * Manages interactions with entities running in the Private Compute Core (PCC) sandbox, such as
 * querying for available PCC entities.
 *
 * @see PccEntity
 * @see android.R.styleable#AndroidManifestApplication_runInPccSandbox
 */
@FlaggedApi(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
@SystemService(Context.PCC_SANDBOX_SERVICE)
public final class PccSandboxManager {

    private final IPccSandboxManager mService;
    private final Context mContext;

    /**
     * Creates an instance.
     *
     * @param service An interface to the backing service.
     * @param context A {@link Context}.
     * @hide
     */
    public PccSandboxManager(IPccSandboxManager service, Context context) {
        mService = service;
        mContext = context;
    }

    /**
     * Returns a list of entities that are configured to run in the PCC sandbox.
     *
     * <p>The list is filtered based on package visibility rules. An entity will only be included
     * in the returned list if all of the following are true:
     * <ul>
     *     <li>The entity's manifest has {@code android:runInPccSandbox="true"}.
     *     <li>The entity is visible to the calling application.
     *     For example, this can be achieved by declaring the target application's package name
     *     in a {@code <queries>} tag in the calling app's {@code AndroidManifest.xml}.
     * </ul>
     *
     * @return A list of {@link PccEntity} objects representing the visible PCC entities.
     *         The list will be empty if no matching entities are found.
     */
    @NonNull
    public List<PccEntity> getPccEntities() {
        try {
            return mService.getPccEntities();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
