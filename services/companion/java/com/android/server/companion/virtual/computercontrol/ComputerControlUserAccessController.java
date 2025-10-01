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

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;

import java.util.Map;
import java.util.Set;

/** Provides constraints for which users are allowed in a computer control session. */
final class ComputerControlUserAccessController {
    private final UserManager mUserManager;

    ComputerControlUserAccessController(Context context) {
        mUserManager = requireNonNull(context.getSystemService(UserManager.class));
    }

    /**
     * Returns the caller and its clones, that should also be allowed in its session.
     *
     * @throws SecurityException if the caller is not allowed because it's a managed profile.
     */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    Set<UserHandle> validateAndGetAllowedUsers(AttributionSource attributionSource) {
        if (!Flags.computerControlUserRestriction()) {
            return emptySet();
        }
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            UserHandle root = getRoot(UserHandle.getUserHandleForUid(attributionSource.getUid()));
            // TODO: b/445856399 - Support managed profiles.
            if (isManagedProfile(root)) {
                throw new SecurityException(
                        "Managed profiles not allowed to use Computer Control.");
            }
            // Building a map from parent to its clone children (graph).
            ArrayMap<UserHandle, ArraySet<UserHandle>> parentToCloneChildren = new ArrayMap<>();
            for (UserHandle other : mUserManager.getAllProfiles()) {
                if (isCloneProfile(other)) {
                    UserHandle parent = mUserManager.getProfileParent(other);
                    if (parent != null) {
                        addChild(parentToCloneChildren, parent, other);
                    }
                }
            }
            // Returning descendants of the caller.
            return buildDescendantsSet(parentToCloneChildren, root, new ArraySet<>());
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /** If this is a clone with a parent, returns that (recursively), otherwise return user. */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    private UserHandle getRoot(UserHandle user) {
        if (isCloneProfile(user)) {
            UserHandle parent = mUserManager.getProfileParent(user);
            if (parent != null) {
                return getRoot(parent);
            }
        }
        return user;
    }

    /**
     * Traverses the graph that maps parent-to-children and adds all descendants of parent to out.
     */
    private static <T> Set<T> buildDescendantsSet(Map<T, ArraySet<T>> graph, T parent, Set<T> out) {
        out.add(parent);
        Set<T> children = graph.get(parent);
        if (children == null) {
            return out;
        }
        for (T child : graph.get(parent)) {
            if (!out.contains(child)) {
                buildDescendantsSet(graph, child, out);
            }
        }
        return out;
    }

    /** Adds child to the last of parent-to-children map, ensuring the parent is present. */
    private static <T> void addChild(Map<T, ArraySet<T>> graph, T parent, T child) {
        ArraySet<T> children = graph.get(parent);
        if (children == null) {
            children = new ArraySet<>();
            graph.put(parent, children);
        }
        children.add(child);
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private boolean isManagedProfile(UserHandle user) {
        // Not using UserManager.isManagedProfile(user) because it's inconsistent with
        // isCloneProfile (doesn't accept user), which is harder to test.
        return UserManager.isUserTypeManagedProfile(
                mUserManager.getUserInfo(user.getIdentifier()).userType);
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private boolean isCloneProfile(UserHandle user) {
        // No UserManager.isCloneProfile(user), requires creating a new context for each user and a
        // new UserManager, which is harder to test.
        return UserManager.isUserTypeCloneProfile(
                mUserManager.getUserInfo(user.getIdentifier()).userType);
    }
}
