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

package com.android.server.pm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.supervision.SupervisionManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.pkg.component.ParsedActivity;
import com.android.server.LocalServices;
import com.android.server.SystemConfig;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.pkg.mutate.PackageStateMutator;
import com.android.server.pm.pkg.mutate.PackageUserStateWrite;

import java.util.List;
import java.util.function.Supplier;

/** Helper class for PackageManager to handle logic related to App Lock */
public final class AppLockPackageHelper {

    private static final String TAG = "AppLockPackageHelper";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.DEBUG);
    private final Context mContext;
    private final PackageManagerService mPms;
    private final BroadcastHelper mBroadcastHelper;
    private final Injector mInjector;

    private static Boolean sIsSupportedDeviceForAppLock = null;
    private static ArraySet<String> sAppLockExemptPackages = null;

    @VisibleForTesting
    AppLockPackageHelper(Context context, PackageManagerService packageManagerService,
            BroadcastHelper broadcastHelper, Injector injector) {
        mContext = context;
        mPms = packageManagerService;
        mBroadcastHelper = broadcastHelper;
        mInjector = injector;
    }

    public AppLockPackageHelper(Context context, PackageManagerService packageManagerService,
            BroadcastHelper broadcastHelper) {
        this(context, packageManagerService, broadcastHelper, new InjectorImpl());
    }

    // Use the static version to set the ApplicationInfo field
    private boolean isAppLockSupported(String packageName, int userId, Computer snapshot) {
        final PackageStateInternal ps = snapshot.getPackageStateInternal(packageName);
        if (ps == null) {
            return false;
        }
        final AndroidPackage pkg = ps.getPkg();
        if (pkg == null) {
            return false;
        }

        return isAppLockSupported(packageName, userId, pkg.getActivities());
    }

    /**
     * Checks if a package has been explicitly prohibited by the system from enabling App Lock.
     *
     * @param pkgName    Name of the package to check the App Lock supported state
     * @param userId     User Id to check for the package's App Lock supported state
     * @param activities A list of activities associated with the package
     * @return {@code true} if App Lock is supported for this package, {@code false} otherwise
     */
    private static boolean isAppLockSupported(String pkgName, int userId,
            List<ParsedActivity> activities) {
        // App Lock is not supported on certain form factors (e.g. watches, cars, TVs).
        if (!isSupportedDeviceForAppLock()) {
            return false;
        }

        // App Lock is not supported for packages explicitly exempted in the system configuration.
        if (isPackageExemptInSystemConfig(pkgName)) {
            return false;
        }

        // App Lock is not supported for profile users.
        if (!isFullUser(userId)) {
            return false;
        }

        // App Lock is not supported for supervised users.
        if (isSupervisedUser(userId)) {
            return false;
        }

        // App Lock is not supported for headless apps.
        return InstallPackageHelper.hasLauncherEntry(activities);
    }

    private static boolean isSupportedDeviceForAppLock() {
        if (sIsSupportedDeviceForAppLock == null) {
            sIsSupportedDeviceForAppLock = !hasSystemFeature(PackageManager.FEATURE_WATCH)
                    && !hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                    && !hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }
        return sIsSupportedDeviceForAppLock;
    }

    private static boolean hasSystemFeature(String featureName) {
        FeatureInfo feature = SystemConfig.getInstance().getAvailableFeatures().get(featureName);
        if (feature == null) {
            return false;
        } else {
            return feature.version >= 0;
        }
    }

    private static boolean isPackageExemptInSystemConfig(String packageName) {
        if (sAppLockExemptPackages == null) {
            sAppLockExemptPackages = SystemConfig.getInstance().getAppLockExemptPackages();
        }
        return sAppLockExemptPackages.contains(packageName);
    }

    private static boolean isFullUser(int userId) {
        final UserManagerInternal umInternal = LocalServices.getService(UserManagerInternal.class);
        if (umInternal == null) {
            return false;
        }
        UserInfo userInfo = umInternal.getUserInfo(userId);
        return userInfo != null && userInfo.isFull();
    }

    private static boolean isSupervisedUser(int userId) {
        final SupervisionManagerInternal smInternal = LocalServices.getService(
                SupervisionManagerInternal.class);
        final long token = Binder.clearCallingIdentity();
        try {
            return smInternal != null && smInternal.isSupervisionEnabledForUser(userId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Returns a {@link PendingIntent} to launch an activity that allows the caller to set App Lock
     * for the specified package. Returns null if the App Lock state of the package cannot be set,
     * either because App Lock is not supported for that package, or because it is already set to
     * that value.
     *
     * <p> Before calling this API to avoid getting a null PendingIntent, callers should first
     * verify that App Lock is supported for the specified package by first checking
     * {@link ApplicationInfo#isAppLockSupported}, or if it's already at the target state for that
     * package by checking {@link ApplicationInfo#isAppLockEnabled}. The PendingIntent resolves to
     * an activity, which allows the user to enroll a device credential if one isn't enrolled, and
     * then requires authentication before setting the App Lock enablement state as enabled or
     * disabled.
     *
     * @param snapshot    Computer snapshot.
     * @param packageName the package to enable or disable App Lock.
     * @param userId      the user to enable or disable App Lock for the package.
     * @param enabled     {@code true} when the user would like to enable App Lock for the given
     *                    package, {@code false} otherwise
     * @return a {@link PendingIntent} to launch an activity to set App Lock for the passed in
     *         package. If the package does not support App Lock or the package's App Lock state is
     *         already in the passed in state, return null.
     */
    @Nullable
    public PendingIntent getEnableAppLockIntentForPackage(@NonNull Computer snapshot,
            String packageName, int userId, boolean enabled) {
        // If the package doesn't support App Lock, return null.
        if (!isAppLockSupported(packageName, userId, snapshot)) {
            if (DEBUG) {
                Slog.d(TAG, "App Lock not supported for the package, no PendingIntent to return");
            }
            return null;
        }

        // If the package already has App Lock set to the desired state, we should return null
        if (isPackageAppLockEnabled(snapshot, packageName, userId) == enabled) {
            if (DEBUG) {
                Slog.d(TAG,
                        "App Lock enabled state is already set to the passed in value, no "
                                + "PendingIntent to return");
            }
            return null;
        }

        // TODO(b/436380342): Make the intent to launch AppLockActivity an explicit intent.
        // We use {@link Intent#setIdentifier(String)} to ensure that the Intents within the created
        // {@link PendingIntent} are unique. This is needed because {@link Intent#filterEquals
        // (Intent)} doesn't compare Intent extras, so two PendingIntents that only differ in their
        // extras would be considered the same, leading to unexpected overwrites.
        Intent intent = new Intent(PackageManager.ACTION_SET_APP_LOCK)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, enabled)
                .setIdentifier("unique:" + System.currentTimeMillis())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final long token = Binder.clearCallingIdentity();
        try {
            // The following activity options prevent the sender of the PendingIntent from using
            // Background Activity Launch (BAL) privileges of the creator, in this case the system.
            ActivityOptions options =
                    ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
            // request code is 0 because we don't call setResult in AppLockActivity     .
            return PendingIntent.getActivityAsUser(mContext, /*requestCode=*/ 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT, options.toBundle(),
                    UserHandle.of(userId));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Checks if the package has App Lock enabled.
     *
     * @param snapshot    Computer snapshot
     * @param packageName Name of the package to set the App Lock enablement state for
     * @param userId      User Id to set the App Lock enablement state for
     * @return {@code true} if App Lock is enabled for the package and user, {@code false} otherwise
     */
    public boolean isPackageAppLockEnabled(@NonNull Computer snapshot, String packageName,
            int userId) {
        PackageStateInternal packageState = snapshot.getPackageStateInternal(packageName);
        return packageState != null && packageState.getUserStateOrDefault(
                userId).isAppLockEnabled();
    }

    /**
     * Sets the App Lock enablement state for a given package and user.
     *
     * <p>This method will write the new state to disk if the caller has the necessary
     * permissions and the new enablement state is different from the current state.
     *
     * <p>If the caller is neither {@link Process#SYSTEM_UID} nor {@link Process#ROOT_UID} this
     * method will do nothing and return {@code false}.
     *
     * <p>If App Lock state can't be changed, either because
     * {@link #isAppLockSupported(String, int, Computer)} is false or because the device is not
     * currently secured with a device credential (e.g., PIN, pattern, or password), this method
     * will not set the target App Lock state and will return {@code false}. Additionally, if the
     * app currently has App Lock enabled, this method will also disable App Lock for the app,
     * because the conditions to keep App Lock enabled are no longer satisfied.
     *
     * <p>If the App Lock state is already set to the desired value, and the caller is permitted to
     * make this change, this method will do nothing and return {@code true}.
     *
     * @param snapshotSupplier    Computer snapshot supplier
     * @param packageName Name of the package to write App Lock enablement state
     * @param userId      User Id to write App Lock enablement state
     * @param enabled     The target App Lock enablement state
     * @param callingUid  The Uid of the caller
     * @return {@code true} if the App Lock enablement state was set to the passed in value for the
     *                      package and user, {@code false} otherwise
     */
    public boolean setPackageAppLockEnabled(@NonNull Supplier<Computer> snapshotSupplier,
            String packageName, int userId, boolean enabled, int callingUid) {
        if (callingUid != Process.SYSTEM_UID && callingUid != Process.ROOT_UID) {
            if (DEBUG) {
                Slog.d(TAG, "setPackageAppLockEnabled can only be called by the system");
            }
            return false;
        }

        Computer snapshot = snapshotSupplier.get();
        boolean currentEnabled = isPackageAppLockEnabled(snapshot, packageName, userId);
        boolean canSetPackageAppLockEnabledState = isAppLockSupported(packageName, userId, snapshot)
                && mInjector.isDeviceSecure(mContext, userId);

        // TODO(b/454876719): proactively listen to events that could disable App Lock, e.g. user
        // removal, device credential removal, an app becoming headless, etc. and update App Lock
        // enablement state accordingly
        if (!canSetPackageAppLockEnabledState) {
            // Disable App Lock for the package, because the conditions to keep App Lock enabled are
            // no longer satisfied.
            if (currentEnabled) {
                writeAppLockStateToDisk(packageName, userId, /* enabled= */ false);
            }
            return false;
        }

        if (enabled != currentEnabled) {
            writeAppLockStateToDisk(packageName, userId, enabled);
        }
        return true;
    }

    private void writeAppLockStateToDisk(String packageName, int userId, boolean enabled) {
        PackageStateMutator.InitialState state = mPms.recordInitialState();
        mPms.commitPackageStateMutation(state, mutator -> {
            final PackageUserStateWrite userState = mutator.forPackage(packageName).userState(
                    userId);
            userState.setAppLockEnabled(enabled);
        });
        mPms.scheduleWritePackageRestrictions(userId);
        mBroadcastHelper.sendPackageAppLockStateChangedForUser(packageName, userId, enabled);
    }

    @VisibleForTesting
    interface Injector {
        boolean isDeviceSecure(Context context, int userId);
    }

    private static final class InjectorImpl implements Injector {
        @Override
        public boolean isDeviceSecure(Context context, int userId) {
            final KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
            return keyguardManager != null && keyguardManager.isDeviceSecure(userId);
        }
    }
}
