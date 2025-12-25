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

package com.android.server.security.talisman;

import static android.security.talisman.TrustTokenManager.VERIFICATION_FAILURE_UNKNOWN;

import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.security.talisman.ITrustTokenManager;
import android.security.talisman.TrustToken;
import android.security.talisman.TrustTokenIdentitySet;
import android.util.Slog;

import com.android.server.SystemService;

import java.util.Arrays;
import java.util.List;

/** A service that manages trust tokens. */
public class TrustTokenManagerService extends SystemService {
    private static final String TAG = "TrustTokenManagerService";

    private final Context mContext;

    public TrustTokenManagerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting TrustTokenManagerService");
        publishBinderService(Context.TALISMAN_SERVICE, mBinder);
    }

    private final ITrustTokenManager.Stub mBinder =
            new ITrustTokenManager.Stub() {
                @RequiresNoPermission
                @Override
                public TrustToken acquireVerifiedDeviceToken() {
                    // TODO(b/418280383): Implement this method.
                    // TODO(b/418280383): Protect with permissions
                    Slog.w(TAG, "acquireVerifiedDeviceToken is not yet implemented.");
                    return null;
                }

                @RequiresNoPermission
                @Override
                public TrustTokenIdentitySet acquirePreparedIdentitySet() {
                    // TODO(b/418280383): Implement this method.
                    // TODO(b/418280383): Protect with permissions
                    Slog.w(TAG, "acquirePreparedIdentitySet is not yet implemented.");
                    return null;
                }

                @RequiresNoPermission
                @Override
                public int verifyTrustTokenAndChallenge(
                        TrustToken token, byte[] remoteResponse, byte[] expectedChallenge) {
                    // TODO(b/418280383): Implement this method.
                    // TODO(b/418280383): Protect with permissions
                    Slog.w(TAG, "verifyTrustTokenAndChallenge is not yet implemented.");
                    return VERIFICATION_FAILURE_UNKNOWN;
                }

                @RequiresNoPermission
                @Override
                public int[] verifyIdentityTokens(
                        TrustToken verifiedDeviceToken, TrustToken[] identityTokens) {
                    // TODO(b/418280383): Implement this method.
                    // TODO(b/418280383): Protect with permissions
                    Slog.w(TAG, "verifyIdentityTokens is not yet implemented.");
                    int[] results = new int[identityTokens.length];
                    Arrays.fill(results, VERIFICATION_FAILURE_UNKNOWN);
                    return results;
                }

                @RequiresNoPermission
                @Override
                public void updatePreparedIdentities(List<String> identities) {
                    // TODO(b/418280383): Implement this method.
                    // TODO(b/418280383): Protect with permissions
                    Slog.w(TAG, "updatePreparedIdentities is not yet implemented.");
                }
            };
}
