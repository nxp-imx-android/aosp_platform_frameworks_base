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

package com.android.server.companion.datatransfer.continuity.settings;

import android.annotation.NonNull;
import android.os.Bundle;
import android.os.UserManager;
import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.UserManagerInternal.UserRestrictionsListener;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Manages policy for Handoff across all users on a device. */
public class HandoffPolicyManager implements UserRestrictionsListener {

    public interface Listener {
        void onHandoffPolicyChanged(int userId);
    }

    private final UserManagerInternal mUserManagerInternal;

    @GuardedBy("mListeners")
    private final Set<Listener> mListeners = new HashSet<>();

    public HandoffPolicyManager() {
        mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
    }

    public HandoffPolicyManager(@NonNull UserManagerInternal userManagerInternal) {
        mUserManagerInternal = Objects.requireNonNull(userManagerInternal);
        mUserManagerInternal.addUserRestrictionsListener(this);
    }

    public boolean isHandoffAllowedForUser(int userId) {
        return !mUserManagerInternal.getUserRestriction(userId, UserManager.DISALLOW_HANDOFF);
    }

    public void addListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    @Override
    public void onUserRestrictionsChanged(
            int userId, Bundle newRestrictions, Bundle prevRestrictions) {

        boolean wasRestricted = prevRestrictions.getBoolean(UserManager.DISALLOW_HANDOFF, false);
        boolean isRestricted = newRestrictions.getBoolean(UserManager.DISALLOW_HANDOFF, false);

        if (wasRestricted != isRestricted) {
            for (Listener listener : mListeners) {
                listener.onHandoffPolicyChanged(userId);
            }
        }
    }
}
