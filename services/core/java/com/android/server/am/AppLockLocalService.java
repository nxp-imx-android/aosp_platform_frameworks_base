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
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public final class AppLockLocalService implements AppLockInternal,
        KeyguardManager.DeviceLockedStateListener {
    private static final String TAG = "AppLockLocalService";
    private static final boolean DEBUG = Build.isDebuggable() && Log.isLoggable(TAG, Log.DEBUG);
    // TODO(b/454308946): Update grace period to be configurable
    private static final Duration DEFAULT_APP_LOCK_GRACE_PERIOD_MS = Duration.ofMillis(5000L);

    private final Object mLock = new Object();

    // Array where indices are userId and values are (map of package name -> AppLockLockedState)
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
    private final Injector mInjector;
    @VisibleForTesting
    final PackageMonitor mPackageMonitor = new AppLockPackageMonitor(this);
    // Note: PackageManagerInternal is not available at the time of construction. Use
    // getPackageManagerInternal() to access it.
    private PackageManagerInternal mPmInternal;

    AppLockLocalService(final ActivityManagerService service) {
        this(service, new InjectorImpl(service));
    }

    @VisibleForTesting
    AppLockLocalService(final ActivityManagerService service, Injector injector) {
        mAmService = service;
        mAtmInternal = service.mAtmInternal;
        mInjector = injector;
    }

    /**
     * Called by the {@link ActivityManagerService} once the system services are ready.
     */
    public void systemServicesReady() {
        initAppLockLockedStates();
        mPackageMonitor.register(mAmService.mContext, UserHandle.ALL,
                BackgroundThread.getHandler());
        Context context = mAmService.mContext;
        final KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
        keyguardManager.addDeviceLockedStateListener(BackgroundThread.getExecutor(), /* listener= */
                this);
    }

    /**
     * Initializes {@link #mAppLockLockedStatesForUser} by querying all installed applications for
     * all full users and populating the map with packages that have App Lock enabled.
     */
    private void initAppLockLockedStates() {
        Trace.beginSection(TAG + ".initAppLockLockedStates");
        try {
            final UserManagerInternal umi = LocalServices.getService(UserManagerInternal.class);
            if (umi == null) {
                Slog.w(TAG, "Unable to retrieve UserManagerInternal");
                return;
            }
            final List<UserInfo> allUsers = umi.getUsers(
                    UserManagerInternal.USER_FILTER_WITH_ALL_COMPLETE_USERS);

            for (UserInfo userInfo : allUsers) {
                if (!userInfo.isFull()) {
                    continue;
                }

                final int userId = userInfo.id;
                final List<ApplicationInfo> appInfos =
                        getPackageManagerInternal().getInstalledApplications(
                                PackageManager.GET_APP_LOCK_INFO, userId, Process.myUid());
                if (appInfos == null) {
                    Slog.w(TAG, "Unable to retrieve appInfos for user " + userId);
                    continue;
                }
                synchronized (mLock) {
                    ArrayMap<String, AppLockLockedState> map = mAppLockLockedStatesForUser.get(
                            userId);

                    for (ApplicationInfo appInfo : appInfos) {
                        if (appInfo != null && appInfo.isAppLockEnabled) {
                            if (map == null) {
                                map = new ArrayMap<>();
                                mAppLockLockedStatesForUser.put(userId, map);
                            }
                            map.put(appInfo.packageName, new AppLockLockedState());
                        }
                    }
                }
            }
        } finally {
            Trace.endSection();
        }
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
        Objects.requireNonNull(listener);

        synchronized (mLock) {
            mPackageLockedStateListeners.add(listener);
        }
    }

    @Override
    public void unregisterPackageLockedStateListener(@NonNull PackageLockedStateListener listener) {
        Objects.requireNonNull(listener);

        synchronized (mLock) {
            mPackageLockedStateListeners.remove(listener);
        }
    }

    @Override
    public boolean isPackageAppLockEnabled(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        synchronized (mLock) {
            return mAppLockLockedStatesForUser.contains(userId) && mAppLockLockedStatesForUser.get(
                    userId).containsKey(packageName);
        }
    }

    // NOTE: This method will hold the ActivityManagerService lock. Callers should be mindful of not
    // causing a deadlock.
    @Override
    public boolean isPackageLocked(@NonNull String packageName, int userId) {
        Trace.beginSection(TAG + ".isPackageLocked");
        try {
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

            if (Thread.holdsLock(mLock)) {
                Slog.wtf(TAG, "isPackageLocked: Attempting to acquire AMS lock while holding "
                        + "AppLockLocalService lock!");
            }

            synchronized (mAmService) {
                // 3. Check pending jobs. If there is a pending job to lock the package, the package
                //    must be unlocked.
                if (packageHasQueuedAppLockedJob(userId, packageName)) {
                    return false;
                }

                // 4. Check if the package has any visible tasks.
                final List<ActivityAssistInfo> visibleAaInfoList =
                        getVisibleActivityAssistInfosForPackageLocked(packageName, userId);
                if (!visibleAaInfoList.isEmpty()) {
                    // If there are visible tasks and any of them have showWhenLocked=false, the
                    // package  is unlocked. showWhenLocked indicates that the activity should be
                    // shown  even if the device is locked, so those activities should still be
                    // shown if the app is locked.
                    final int callingUid = Binder.getCallingUid();
                    for (ActivityAssistInfo aaInfo : visibleAaInfoList) {
                        final ActivityInfo info = getPackageManagerInternal().getActivityInfo(
                                aaInfo.getComponentName(), PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, callingUid,
                                userId);
                        if (info != null
                                && (info.flags & ActivityInfo.FLAG_SHOW_WHEN_LOCKED) == 0) {
                            return false;
                        }
                    }
                    // If all visible tasks have showWhenLocked=true, the package is locked.
                    return true;
                }

                // 5. Check if the package is in the foreground: at least one process belonging to
                //    the package should have a PROCESS_STATE_TOP state (includes the notification
                //    shade being pulled down while the app is in the foreground).
                return !anyProcessInPackageIsInForegroundLocked(packageName, userId);
            }
        } finally {
            Trace.endSection();
        }
    }

    private boolean isLastAuthWithinGracePeriod(String packageName, int userId) {
        final long lastSuccessfulAuth = getLastSuccessfulAuthTimeForLockedPackage(packageName,
                userId);
        return lastSuccessfulAuth + DEFAULT_APP_LOCK_GRACE_PERIOD_MS.toMillis()
                > System.currentTimeMillis();
    }

    @GuardedBy("mAmService")
    private boolean anyProcessInPackageIsInForegroundLocked(String packageName, int userId) {
        final int uid = getPackageManagerInternal().getPackageUid(packageName, /* flags= */ 0,
                userId);
        final UidRecord uidRecord = mAmService.mProcessList.getUidRecordLOSP(uid);

        return uidRecord != null && uidRecord.anyProcessInPackageMatches(packageName,
                process -> process.getCurProcState() <= PROCESS_STATE_TOP);
    }

    @GuardedBy("mAmService")
    private ArrayList<ActivityAssistInfo> getVisibleActivityAssistInfosForPackageLocked(
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

    /**
     * Updates the App Lock locked state for packages when a UID's process state changes.
     *
     * <p>This method is called when a UID's process state changes (e.g., from foreground to
     * background). It checks all packages associated with that UID and, if any of them have App
     * Lock enabled, it updates their locked state.
     *
     * <ul>
     *   <li>If a package becomes locked (i.e., it moves to the background), a delayed job is queued
     *       to update the state. This provides a grace period for the user to switch back to the
     *       app before it actually locks.
     *   <li>If a package becomes unlocked (i.e., it moves to the foreground), the state is updated
     *       immediately.
     *   <li>If a package moves to the background and then back to the foreground within the grace
     *       period, the queued lock update is canceled.
     * </ul>
     *
     * <p>Callbacks are sent to registered {@link PackageLockedStateListener}s to notify them of any
     * changes to the locked state.
     *
     * @param uidRec         object for the uid
     * @param uid            uid
     * @param enqueuedChange what kind of change
     * @param procState      the process state the uid is moving to
     */
    @GuardedBy("mAmService")
    public void handleUidChangeLocked(UidRecord uidRec, int uid, int enqueuedChange,
            int procState) {
        Trace.beginSection(TAG + ".handleUidChangeLocked");
        try {
            if ((enqueuedChange & UidRecord.CHANGE_PROCSTATE) == 0) {
                // Only handles process state changes, e.g. TOP <-> NOT_TOP
                return;
            }
            final int userId = UserHandle.getUserId(uid);

            // 1. Collect packages involved in the UID change.
            final ArraySet<String> packageSet = new ArraySet<>();
            for (int i = 0; i < uidRec.getNumOfProcs(); i++) {
                final ProcessRecord proc = uidRec.getProcessRecordByIndex(i);
                final String[] packages = proc.getProcessPackageNames();
                packageSet.addAll(Set.of(packages));
            }

            // 2. Remove packages that have App Lock disabled, as only App Lock enabled packages are
            //    relevant.
            packageSet.removeIf(pkg -> !isPackageAppLockEnabled(pkg, userId));

            if (packageSet.isEmpty()) {
                // No App Lock enabled packages involved in the UID change, nothing to do.
                return;
            }

            // 3. Iterate over App Lock enabled packages and schedule listener updates.
            for (int i = 0; i < packageSet.size(); i++) {
                final String packageName = packageSet.valueAt(i);
                // isPackageLocked should be called outside of this synchronized block because it
                // acquires the outer lock (mAmService).
                final boolean isCurrentlyLocked = procState != PROCESS_STATE_TOP && isPackageLocked(
                        packageName, userId);
                synchronized (mLock) {
                    final Boolean lastSentLockedState = getOrCreateAppLockLockedStateLocked(
                            packageName,
                            userId).mLastSentLockedState;

                    if (lastSentLockedState != null && lastSentLockedState == isCurrentlyLocked) {
                        // No change since last listener update.
                        if (!isCurrentlyLocked) {
                            // If the update that the package is locked hasn't been sent yet, but
                            // the
                            // package was moved off of the top (and now back to the top), cancel
                            // the
                            // queued lock update
                            cancelPackageAppLockedJobLocked(packageName, userId);
                        }
                        continue;
                    }

                    if (isCurrentlyLocked) {
                        handleLockedStateLocked(packageName, userId, /* lockImmediately= */ false);
                    } else {
                        handleUnlockedStateLocked(packageName, userId);
                    }
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    /** Send an immediate update that the package is unlocked. */
    @VisibleForTesting
    @GuardedBy("mLock")
    void handleUnlockedStateLocked(String packageName, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "handleUnlockedState for " + packageName + " in user: " + userId);
        }
        mInjector.getHandler().post(() -> {
            synchronized (AppLockLocalService.this.mLock) {
                for (int i = 0; i < mPackageLockedStateListeners.size(); i++) {
                    mPackageLockedStateListeners.get(i).onPackageLockedStateChanged(packageName,
                            userId, false);
                }
                final AppLockLockedState appLockLockedState = getOrCreateAppLockLockedStateLocked(
                        packageName, userId);
                appLockLockedState.mLastSentLockedState = false;
                cancelPackageAppLockedJobLocked(packageName, userId);
            }
        });
    }

    /**
     * Handles the event when a package becomes App Lock enabled. This involves adding the package
     * to the internal locked states map, and notifying listeners if the package is now locked. The
     * package may not be immediately locked if it had open windows during the enablement flow.
     *
     * @param packageName The package that had App Lock enabled.
     * @param userId      The user ID for which App Lock was enabled.
     */
    @VisibleForTesting
    void handleAppLockEnabled(String packageName, int userId) {
        synchronized (mLock) {
            getOrCreateAppLockLockedStateLocked(packageName, userId);
        }

        if (isPackageLocked(packageName, userId)) {
            mInjector.getHandler().post(() -> {
                synchronized (AppLockLocalService.this.mLock) {
                    for (int j = 0; j < mPackageLockedStateListeners.size(); j++) {
                        mPackageLockedStateListeners.get(j).onPackageLockedStateChanged(packageName,
                                userId, true);
                    }

                    getOrCreateAppLockLockedStateLocked(packageName, userId).mLastSentLockedState =
                            true;
                }
            });
        }
    }

    @VisibleForTesting
    @GuardedBy("mLock")
    void handleLockedStateLocked(String packageName, int userId, boolean lockImmediately) {
        if (DEBUG) {
            Slog.d(TAG, "handleLockedState for " + packageName + " and user: " + userId);
        }

        if (!lockImmediately && getOrCreateAppLockLockedStateLocked(packageName,
                userId).mLockedUpdateRunnable != null) {
            // This shouldn't happen, but if there's already a job to lock the package, don't
            // schedule a new one and let the existing one run. If we're locking immediately, do
            // that instead.
            return;
        }

        final Runnable setPackageLocked = () -> {
            if (!isPackageAppLockEnabled(packageName, userId)) {
                // ensure the package still has App Lock enabled
                return;
            }
            synchronized (AppLockLocalService.this.mLock) {
                for (int i = 0; i < mPackageLockedStateListeners.size(); i++) {
                    mPackageLockedStateListeners.get(i).onPackageLockedStateChanged(packageName,
                            userId, true);
                }
                final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName,
                        userId);
                state.mLastSentLockedState = true;
                state.mLockedUpdateRunnable = null;
            }
        };

        if (lockImmediately) {
            cancelPackageAppLockedJobLocked(packageName, userId);
            mInjector.getHandler().post(setPackageLocked);
            return;
        }

        getOrCreateAppLockLockedStateLocked(packageName, userId).mLockedUpdateRunnable =
                setPackageLocked;
        mInjector.getHandler().postDelayed(setPackageLocked,
                DEFAULT_APP_LOCK_GRACE_PERIOD_MS.toMillis());
    }

    @Override
    public void onDeviceLockedStateChanged(boolean isDeviceLocked) {
        if (!isDeviceLocked) {
            return;
        }
        synchronized (mLock) {
            for (int i = 0; i < mAppLockLockedStatesForUser.size(); i++) {
                final int userId = mAppLockLockedStatesForUser.keyAt(i);
                final ArrayMap<String, AppLockLockedState> userPackages =
                        mAppLockLockedStatesForUser.valueAt(i);

                if (userPackages == null) {
                    continue;
                }
                for (Map.Entry<String, AppLockLockedState> entry : userPackages.entrySet()) {
                    AppLockLockedState state = entry.getValue();
                    // Send an update for all unlocked packages that they are now immediately locked
                    if (state != null && state.mLastSentLockedState != null
                            && !state.mLastSentLockedState) {
                        if (DEBUG) {
                            Slog.d(TAG, entry.getKey()
                                    + " has become locked due to the device locking");
                        }
                        handleLockedStateLocked(entry.getKey(), userId, /* lockImmediately= */
                                true);
                    }
                }
            }
        }
    }

    @Override
    public void setAppLockEnabledPackageSuccessfullyAuthenticated(@NonNull String packageName,
            int userId) {
        Objects.requireNonNull(packageName);

        synchronized (mLock) {
            final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName,
                    userId);
            state.mLastSuccessfulAuthTimeSinceBoot = System.currentTimeMillis();
            handleUnlockedStateLocked(packageName, userId);
        }
    }

    private long getLastSuccessfulAuthTimeForLockedPackage(String packageName, int userId) {
        synchronized (mLock) {
            final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName,
                    userId);
            return state.mLastSuccessfulAuthTimeSinceBoot;
        }
    }

    @NonNull
    @GuardedBy("mLock")
    private AppLockLockedState getOrCreateAppLockLockedStateLocked(String packageName, int userId) {
        ArrayMap<String, AppLockLockedState> userStates = mAppLockLockedStatesForUser.get(userId);
        if (userStates == null) {
            userStates = new ArrayMap<>();
            mAppLockLockedStatesForUser.put(userId, userStates);
        }
        return userStates.computeIfAbsent(packageName, pkgName -> new AppLockLockedState());
    }

    @GuardedBy("mLock")
    private void cancelPackageAppLockedJobLocked(String packageName, int userId) {
        final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName, userId);
        if (state.mLockedUpdateRunnable == null) {
            return;
        }
        mInjector.getHandler().removeCallbacks(state.mLockedUpdateRunnable);
        state.mLockedUpdateRunnable = null;
    }

    boolean packageHasQueuedAppLockedJob(int userId, String packageName) {
        synchronized (mLock) {
            return getOrCreateAppLockLockedStateLocked(packageName, userId).mLockedUpdateRunnable
                    != null;
        }
    }

    @VisibleForTesting
    interface Injector {
        Handler getHandler();
    }

    /**
     * Represents the App Lock state for a specific package and user combination.
     *
     * <p>This class tracks the last successful authentication time, any pending runnables to
     * lock the app, and the last state change that was communicated to listeners. This allows the
     * service to manage the grace period before an app is locked and to avoid sending redundant
     * state change notifications.
     */
    static final class AppLockLockedState {
        private static final long DEFAULT_LAST_AUTH_TIME_SINCE_BOOT_MS = 0L;
        // Last successful authentication timestamp, which is required to calculate grace period
        // expiration.
        private long mLastSuccessfulAuthTimeSinceBoot;
        // A runnable to inform listeners that the package has become locked after the grace period
        // has expired. If the package moves back to PROCESS_STATE_TOP, the runnable should be
        // canceled. If this runnable exists for this package and user combination, the package
        // should be considered unlocked.
        private Runnable mLockedUpdateRunnable;
        // Last sent locked state to locked state listeners.
        private Boolean mLastSentLockedState;

        AppLockLockedState() {
            mLastSuccessfulAuthTimeSinceBoot = DEFAULT_LAST_AUTH_TIME_SINCE_BOOT_MS;
            mLockedUpdateRunnable = null;
            mLastSentLockedState = null;
        }
    }

    private static final class AppLockPackageMonitor extends PackageMonitor {
        private final AppLockLocalService mService;

        AppLockPackageMonitor(AppLockLocalService service) {
            mService = service;
        }

        @Override
        public void onPackageAppLockEnabled(String packageName) {
            super.onPackageAppLockEnabled(packageName);
            if (DEBUG) {
                Slog.d(TAG, "onPackageAppLockEnabled for " + packageName);
            }
            final int userId = getChangingUserId();

            mService.handleAppLockEnabled(packageName, userId);
        }

        @Override
        public void onPackageAppLockDisabled(String packageName) {
            super.onPackageAppLockDisabled(packageName);
            if (DEBUG) {
                Slog.d(TAG, "onPackageAppLockDisabled for " + packageName);
            }

            updateMapAndListenersPackageNoLongerAppLockEnabled(packageName);
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            super.onPackageRemoved(packageName, uid);
            if (DEBUG) {
                Slog.d(TAG, "onPackageRemoved for " + packageName);
            }

            updateMapAndListenersPackageNoLongerAppLockEnabled(packageName);
        }

        private void updateMapAndListenersPackageNoLongerAppLockEnabled(String packageName) {
            final int userId = getChangingUserId();

            mService.mInjector.getHandler().post(() -> {
                synchronized (mService.mLock) {
                    final ArrayMap<String, AppLockLockedState> map =
                            mService.mAppLockLockedStatesForUser.get(userId);
                    if (map == null || !map.containsKey(packageName)) {
                        return;
                    }
                    for (int i = 0; i < mService.mPackageLockedStateListeners.size(); i++) {
                        mService.mPackageLockedStateListeners.get(i).onPackageLockedStateChanged(
                                packageName, userId, false);
                    }
                    mService.cancelPackageAppLockedJobLocked(packageName, userId);
                    map.remove(packageName);
                    if (map.isEmpty()) {
                        mService.mAppLockLockedStatesForUser.remove(userId);
                    }
                }
            });
        }
    }

    /**
     * Default implementation of {@link Injector}.
     */
    private static final class InjectorImpl implements Injector {
        final ActivityManagerService mAms;

        InjectorImpl(ActivityManagerService service) {
            this.mAms = service;
        }

        @Override
        public Handler getHandler() {
            return BackgroundThread.getHandler();
        }
    }
}
