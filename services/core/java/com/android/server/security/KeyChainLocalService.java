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

package com.android.server.security;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.security.keystore.KeyGenParameterSpec;

/**
 * Implements {@link KeyChainManagerInternal} to provide APIs for other system_server components
 * like DevicePolicyManagerService. This class routes calls to the {@link KeyChainSystemService}.
 *
 * @hide
 */
public final class KeyChainLocalService extends KeyChainManagerInternal {

    private static final String TAG = "KeyChainLocalService";

    private final KeyChainSystemService mService;

    public KeyChainLocalService(@NonNull KeyChainSystemService service) {
        mService = service;
    }

    @Override
    boolean installUserKeyPair(
            @NonNull byte[] pkcs8PrivateKey,
            @NonNull byte[] certificate,
            @Nullable byte[][] certificateChain,
            @NonNull String alias,
            int userId) {
        return mService.installUserKeyPair(
                pkcs8PrivateKey, certificate, certificateChain, alias, userId);
    }

    @Override
    boolean installDeviceKeyPair(
            @NonNull byte[] pkcs8PrivateKey,
            @NonNull byte[] certificate,
            @Nullable byte[][] certificateChain,
            @NonNull String alias) {
        return mService.installDeviceKeyPair(pkcs8PrivateKey, certificate, certificateChain, alias);
    }

    @Override
    int generateUserKeyPair(
            @NonNull String algorithm,
            @NonNull KeyGenParameterSpec keySpec,
            int userId) {
        return mService.generateUserKeyPair(algorithm, keySpec, userId);
    }

    @Override
    int generateDeviceKeyPair(@NonNull String algorithm, @NonNull KeyGenParameterSpec keySpec) {
        return mService.generateDeviceKeyPair(algorithm, keySpec);
    }

    @Override
    boolean setUserKeyPairCertificate(
            @NonNull String alias,
            @NonNull byte[] clientCertificate,
            @NonNull byte[] clientCertificateChain,
            int userId) {
        return mService.setUserKeyPairCertificate(
                alias, clientCertificate, clientCertificateChain, userId);
    }

    @Override
    boolean setDeviceKeyPairCertificate(
            @NonNull String alias,
            @NonNull byte[] clientCertificate,
            @NonNull byte[] clientCertificateChain) {
        return mService.setDeviceKeyPairCertificate(
                alias, clientCertificate, clientCertificateChain);
    }
}
