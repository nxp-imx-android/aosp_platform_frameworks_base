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

import static android.security.talisman.TrustTokenManager.VERIFICATION_FAILURE_CHALLENGE_INCORRECT;
import static android.security.talisman.TrustTokenManager.VERIFICATION_FAILURE_SIGNATURE_INVALID;
import static android.security.talisman.TrustTokenManager.VERIFICATION_FAILURE_UNKNOWN;
import static android.security.talisman.TrustTokenManager.VERIFICATION_SUCCESS;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.os.Binder;
import android.os.PermissionEnforcer;
import android.security.talisman.ITrustTokenManager;
import android.security.talisman.TrustConfiguration;
import android.security.talisman.TrustToken;
import android.security.talisman.TrustTokenIdentitySet;
import android.security.talisman.TrustTokenWithChallenge;
import android.util.Base64;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.Clock;
import com.android.server.SystemService;

import com.google.android.security.trusttoken.TrustAnchor;
import com.google.android.security.trusttoken.TrustTokenInvalidSignatureException;
import com.google.android.security.trusttoken.TrustTokenPolicy;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** A service that manages trust tokens. */
public class TrustTokenManagerService extends SystemService {
    private static final String TAG = "TrustTokenManagerService";
    private static final String DATABASE_NAME = "trust_token";
    private static final String MASTER_KEY_PREFIX = "trust_token_";

    @VisibleForTesting
    static final long SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS = Duration.ofDays(183).toMillis();

    private final Context mContext;
    private final TrustTokenDatabase mDatabase;
    private final Clock mClock;
    private final TrustTokenRefreshService.Scheduler mRefreshScheduler;
    private final TrustTokenMasterKey mMasterKey;
    private final AtomicReference<TrustAnchor> mTrustAnchor = new AtomicReference<>();
    private final Stub mBinder;
    private final boolean mHasProvider;

    /**
     * Creates a new instance of {@link TrustTokenManagerService}.
     *
     * @param context The {@link Context} of the service.
     * @return A new instance of {@link TrustTokenManagerService}.
     */
    public static TrustTokenManagerService create(Context context) {
        var masterKey = TrustTokenMasterKey.fromKeyStore(MASTER_KEY_PREFIX);
        if (masterKey == null) {
            masterKey = TrustTokenMasterKey.generateMasterKey(MASTER_KEY_PREFIX);
        }
        var database =
                TrustTokenSqliteDatabase.create(
                        context, context.getDatabasePath(DATABASE_NAME), Clock.SYSTEM_CLOCK);
        return new TrustTokenManagerService(context, masterKey, database, Clock.SYSTEM_CLOCK);
    }

    TrustTokenManagerService(
            Context context,
            TrustTokenMasterKey masterKey,
            TrustTokenDatabase database,
            Clock clock) {
        super(context);
        mDatabase = database;
        mMasterKey = masterKey;
        mClock = clock;
        mContext = context;
        mBinder = new Stub(context);
        mRefreshScheduler = new TrustTokenRefreshService.Scheduler(context);
        mHasProvider = TrustTokenProvider.getServiceProvider(context) != null;

        try {
            updateTrustAnchor();
        } catch (TrustConfigurationUnavailableException | IllegalArgumentException e) {
            // It's intended to be able to create the service when the TrustAnchor is not available.
            // This can happen on freshly installed system. We need to the service in order to
            // update the trust configuration.
            Slog.w(TAG, "failed to update trust anchor upon creation, this may be intended: ", e);
        }
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting TrustTokenManagerService");
        publishBinderService(Context.TRUST_TOKEN_SERVICE, mBinder);
        publishLocalService(TrustTokenManagerInternal.class, mInternal);
        if (mHasProvider) {
            mRefreshScheduler.scheduleRegularRefresh();
        }
    }

    private void updateTrustAnchor() throws TrustConfigurationUnavailableException {
        TrustConfiguration configuration = mDatabase.getTrustConfiguration();
        // Convert to ByteBuffer so that they are compared by the content.
        List<ByteBuffer> rootKeys = new ArrayList<>(configuration.getRootKeys().size());
        for (byte[] key : configuration.getRootKeys()) {
            rootKeys.add(ByteBuffer.wrap(key));
        }
        if (isOnDeviceKeyUpToDate(configuration)) {
            // Use the root keys that are both in the configuration and in the firmware, if the
            // firmware is up-to-date.
            String[] deviceRootKeys =
                    mContext.getResources()
                            .getStringArray(R.array.vendor_required_trust_token_roots);
            if (deviceRootKeys.length > 0) {
                var deviceRootKeySet = new HashSet<>(deviceRootKeys.length);
                for (String key : deviceRootKeys) {
                    deviceRootKeySet.add(ByteBuffer.wrap(Base64.decode(key, Base64.DEFAULT)));
                }
                rootKeys.retainAll(deviceRootKeySet);
            }
            Slog.i(TAG, "The number of root keys: " + rootKeys.size());
            if (rootKeys.isEmpty()) {
                Slog.e(
                        TAG,
                        "No common root keys between the device and the configuration."
                                + " TrustTokenService won't function!");
            }
        }

        var newAnchor =
                new TrustAnchor(
                        rootKeys.stream().map(key -> key.array()).collect(Collectors.toList()),
                        configuration.getIntermediateCertificates());
        TrustAnchor oldAnchor = mTrustAnchor.getAndSet(newAnchor);
        if (oldAnchor != null) {
            oldAnchor.close();
        }
    }

    @NonNull
    private TrustAnchor getTrustAnchor() throws TrustConfigurationUnavailableException {
        TrustAnchor anchor = mTrustAnchor.get();
        if (anchor == null) {
            throw new TrustConfigurationUnavailableException();
        }
        return anchor;
    }

    private boolean isOnDeviceKeyUpToDate(TrustConfiguration configuration) {
        int keyTimestamp =
                mContext.getResources()
                                .getInteger(R.integer.vendor_required_trust_token_roots_timestamp)
                        * 1000;
        return Math.min(configuration.getUpdatedAt().toEpochMilli(), mClock.currentTimeMillis())
                        - keyTimestamp
                <= SYSTEM_UP_TO_DATE_THRESHOLD_MILLIS;
    }

    @VisibleForTesting
    Binder getBinder() {
        return mBinder;
    }

    @VisibleForTesting
    TrustTokenManagerInternal getInternal() {
        return mInternal;
    }

    private final class Stub extends ITrustTokenManager.Stub {
        Stub(Context ctx) {
            super(PermissionEnforcer.fromContext(ctx));
        }

        @Override
        @EnforcePermission(
                allOf = {
                    android.Manifest.permission.ACQUIRE_VERIFIED_DEVICE_TOKEN,
                    android.Manifest.permission.SIGN_WITH_TRUST_TOKEN
                })
        public TrustTokenWithChallenge acquireVerifiedDeviceToken(byte[] challenge) {
            acquireVerifiedDeviceToken_enforcePermission();
            TrustTokenSetWithKey setWithKey;
            try {
                setWithKey = mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE);
            } catch (TrustTokenExhaustedException e) {
                if (mHasProvider) {
                    mRefreshScheduler.scheduleUrgentRefresh();
                }
                throw e;
            }
            try (var token =
                    new com.google.android.security.trusttoken.TrustToken(
                            getTrustAnchor(), setWithKey.getTokenSet().getTokenSet())) {
                // No exception means the token is valid.
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Fetched an invalid token: " + e.toString());
                // Since the tokens are fetched in a batch, we are likely to have a large
                // amount of invalid tokens. We return immediately to avoid keeping the
                // client waiting.
                //
                // Note that the database doesn't return expired tokens, so this only
                // happens when something goes wrong.
                throw new IllegalStateException(
                        "Cannot acquire valid tokens. Consider the service unavailable.");
            }
            byte[] challengeResponse = mMasterKey.sign(setWithKey.getKey(), challenge);
            return new TrustTokenWithChallenge(
                    setWithKey.getTokenSet().asVerifiedDeviceToken(), challengeResponse);
        }

        @RequiresNoPermission
        @Override
        public TrustTokenIdentitySet acquirePreparedIdentitySet(byte[] challenge) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "acquirePreparedIdentitySet is not yet implemented.");
            return null;
        }

        @Override
        @EnforcePermission(android.Manifest.permission.ACQUIRE_VERIFIED_DEVICE_TOKEN)
        public int verifyTrustTokenAndChallenge(
                TrustToken token, byte[] remoteResponse, byte[] expectedChallenge) {
            // TODO(b/418280383): Replace with correct permissions.
            verifyTrustTokenAndChallenge_enforcePermission();
            try (var parsedToken =
                    new com.google.android.security.trusttoken.TrustToken(
                            getTrustAnchor(), token.encoded())) {
                var policy = parsedToken.getPolicy();
                if (policy != TrustTokenPolicy.VERIFIED_DEVICE
                        && policy != TrustTokenPolicy.VERIFIED_DEVICE_STRONG) {
                    // The method only takes a verified device token. Identity tokens should
                    // be verified with |verifyIdentityTokens| after the device is
                    // authenticated.
                    throw new IllegalArgumentException("not a verified device token");
                }
                if (parsedToken.verifyChallenge(expectedChallenge, remoteResponse)) {
                    return VERIFICATION_SUCCESS;
                } else {
                    return VERIFICATION_FAILURE_CHALLENGE_INCORRECT;
                }
            } catch (TrustTokenInvalidSignatureException e) {
                Slog.e(TAG, "Trust token signature error: " + e.toString());
                return VERIFICATION_FAILURE_SIGNATURE_INVALID;
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Failed to verify the trust token: " + e.toString());
                return VERIFICATION_FAILURE_UNKNOWN;
            } catch (TrustConfigurationUnavailableException e) {
                if (mHasProvider) {
                    mRefreshScheduler.scheduleUrgentRefresh();
                }
                throw e;
            }
        }

        @RequiresNoPermission
        @Override
        public int[] verifyIdentityTokens(
                TrustToken verifiedDeviceToken, TrustToken[] identityTokens) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "verifyIdentityTokens is not yet implemented.");
            int[] results = new int[identityTokens.length];
            Arrays.fill(results, VERIFICATION_FAILURE_UNKNOWN);
            return results;
        }

        @RequiresNoPermission
        @Override
        public void updatePreparedIdentities(List<String> identities) {
            // TODO(b/472383812): Implement this method.
            // TODO(b/472383812): Protect with permissions
            Slog.w(TAG, "updatePreparedIdentities is not yet implemented.");
        }
    }

    private final TrustTokenManagerInternal mInternal =
            new TrustTokenManagerInternal() {
                @Override
                public List<TrustTokenKey> generateKeys(int num) {
                    var keys = new ArrayList<TrustTokenKey>(num);
                    for (int i = 0; i < num; ++i) {
                        keys.add(mMasterKey.generatePerTokenKey());
                    }
                    return keys;
                }

                @Override
                public TrustTokenBatchAttestation attestKeys(List<TrustTokenKey> keys) {
                    return mMasterKey.attest(keys);
                }

                @Override
                public void addTrustTokens(
                        @NonNull List<TrustTokenKey> keys, @NonNull List<byte[]> tokens)
                        throws TrustConfigurationUnavailableException {
                    // We only supported trusted device tokens at the moment, so there should be
                    // 1-to-1 mapping between the keys and the tokens
                    if (keys.size() != tokens.size()) {
                        throw new IllegalArgumentException(
                                "the number of keys and the number of tokens mismatch");
                    }
                    var tokenByKey = new HashMap<ByteBuffer, TrustTokenSet>();
                    TrustAnchor anchor = getTrustAnchor();
                    for (byte[] encoded : tokens) {
                        try (var token =
                                new com.google.android.security.trusttoken.TrustToken(
                                        anchor, encoded)) {
                            if (token.getPolicy() != TrustTokenPolicy.VERIFIED_DEVICE
                                    && token.getPolicy()
                                            != TrustTokenPolicy.VERIFIED_DEVICE_STRONG) {
                                throw new IllegalArgumentException("not a verified device token");
                            }
                            var tokenSet =
                                    new TrustTokenSet.Builder()
                                            .setType(TrustTokenSet.TYPE_VERIFIED_DEVICE)
                                            .setTokenSet(encoded)
                                            .setExpireAt(token.getExpirationTime())
                                            .setCreatedAt(token.getIssuedAt())
                                            .setPublicKey(token.getPublicKey())
                                            .build();
                            if (tokenByKey.putIfAbsent(
                                            ByteBuffer.wrap(tokenSet.getPublicKey()), tokenSet)
                                    != null) {
                                throw new IllegalArgumentException(
                                        "more than one trust device token per key");
                            }
                        }
                    }
                    var validTokens = new ArrayList<TrustTokenSetWithKey>(tokens.size());
                    for (TrustTokenKey key : keys) {
                        TrustTokenSet token =
                                tokenByKey.remove(ByteBuffer.wrap(key.getPublicKey()));
                        if (token == null) {
                            throw new IllegalArgumentException(
                                    "missing token for at least one key");
                        }
                        validTokens.add(new TrustTokenSetWithKey(key, token));
                    }
                    mDatabase.addTrustTokenSets(validTokens);
                }

                @Override
                public void updateTrustConfiguration(@NonNull TrustConfiguration configuration) {
                    mDatabase.updateTrustConfiguration(configuration);
                    updateTrustAnchor();
                }
            };
}
