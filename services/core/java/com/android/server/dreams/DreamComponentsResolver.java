/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.dreams;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;
import android.provider.Settings;
import android.util.Slog;

import com.android.server.pm.UserManagerInternal;

import java.util.ArrayList;
import java.util.List;

/** Resolves the component to use for dreaming or dozing. */
final class DreamComponentsResolver {
    private static final String TAG = "DreamComponentsResolver";

    private final Context mContext;
    private final DreamValidator mDreamValidator;
    private final AmbientDisplayConfiguration mDozeConfig;
    private final UserManagerInternal mUserManagerInternal;
    private final boolean mDreamsOnlyEnabledForDockUser;

    DreamComponentsResolver(
            @NonNull Context context,
            @NonNull DreamValidator dreamValidator,
            @NonNull AmbientDisplayConfiguration dozeConfig,
            @NonNull UserManagerInternal userManagerInternal,
            boolean dreamsOnlyEnabledForDockUser) {
        mContext = context;
        mDreamValidator = dreamValidator;
        mDozeConfig = dozeConfig;
        mUserManagerInternal = userManagerInternal;
        mDreamsOnlyEnabledForDockUser = dreamsOnlyEnabledForDockUser;
    }

    /**
     * Resolves the component to be used for the current dream state.
     *
     * @param doze whether the component is being resolved for dozing.
     * @param userId the user ID for which to resolve the component.
     * @param forceAmbientDisplayEnabled whether ambient display is forced enabled.
     * @param systemDreamComponent the system dream component, if any.
     * @return the resolved component, or null if none should be used.
     */
    @Nullable
    ComponentName resolve(
            boolean doze,
            int userId,
            boolean forceAmbientDisplayEnabled,
            @Nullable ComponentName systemDreamComponent) {
        if (doze) {
            ComponentName dozeComponent = getDozeComponent(userId, forceAmbientDisplayEnabled);
            return mDreamValidator.validate(dozeComponent, userId) ? dozeComponent : null;
        }

        if (systemDreamComponent != null) {
            return systemDreamComponent;
        }

        ComponentName[] dreams = getDreamComponentsForUser(userId);
        return dreams != null && dreams.length != 0 ? dreams[0] : null;
    }

    /**
     * Returns the user-configured dream components.
     *
     * @param userId the user ID to check.
     * @return an array of component names, or null if dreams are disabled for the user.
     */
    @Nullable
    ComponentName[] getDreamComponentsForUser(int userId) {
        if (!dreamsEnabledForUser(userId)) {
            return null;
        }

        final String names =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.SCREENSAVER_COMPONENTS,
                        userId);
        final ComponentName[] components = DreamComponentNameUtils.fromCommaSeparatedString(names);

        List<ComponentName> validComponents = new ArrayList<>();
        for (ComponentName component : components) {
            if (mDreamValidator.validate(component, userId)) {
                validComponents.add(component);
            }
        }

        if (validComponents.isEmpty()) {
            ComponentName defaultDream = getDefaultDreamComponentForUser(userId);
            if (defaultDream != null && mDreamValidator.validate(defaultDream, userId)) {
                Slog.w(TAG, "Falling back to default dream " + defaultDream);
                validComponents.add(defaultDream);
            }
        }
        return validComponents.toArray(new ComponentName[validComponents.size()]);
    }

    @Nullable
    ComponentName getDefaultDreamComponentForUser(int userId) {
        String name =
                Settings.Secure.getStringForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.SCREENSAVER_DEFAULT_COMPONENT,
                        userId);
        return name == null ? null : ComponentName.unflattenFromString(name);
    }

    @Nullable
    ComponentName getDozeComponent(int userId, boolean forceAmbientDisplayEnabled) {
        if (forceAmbientDisplayEnabled || mDozeConfig.enabled(userId)) {
            return ComponentName.unflattenFromString(mDozeConfig.ambientDisplayComponent());
        } else {
            return null;
        }
    }

    private boolean dreamsEnabledForUser(int userId) {
        if (!mDreamsOnlyEnabledForDockUser) {
            return true;
        }
        if (userId < 0) {
            return false;
        }
        final int mainUserId = mUserManagerInternal.getMainUserId();
        return userId == mainUserId;
    }
}
