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

package com.android.server.personalcontext.component.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;

import com.android.server.personalcontext.component.Component;

import java.util.UUID;

/**
 * Base class for component service clients.
 *
 * @hide
 */
public class BaseServiceClientComponent implements Component {
    private final Context mContext;
    private final UUID mComponentId;
    private final ServiceInfo mServiceInfo;
    private final ComponentName mComponentName;

    public BaseServiceClientComponent(Context context, UUID componentId, ServiceInfo serviceInfo) {
        mContext = context;
        mComponentId = componentId;
        mServiceInfo = serviceInfo;
        mComponentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Override
    public String toString() {
        return String.format(
                "%s - %s -> %s",
                mComponentId,
                getClass().getSimpleName(),
                mComponentName.flattenToShortString());
    }
}
