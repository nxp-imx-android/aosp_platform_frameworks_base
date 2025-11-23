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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.app.Service;
import android.app.contentrestriction.flags.Flags;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Slog;

/**
 * Base class for a service that the
 * {@code android.app.role.RoleManager.ROLE_CONTENT_RESTRICTION} role holder must implement.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
public class ContentRestrictionAppService extends Service {
    private static final String TAG = "ContentRestrictionAppService";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * Service action: Action for a service that the {@code
     * android.app.role.RoleManager.ROLE_CONTENT_RESTRICTION} role holder must implement.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.SERVICE_ACTION)
    public static final String ACTION_BIND_CONTENT_RESTRICTION_APP_SERVICE =
            "android.app.action.BIND_CONTENT_RESTRICTION_APP_SERVICE";

    private final IContentRestrictionAppService mBinder = new IContentRestrictionAppService.Stub() {
        @Override
        public void onContentRestrictionEnabled(boolean enabled) {
            mHandler.post(() -> {
                if (enabled) {
                    ContentRestrictionAppService.this.onContentRestrictionEnabled();
                } else {
                    ContentRestrictionAppService.this.onContentRestrictionDisabled();
                }
            });
        }

        @Override
        public void onClassifyContent(Content content, IContentRestrictionCallback callback) {
            try {
                ContentClassificationResult result =
                        ContentRestrictionAppService.this.onClassifyContent(content);
                if (result == null) {
                    callback.onResult(false);
                } else {
                    callback.onResult(result.isAllowed());
                }
            } catch (Exception e) {
                Slog.e(TAG, "Implementation of onClassifyContent crashed.", e);
                try {
                    callback.onResult(false);
                } catch (RemoteException re) {
                    Slog.e(TAG, "System service died", re);
                }
            }
        }
    };

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        return mBinder.asBinder();
    }

    /**
     * Called when content restriction is enabled.
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    public void onContentRestrictionEnabled() {}

    /**
     * Called when content restriction is disabled.
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    public void onContentRestrictionDisabled() {}

    /**
     * Called when content needs to be classified.
     *
     * <p>This is called on the background thread.
     *
     * @param content the content to be classified
     * @return the content classification result
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
    public ContentClassificationResult onClassifyContent(@NonNull Content content) {
        return null;
    }
}
