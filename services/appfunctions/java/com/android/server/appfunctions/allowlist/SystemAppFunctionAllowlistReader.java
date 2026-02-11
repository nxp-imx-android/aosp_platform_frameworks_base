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

package com.android.server.appfunctions.allowlist;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.Signature;
import android.content.pm.SignedPackage;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;
import android.util.ArrayMap;
import android.util.LruCache;
import android.util.PackageUtils;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.server.LocalServices;
import com.android.server.appfunctions.ServiceConfig;
import com.android.server.appfunctions.ServiceConfigImpl;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implementation of {@link AppFunctionAllowlistReader} to read allowlist from system service. */
public class SystemAppFunctionAllowlistReader implements AppFunctionAllowlistReader {
    private static final String TAG = "AppFunctionAllowlistReader";
    private static final boolean DEBUG = Build.TYPE.equals("eng");

    private static SystemAppFunctionAllowlistReader sInstance = null;

    private final Object mLock = new Object();

    private final AtomicBoolean mEnable = new AtomicBoolean(false);

    @GuardedBy("mLock")
    private final ArrayMap<SignedPackage, List<SignedPackage>> mTestAllowlist = new ArrayMap<>();

    // TODO(b/457349791): Handle cache invalidation by observe the allowlist change
    @GuardedBy("mLock")
    private final LruCache<SignedPackage, List<SignedPackage>> mCache;

    private final PackageManagerInternal mPackageManagerInternal;

    @VisibleForTesting
    public SystemAppFunctionAllowlistReader(
            @NonNull PackageManagerInternal packageManagerInternal,
            @NonNull ServiceConfig serviceConfig) {
        mPackageManagerInternal = Objects.requireNonNull(packageManagerInternal);
        mCache = new LruCache<>(serviceConfig.getAppFunctionAllowlistCacheSize());
    }

    // TODO(b/457349791): Remove this once the source is stable to avoid disruption
    /** Enable allowlist. */
    public void enableAllowlist() {
        mEnable.set(true);
    }

    // TODO(b/457349791): Remove this once the source is stable to avoid disruption
    /** Disable allowlist. */
    public void disableAllowlist() {
        mEnable.set(false);
    }

    // TODO(b/457349791): Remove once allowlist service is ready
    /** Sets test allowlist. */
    public void setTestAllowlist(
            @NonNull SignedPackage agentPackage, @NonNull List<SignedPackage> packages) {
        synchronized (mLock) {
            mTestAllowlist.put(agentPackage, packages);
        }
    }

    // TODO(b/457349791): Remove once allowlist service is ready
    /** Clear test allowlist. */
    public void clearTestAllowlist() {
        synchronized (mLock) {
            mCache.evictAll();
            mTestAllowlist.clear();
        }
    }

    @Override
    @NonNull
    public AndroidFuture<Boolean> isAllowlisted(
            @NonNull String agentPackageName, @NonNull String targetPackageName, int userId) {
        if (mEnable.get()) {
            if (agentPackageName.equals(targetPackageName)) {
                // Interaction with the app's own AppFunction is implicitly allowed.
                return AndroidFuture.completedFuture(true);
            }

            PackageInfo agentPackageInfo = getPackageInfo(agentPackageName, userId);
            if (agentPackageInfo == null) {
                Slog.w(
                        TAG,
                        "Unable to resolve PackageInfo for "
                                + agentPackageName
                                + " in user "
                                + userId);
                return AndroidFuture.completedFuture(false);
            }

            byte[] agentLatestCertificate = getLatestCertificateDigest(agentPackageInfo);
            if (agentLatestCertificate == null) {
                return AndroidFuture.completedFuture(false);
            }
            SignedPackage agentSignedPackage =
                    new SignedPackage(
                            agentPackageName,
                            PackageUtils.computeSha256DigestBytes(agentLatestCertificate));

            return getAllowlistState(agentSignedPackage)
                    .thenApply(
                            (allowlistTargets) -> {
                                for (SignedPackage allowedTarget : allowlistTargets) {
                                    if (allowedTarget.getPackageName().equals(targetPackageName)) {
                                        return true;
                                    }
                                }

                                return false;
                            });
        } else {
            return AndroidFuture.completedFuture(true);
        }
    }

    @Nullable
    private byte[] getLatestCertificateDigest(@NonNull PackageInfo packageInfo) {
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            if (DEBUG) {
                Slog.d(TAG, "Unable to resolve SigningInfo from " + packageInfo.packageName);
            }
            return null;
        }
        Signature[] histories = signingInfo.getSigningCertificateHistory();
        if (histories == null || histories.length == 0) {
            if (DEBUG) {
                Slog.d(
                        TAG,
                        "Unable to resolve certificate history from " + packageInfo.packageName);
            }
            return null;
        }
        return histories[histories.length - 1].toByteArray();
    }

    /**
     * Gets a list of valid targets for {@code agentSignedPackage}.
     *
     * <p>Lazily retrieve from the remote source if unavailable in local cache. In most cases, the
     * caller of AppFunction is very limited. Therefore, the cache miss for follow up query should
     * be less common. This can reduce the IPC latency for each request.
     */
    @NonNull
    private AndroidFuture<List<SignedPackage>> getAllowlistState(
            @NonNull SignedPackage agentSignedPackage) {
        synchronized (mLock) {
            List<SignedPackage> current = mCache.get(agentSignedPackage);
            if (current == null) {
                // TODO(b/457349791): Update to read from allowlist service when ready
                List<SignedPackage> latestTargets = mTestAllowlist.get(agentSignedPackage);
                if (latestTargets == null) {
                    if (DEBUG) {
                        Slog.d(TAG, "No allowlist state for " + agentSignedPackage);
                    }
                    return AndroidFuture.completedFuture(List.of());
                }
                mCache.put(agentSignedPackage, latestTargets);
            }
            return AndroidFuture.completedFuture(mCache.get(agentSignedPackage));
        }
    }

    @Nullable
    private PackageInfo getPackageInfo(@NonNull String packageName, int userId) {
        return mPackageManagerInternal.getPackageInfo(
                packageName,
                /* flags= */ PackageManager.GET_SIGNING_CERTIFICATES,
                Binder.getCallingUid(),
                userId);
    }

    /** Gets the singleton instance. */
    public static synchronized SystemAppFunctionAllowlistReader getInstance() {
        if (sInstance == null) {
            sInstance =
                    new SystemAppFunctionAllowlistReader(
                            LocalServices.getService(PackageManagerInternal.class),
                            new ServiceConfigImpl());
        }
        return sInstance;
    }
}
