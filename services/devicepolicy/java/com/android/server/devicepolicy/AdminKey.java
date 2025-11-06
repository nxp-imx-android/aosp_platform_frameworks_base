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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Key identifying admin entity for policy accounting purposes.
 */
public class AdminKey {
    // UserId of the admin, USER_SYSTEM for system entities.
    private final @UserIdInt int mUserId;
    // Name of the package. Cannot be set together with mSystemEntity.
    private final String mPackageName;
    // Name of the system entity. Cannot be set together with mPackageName.
    private final String mSystemEntity;
    // Name of the admin component. Must only be set for legacy DA admins. Must not be set for
    // device and profile owners.
    private final ComponentName mComponent;

    private AdminKey(int userId, String packageName, String systemEntity, ComponentName component) {
        mUserId = userId;
        mPackageName = packageName;
        mSystemEntity = systemEntity;
        mComponent = component;
    }

    /**
     * Creates an admin key representing a system entity.
     */
    public static AdminKey ofSystemEntity(@NonNull String systemEntity) {
        Objects.requireNonNull(systemEntity);

        return new AdminKey(
                UserHandle.USER_SYSTEM,
                null, /* packageName */
                systemEntity,
                null /* component */
        );
    }

    /**
     * Creates an admin key representing a package. This includes DPCs and role holders, but
     * doesn't include legacy DAs.
     */
    public static AdminKey ofPackage(@UserIdInt int userId, @NonNull String packageName) {
        Objects.requireNonNull(packageName);

        return new AdminKey(
                userId,
                packageName,
                null, /* systemEntity */
                null /* component */
        );
    }

    /**
     * Creates an admin key representing a legacy device admin component.
     */
    public static AdminKey ofLegacyAdminComponent(
            @UserIdInt int userId, @NonNull ComponentName component) {
        Objects.requireNonNull(component);

        return new AdminKey(
                userId,
                component.getPackageName(),
                null, /* systemEntity */
                component);
    }

    public @UserIdInt int getUserId() {
        return mUserId;
    }

    public String getPackageName() {
        // Ideally it should return null instead of empty string but keeping the existing behavior.
        return mSystemEntity != null ? "" : mPackageName;
    }

    boolean isSystemEntity() {
        return mSystemEntity != null;
    }

    public String getSystemEntity() {
        return mSystemEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AdminKey other)) return false;
        return mUserId == other.mUserId
                && Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mSystemEntity, other.mSystemEntity)
                && Objects.equals(mComponent, other.mComponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUserId, mPackageName, mSystemEntity, mComponent);
    }
}
