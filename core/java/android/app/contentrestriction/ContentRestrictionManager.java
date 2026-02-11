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

package android.app.contentrestriction;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.annotation.UserHandleAware;
import android.annotation.UserIdInt;
import android.app.contentrestriction.flags.Flags;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Service for handling content restrictions.
 */
@SystemService(Context.CONTENT_RESTRICTION_SERVICE)
@FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
public class ContentRestrictionManager {
    private final Context mContext;
    private final IContentRestrictionManager mService;

    /** @hide */
    public ContentRestrictionManager(
            @NonNull Context context,
            @NonNull IContentRestrictionManager service) {
        mContext = context;
        mService = service;
    }

    /**
     * Whether the content is allowed or should be blocked.
     *
     * <p>When content restrictions are enabled, this method determines if the specified content
     * is appropriate based on the user's settings.
     *
     * <p>If the content is allowed or the content restrictions settings are disabled, the result
     * provide to the callback will be {@code true}. If the content is blocked, the value will be
     * {@code false}.
     *
     * @param content the content to be classified
     * @param executor the executor on which to run the callback
     * @param callback the callback for whether the content is allowed
     */
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    public void requestClassification(
            @NonNull ClassifiableContent content,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull Consumer<Boolean> callback) {

        if (!isContentRestrictionEnabled()) {
            executor.execute(() -> callback.accept(true));
            return;
        }

        try {
            mService.requestClassification(mContext.getUserId(), content,
                    new IContentRestrictionCallback.Stub() {
                @Override
                public void onResult(boolean isContentAllowed) {
                    final long token = Binder.clearCallingIdentity();
                    try {
                        executor.execute(() -> callback.accept(isContentAllowed));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            });
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether content restriction is currently enabled for the user.
     */
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    public boolean isContentRestrictionEnabled() {
        if (mService != null) {
            try {
                return mService.isContentRestrictionEnabledForUser(mContext.getUserId());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return false;
    }

    /**
     * Returns whether content restriction device policy bypassing is allowed.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    @UserHandleAware(requiresPermissionIfNotCaller = INTERACT_ACROSS_USERS)
    public boolean isDevicePolicyBypassingEnabledForUser(@UserIdInt int userId) {
        if (mService != null) {
            try {
                return mService.isDevicePolicyBypassingEnabledForUser(userId);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return false;
    }

    /**
     * Sets whether content restriction device policy bypassing is allowed.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    @UserHandleAware(requiresPermissionIfNotCaller = INTERACT_ACROSS_USERS)
    public void setDevicePolicyBypassingEnabledForUser(@UserIdInt int userId, boolean enabled) {
        if (mService != null) {
            try {
                mService.setDevicePolicyBypassingEnabledForUser(userId, enabled);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }
}
