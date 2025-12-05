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

package com.android.server.companion.devicetrust;

import android.content.Context;

import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.transport.CompanionTransportManager;

/**
 * Manages trusted devices for Companion Device Manager.
 *
 * <p>This class is responsible for storing and retrieving session keys for trusted devices,
 * and handling trusted devices verification requests.
 */
public class TrustedDevicesManager {
    private static final String TAG = "CDM_TrustedDevicesManager";

    private final Context mContext;
    private final AssociationStore mAssociationStore;
    private final TrustedDevicesStore mTrustedDevicesStore;
    private final CompanionTransportManager mTransportManager;

    public TrustedDevicesManager(Context context,
            AssociationStore associationStore,
            TrustedDevicesStore trustedDevicesStore,
            CompanionTransportManager transportManager) {
        mContext = context;
        mAssociationStore = associationStore;
        mTrustedDevicesStore = trustedDevicesStore;
        mTransportManager = transportManager;
    }

    // TODO: Implement trusted device management
}
