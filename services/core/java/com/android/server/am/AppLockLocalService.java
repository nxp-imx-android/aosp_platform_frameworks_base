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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_STATE_TOP;

import android.annotation.NonNull;
import android.app.AppLockInternal;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service that manages the locked state of applications for the App Lock feature.
 *
 * <p>This service is responsible for determining whether an app should be considered locked or
 * unlocked based on its visibility and recent user authentications. It listens to UID process state
 * changes to decide when to lock an application. To prevent an abrupt user experience when
 * switching apps, a grace period is provided before an app is actually locked after it moves to the
 * background. If the app returns to the foreground within this grace period, the pending lock is
 * canceled.
 *
 * <p>The locked state changes are communicated to registered listeners via
 * {@link PackageLockedStateListener}.
 *
 * <p>This class deals with two locks: the {@link ActivityManagerService} (AMS) lock and one
 * specific to this class. To avoid deadlocks, the AMS lock should always be the outer lock, and
 * should never be acquired while holding onto the AppLockLocalService lock.
 */
// TODO(b/465408530): Move class its own directory
public final class AppLockLocalService implements AppLockInternal {

    // TODO(b/454309480): Handle UID changes.
    // TODO(b/436379812): Populate initial App Lock Enabled Packages.
    private static final String TAG = "AppLockLocalService";
    // TODO(b/454308946): Update grace period to be configurable
    private static final Duration DEFAULT_APP_LOCK_GRACE_PERIOD_MS = Duration.ofMillis(5000L);

    private final Object mLock = new Object();

    // Array where indices are userId and values are (map of package name -> AppLockLockedState)
    // TODO(b/454309480): Implement listeners to keep mAppLockLockedStatesForUser up to date.
    //
    // To keep this up to date and remove obsolete entries, the following events will be monitored:
    // - User removal
    // - Package removal
    // - Package App Lock enablement status
    @GuardedBy("mLock")
    private final SparseArray<ArrayMap<String, AppLockLockedState>> mAppLockLockedStatesForUser =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final ArrayList<PackageLockedStateListener> mPackageLockedStateListeners =
            new ArrayList<>();

    private final ActivityManagerService mAmService;
    private final ActivityTaskManagerInternal mAtmInternal;
    // Note: PackageManagerInternal is not available at the time of construction. Use
    // getPackageManagerInternal() to access it.
    private PackageManagerInternal mPmInternal;

    AppLockLocalService(final ActivityManagerService service) {
        mAmService = service;
        mAtmInternal = service.mAtmInternal;
    }

    private PackageManagerInternal getPackageManagerInternal() {
        if (mPmInternal == null) {
            mPmInternal = mAmService.getPackageManagerInternal();
        }
        return mPmInternal;
    }

    @Override
    public SparseArray<Set<String>> getAppLockEnabledPackages() {
        final SparseArray<Set<String>> appLockEnabledPackages = new SparseArray<>();
        synchronized (mLock) {
            for (int i = 0; i < mAppLockLockedStatesForUser.size(); i++) {
                final int userId = mAppLockLockedStatesForUser.keyAt(i);
                final ArrayMap<String, AppLockLockedState> userPackages =
                        mAppLockLockedStatesForUser.valueAt(i);

                if (userPackages != null && !userPackages.isEmpty()) {
                    // Packages that are currently locked must have App Lock enabled.
                    appLockEnabledPackages.put(userId, new ArraySet<>(userPackages.keySet()));
                }
            }
        }
        return appLockEnabledPackages;
    }

    @Override
    public void registerPackageLockedStateListener(@NonNull PackageLockedStateListener listener) {
        // TODO(b/436379942): Implement listener registration.
    }

    @Override
    public void unregisterPackageLockedStateListener(@NonNull PackageLockedStateListener listener) {
        // TODO(b/436379942): Implement listener unregistration.
    }

    @Override
    public boolean isPackageAppLockEnabled(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        final long token = Binder.clearCallingIdentity();
        try {
            return mAmService.getPackageManager().isPackageAppLockEnabled(packageName, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to retrieve the package's App Lock enablement state");
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return false;
    }

    // NOTE: This method will hold the ActivityManagerService lock. Callers should be mindful of not
    // causing a deadlock.
    @Override
    public boolean isPackageLocked(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        // 1. Check if App Lock is enabled.
        if (!isPackageAppLockEnabled(packageName, userId)) {
            return false;
        }

        // 2. Check if the last successful authentication is within the grace period. Note that
        //    the grace period associated with the package's last visibility in the foreground
        //    is checked below with pending locked jobs.
        if (isLastAuthWithinGracePeriod(packageName, userId)) {
            return false;
        }

        synchronized (mAmService) {

            // 3. Check if the package has any visible tasks.
            final List<ActivityAssistInfo> visibleAaInfoList =
                    getVisibleActivityAssistInfosForPackage(packageName, userId);
            if (!visibleAaInfoList.isEmpty()) {
                // If there are visible tasks and any of them have showWhenLocked=false, the package
                // is unlocked. showWhenLocked indicates that the activity should be shown even if
                // the device is locked, so those activities should still be shown if the app is
                // locked.
                final int callingUid = Binder.getCallingUid();
                for (ActivityAssistInfo aaInfo : visibleAaInfoList) {
                    final ActivityInfo info = getPackageManagerInternal().getActivityInfo(
                            aaInfo.getComponentName(), PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, callingUid, userId);
                    if (info != null && (info.flags & ActivityInfo.FLAG_SHOW_WHEN_LOCKED) == 0) {
                        return false;
                    }
                }
                // If all visible tasks have showWhenLocked=true, the package is locked.
                return true;
            }

            // 4. Check if the package is in the foreground: at least one process belonging to
            //    the package should have a PROCESS_STATE_TOP state (includes the notification
            //    shade being pulled down while the app is in the foreground).
            // TODO(b/454309480): Check for a pending job in AppLock#isPackageLocked
            return !anyProcessInPackageIsInForeground(packageName, userId);
        }
    }

    private boolean isLastAuthWithinGracePeriod(String packageName, int userId) {
        final long lastSuccessfulAuth = getLastSuccessfulAuthTimeForLockedPackage(packageName,
                userId);
        return lastSuccessfulAuth + DEFAULT_APP_LOCK_GRACE_PERIOD_MS.toMillis()
                > System.currentTimeMillis();
    }

    @GuardedBy("mAmService")
    private ArrayList<ActivityAssistInfo> getVisibleActivityAssistInfosForPackage(
            String packageName, int userId) {
        final List<ActivityAssistInfo> aaInfoList = mAtmInternal.getTopVisibleActivities();
        // TODO(b/456178049): Filter out paused activities
        final ArrayList<ActivityAssistInfo> visibleAaInfoList = new ArrayList<>();
        for (int i = aaInfoList.size() - 1; i >= 0; i--) {
            final ActivityAssistInfo aaInfo = aaInfoList.get(i);
            if (aaInfo.getUserId() == userId && aaInfo.getComponentName().getPackageName().equals(
                    packageName)) {
                visibleAaInfoList.add(aaInfo);
            }
        }
        return visibleAaInfoList;
    }


    @GuardedBy("mAmService")
    private boolean anyProcessInPackageIsInForeground(String packageName, int userId) {
        final int uid = getPackageManagerInternal().getPackageUid(packageName, /* flags= */ 0,
                userId);
        final UidRecord uidRecord = mAmService.mProcessList.getUidRecordLOSP(uid);

        return uidRecord != null && uidRecord.anyProcessInPackageMatches(packageName,
                process -> process.getCurProcState() <= PROCESS_STATE_TOP);
    }

    @Override
    public void setAppLockEnabledPackageSuccessfullyAuthenticated(@NonNull String packageName,
            int userId) {
        Objects.requireNonNull(packageName);

        synchronized (mLock) {
            final AppLockLockedState state = getOrCreateAppLockLockedState(packageName, userId);
            state.mLastSuccessfulAuthTimeSinceBoot = System.currentTimeMillis();
        }
    }

    private long getLastSuccessfulAuthTimeForLockedPackage(String packageName, int userId) {
        synchronized (mLock) {
            final AppLockLockedState state = getOrCreateAppLockLockedState(packageName, userId);
            return state.mLastSuccessfulAuthTimeSinceBoot;
        }
    }

    @NonNull
    @GuardedBy("mLock")
    private AppLockLockedState getOrCreateAppLockLockedState(String packageName, int userId) {
        ArrayMap<String, AppLockLockedState> userStates = mAppLockLockedStatesForUser.get(userId);
        if (userStates == null) {
            userStates = new ArrayMap<>();
            mAppLockLockedStatesForUser.put(userId, userStates);
        }
        return userStates.computeIfAbsent(packageName, pkgName -> new AppLockLockedState());
    }

    private static final class AppLockLockedState {
        private static final long DEFAULT_LAST_AUTH_TIME_SINCE_BOOT_MS = 0L;
        // Last successful authentication timestamp, which is required to calculate grace period
        // expiry.
        Long mLastSuccessfulAuthTimeSinceBoot;

        AppLockLockedState() {
            mLastSuccessfulAuthTimeSinceBoot = DEFAULT_LAST_AUTH_TIME_SINCE_BOOT_MS;
        }
    }
}
