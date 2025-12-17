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

package com.android.server.devicepolicy;

import static android.app.admin.DevicePolicyManager.DEVICE_OWNER;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.admin.DeviceAdminInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.SparseArray;
import androidx.annotation.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.utils.Slogf;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class DeviceAdmins {

    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    private static final Set<Integer> DA_DISALLOWED_POLICIES;

    static {
        DA_DISALLOWED_POLICIES = new ArraySet<>();
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD);
        DA_DISALLOWED_POLICIES.add(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
    }

    interface Delegate {
        void initializePolicyDataFromXml(int userHandle, @NonNull DevicePolicyData policy);

        int getTargetSdk(String packageName, int userId);
    }

    final Injector mInjector;
    final Owners mOwners;

    @VisibleForTesting final SparseArray<DevicePolicyData> mUserData = new SparseArray<>();
    final UserManager mUserManager;
    final Delegate mDelegate;
    final Lock mLock;

    DeviceAdmins(
            Lock lock,
            Injector injector,
            Owners owners,
            Delegate delegate) {
        mLock = lock;
        mInjector = injector;
        mOwners = owners;
        mUserManager = injector.getUserManager();
        mDelegate = delegate;
    }

    @Nullable
    ActiveAdmin getDeviceOwnerAdmin() {
        synchronized (mLock.getLockObject()) {
            ComponentName component = mOwners.getDeviceOwnerComponent();
            if (component == null) {
                return null;
            }

            DevicePolicyData policy = getUserData(mOwners.getDeviceOwnerUserId());
            final int n = policy.mAdminList.size();
            for (int i = 0; i < n; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (component.equals(admin.info.getComponent())) {
                    return admin;
                }
            }
            Slogf.wtf(LOG_TAG, "Active admin for device owner not found. component=" + component);
            return null;
        }
    }

    @Nullable
    ActiveAdmin getDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ComponentName doComponent = mOwners.getDeviceOwnerComponent();
            ActiveAdmin doAdmin = getUserData(userId).mAdminMap.get(doComponent);
            return doAdmin;
        }
    }

    /**
     * @deprecated Use the version which does not take a user id.
     */
    @Deprecated
    @Nullable
    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getDeviceOwnerAdmin();
            if (admin == null) {
                admin = getProfileOwnerOfOrganizationOwnedDevice(userId);
            }
            return admin;
        }
    }

    @Nullable
    ActiveAdmin getDeviceOwnerOrProfileOwnerOfOrganizationOwnedDevice() {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getDeviceOwnerAdmin();
            if (admin == null) {
                admin = getProfileOwnerOfOrganizationOwnedDevice();
            }
            return admin;
        }
    }

    /** Returns the ActiveAdmin associated with the PO or DO on the given user. */
    @Nullable
    ActiveAdmin getDeviceOrProfileOwnerAdmin(int userHandle) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin admin = getProfileOwnerAdmin(userHandle);
            if (admin == null && getDeviceOwnerUserIdUnchecked() == userHandle) {
                admin = getDeviceOwnerAdmin();
            }
            return admin;
        }
    }

    @Nullable
    private ActiveAdmin getProfileOwnerOfOrganizationOwnedDevice(int userHandle) {
        synchronized (mLock.getLockObject()) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        for (UserInfo userInfo : mUserManager.getProfiles(userHandle)) {
                            if (userInfo.isManagedProfile()) {
                                if (getProfileOwnerAsUser(userInfo.id) != null
                                        && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                                    ComponentName who = getProfileOwnerAsUser(userInfo.id);
                                    return getActiveAdminUnchecked(who, userInfo.id);
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    @Nullable
    ActiveAdmin getProfileOwnerOfOrganizationOwnedDevice() {
        synchronized (mLock.getLockObject()) {
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        for (UserInfo userInfo : mUserManager.getUsers()) {
                            if (userInfo.isManagedProfile()) {
                                if (getProfileOwnerAsUser(userInfo.id) != null
                                        && isProfileOwnerOfOrganizationOwnedDevice(userInfo.id)) {
                                    ComponentName who = getProfileOwnerAsUser(userInfo.id);
                                    return getActiveAdminUnchecked(who, userInfo.id);
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    // Returns the active profile owner for this user or null if the current user has no
    // profile owner.
    @Nullable
    ActiveAdmin getProfileOwnerAdmin(int userHandle) {
        synchronized (mLock.getLockObject()) {
            ComponentName profileOwner = mOwners.getProfileOwnerComponent(userHandle);
            if (profileOwner == null) {
                return null;
            }
            DevicePolicyData policy = getUserData(userHandle);
            final int n = policy.mAdminList.size();
            for (int i = 0; i < n; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (profileOwner.equals(admin.info.getComponent())) {
                    return admin;
                }
            }
            return null;
        }
    }

    @Nullable
    ActiveAdmin getDefaultDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ComponentName doComponent = mOwners.getDeviceOwnerComponent();
            if (mOwners.getDeviceOwnerType(doComponent.getPackageName()) == DEVICE_OWNER) {
                ActiveAdmin doAdmin = getUserData(userId).mAdminMap.get(doComponent);
                return doAdmin;
            }
            return null;
        }
    }

    @Nullable
    ActiveAdmin getProfileOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);
            ActiveAdmin poAdmin = getUserData(userId).mAdminMap.get(poAdminComponent);
            return poAdmin;
        }
    }

    @Nullable
    ActiveAdmin getProfileOwnerOrDeviceOwner(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            // Try to find an admin which can use reqPolicy
            final ComponentName poAdminComponent = mOwners.getProfileOwnerComponent(userId);

            if (poAdminComponent != null) {
                return getProfileOwner(userId);
            }

            return getDeviceOwner(userId);
        }
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(CallerIdentity caller) {
        return getActiveAdminUnchecked(
                caller.getComponentName(), caller.getUserId(), caller.getPackageName());
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle) {
        return getActiveAdminUnchecked(who, userHandle, /* packageName= */ null);
    }

    @Nullable
    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle, String packageName) {
        synchronized (mLock.getLockObject()) {
            final DevicePolicyData policy = getUserData(userHandle);

            // Find ActiveAdmin by component name.
            ActiveAdmin componentAdmin = (who != null) ? policy.mAdminMap.get(who) : null;
            if (componentAdmin != null) {
                ActivityInfo activityInfo = componentAdmin.info.getActivityInfo();
                if (who.getPackageName().equals(activityInfo.packageName)
                        && who.getClassName().equals(activityInfo.name)) {
                    return componentAdmin;
                }
            }

            // Find ActiveAdmin by package name. There must be only one.
            if (packageName != null) {
                List<ActiveAdmin> packageAdmins = new ArrayList<>();
                for (ActiveAdmin admin : policy.mAdminList) {
                    if (admin.info.getPackageName().equals(packageName)) {
                        packageAdmins.add(admin);
                    }
                }
                Preconditions.checkState(
                        packageAdmins.size() == 1,
                        String.format(
                                "There must be exactly one ActiveAdmin for specified package %s",
                                packageName));
                return packageAdmins.get(0);
            }

            return null;
        }
    }

    ActiveAdmin getActiveAdminUnchecked(ComponentName who, int userHandle, boolean parent) {
        synchronized (mLock.getLockObject()) {
            if (parent) {
                Preconditions.checkCallAuthorization(isManagedProfile(userHandle));
            }
            ActiveAdmin admin = getActiveAdminUnchecked(who, userHandle);
            if (parent && admin != null) {
                return admin.getParentActiveAdmin();
            }
            return admin;
        }
    }

    @Nullable
    ActiveAdmin getActiveAdmin(CallerIdentity caller) {
        synchronized (mLock.getLockObject()) {
            if (caller.getComponentName() != null) {
                return getActiveAdminUnchecked(caller.getComponentName(), caller.getUserId());
            }
            return mInjector.binderWithCleanCallingIdentity(
                    () -> {
                        List<ComponentName> activeAdmins = getActiveAdmins(caller.getUserId());
                        if (activeAdmins != null) {
                            for (ComponentName admin : activeAdmins) {
                                if (admin.getPackageName().equals(caller.getPackageName())) {
                                    return getActiveAdminUnchecked(admin, caller.getUserId());
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    /**
     * Find the admin for the component and userId bit of the uid, then check the admin's uid
     * matches the uid.
     */
    @Nullable
    ActiveAdmin getActiveAdminForUid(ComponentName who, int uid) {
        synchronized (mLock.getLockObject()) {
            final int userId = UserHandle.getUserId(uid);
            final DevicePolicyData policy = getUserData(userId);
            ActiveAdmin admin = policy.mAdminMap.get(who);
            if (admin == null) {
                throw new SecurityException("No active admin " + who + " for UID " + uid);
            }
            if (admin.getUid() != uid) {
                throw new SecurityException("Admin " + who + " is not owned by uid " + uid);
            }
            return admin;
        }
    }

    /**
     * Returns the active admin for the user of the caller as denoted by uid, which implements the
     * {@code reqPolicy}.
     *
     * <p>The {@code who} parameter is used as a hint: If provided, it must be the component name of
     * the active admin for that user and the caller uid must match the uid of the admin. If not
     * provided, iterate over all of the active admins in the DevicePolicyData for that user and
     * return the one with the uid specified as parameter, and has the policy specified.
     */
    @Nullable
    ActiveAdmin getActiveAdminWithPolicyForUid(ComponentName who, int reqPolicy, int uid) {
        synchronized (mLock.getLockObject()) {
            // Try to find an admin which can use reqPolicy
            final int userId = UserHandle.getUserId(uid);
            final DevicePolicyData policy = getUserData(userId);
            if (who != null) {
                ActiveAdmin admin = policy.mAdminMap.get(who);
                if (admin == null || admin.getUid() != uid) {
                    throw new SecurityException(
                            "Admin " + who + " is not active or not owned by uid " + uid);
                }
                if (isActiveAdminWithPolicyForUser(admin, reqPolicy, userId)) {
                    return admin;
                }
            } else {
                for (ActiveAdmin admin : policy.mAdminList) {
                    if (admin.getUid() == uid
                            && isActiveAdminWithPolicyForUser(admin, reqPolicy, userId)) {
                        return admin;
                    }
                }
            }

            return null;
        }
    }

    @Nullable
    ActiveAdmin getActiveAdminForCaller(CallerIdentity caller, int reqPolicy)
            throws SecurityException {
        return getActiveAdminOrCheckPermissionsForCaller(
                caller, reqPolicy, /* permissions= */ Set.of());
    }

    @Nullable
    ActiveAdmin getActiveAdminForCaller(CallerIdentity caller, int reqPolicy, boolean parent)
            throws SecurityException {
        if (parent) {
            Preconditions.checkCallingUser(isManagedProfile(caller.getUserId()));
        }
        ActiveAdmin admin =
                getActiveAdminOrCheckPermissionsForCaller(
                        caller, reqPolicy, /* permissions= */ Set.of());
        if (parent && admin != null) {
            return admin.getParentActiveAdmin();
        }
        return admin;
    }

    /**
     * Finds an active admin for the caller then checks {@code permissions} if admin check failed.
     *
     * @return an active admin or {@code null} if there is no active admin but one of {@code
     *     permissions} is granted
     * @throws SecurityException if caller neither has an active admin nor {@code permission}
     */
    @Nullable
    ActiveAdmin getActiveAdminOrCheckPermissionsForCaller(
            CallerIdentity caller, int reqPolicy, Set<String> permissions)
            throws SecurityException {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin result =
                    getActiveAdminWithPolicyForUid(
                            caller.getComponentName(), reqPolicy, caller.getUid());
            if (result != null) {
                return result;
            } else {
                for (String permission : permissions) {
                    if (hasPermission(caller, permission)) {
                        return null;
                    }
                }
            }

            // Code for handling failure from getActiveAdminWithPolicyForUid to find an admin
            // that satisfies the required policy.
            // Throws a security exception with the right error message.
            if (caller.hasAdminComponent()) {
                final DevicePolicyData policy = getUserData(caller.getUserId());
                ActiveAdmin admin = policy.mAdminMap.get(caller.getComponentName());
                final boolean isDeviceOwner =
                        isDeviceOwner(admin.info.getComponent(), caller.getUserId());
                final boolean isProfileOwner =
                        isProfileOwner(admin.info.getComponent(), caller.getUserId());

                if (DA_DISALLOWED_POLICIES.contains(reqPolicy)
                        && !isDeviceOwner
                        && !isProfileOwner) {
                    throw new SecurityException(
                            "Admin "
                                    + admin.info.getComponent()
                                    + " is not a device owner or profile owner, so may not use"
                                    + " policy: "
                                    + admin.info.getTagForPolicy(reqPolicy));
                }
                throw new SecurityException(
                        "Admin "
                                + admin.info.getComponent()
                                + " did not specify uses-policy for: "
                                + admin.info.getTagForPolicy(reqPolicy));
            } else {
                throw new SecurityException(
                        "No active admin owned by uid "
                                + caller.getUid()
                                + " for policy #"
                                + reqPolicy
                                + (permissions.isEmpty()
                                        ? ""
                                        : ", which doesn't have " + permissions));
            }
        }
    }

    ActiveAdmin getActiveAdminOrCheckPermissionForCaller(
            CallerIdentity caller, int reqPolicy, boolean parent, @NonNull String permission)
            throws SecurityException {
        if (parent) {
            Preconditions.checkCallAuthorization(isManagedProfile(caller.getUserId()));
        }
        ActiveAdmin admin =
                getActiveAdminOrCheckPermissionsForCaller(caller, reqPolicy, Set.of(permission));
        if (parent && admin != null) {
            return admin.getParentActiveAdmin();
        }
        return admin;
    }

    @NonNull
    List<ComponentName> getActiveAdmins(int userHandle) {
        synchronized (mLock.getLockObject()) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            if (N <= 0) {
                return null;
            }
            ArrayList<ComponentName> res = new ArrayList<ComponentName>(N);
            for (int i = 0; i < N; i++) {
                res.add(policy.mAdminList.get(i).info.getComponent());
            }
            return res;
        }
    }

    /**
     * Returns the most probable admin to have set a policy on the given {@code userId} according to
     * the following heuristics:
     *
     * <ul>
     *   <li>The device owner on the given userId
     *   <li>The profile owner on the given userId
     *   <li>The org owned profile owner of which the given userId is its parent
     *   <li>The profile owner of which the given userId is its parent
     *   <li>The device owner on any user
     *   <li>The profile owner on any user
     * </ul>
     */
    @Nullable
    // TODO(b/266928216): Check what the admin capabilities are when deciding which admin to return.
    ActiveAdmin getMostProbableDPCAdminForLocalPolicy(@UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            ActiveAdmin localDeviceOwner = getDeviceOwner(userId);
            if (localDeviceOwner != null) {
                return localDeviceOwner;
            }

            ActiveAdmin localProfileOwner = getProfileOwner(userId);
            if (localProfileOwner != null) {
                return localProfileOwner;
            }

            int[] profileIds = mUserManager.getProfileIds(userId, /* enabledOnly= */ false);
            for (int id : profileIds) {
                if (id == userId) {
                    continue;
                }
                if (isProfileOwnerOfOrganizationOwnedDevice(id)) {
                    return getProfileOwnerAdmin(id);
                }
            }

            for (int id : profileIds) {
                if (id == userId) {
                    continue;
                }
                if (isManagedProfile(id)) {
                    return getProfileOwnerAdmin(id);
                }
            }

            ActiveAdmin deviceOwner = getDeviceOwnerAdmin();
            if (deviceOwner != null) {
                return deviceOwner;
            }

            for (UserInfo userInfo : mUserManager.getUsers()) {
                ActiveAdmin profileOwner = getProfileOwner(userInfo.id);
                if (profileOwner != null) {
                    return profileOwner;
                }
            }
            return null;
        }
    }

    @VisibleForTesting
    boolean isActiveAdminWithPolicyForUser(
            ActiveAdmin admin, int reqPolicy, @UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            final boolean ownsDevice = isDeviceOwner(admin.info.getComponent(), userId);
            final boolean ownsProfile = isProfileOwner(admin.info.getComponent(), userId);

            boolean allowedToUsePolicy =
                    ownsDevice
                            || ownsProfile
                            || !DA_DISALLOWED_POLICIES.contains(reqPolicy)
                            || getTargetSdk(admin.info.getPackageName(), userId)
                                    < Build.VERSION_CODES.Q;
            return allowedToUsePolicy && admin.info.usesPolicy(reqPolicy);
        }
    }

    private @Nullable ComponentName getProfileOwnerAsUser(@UserIdInt int userId) {
        return mOwners.getProfileOwnerComponent(userId);
    }

    public boolean isProfileOwner(@Nullable ComponentName who, @UserIdInt int userId) {
        final ComponentName profileOwner =
                mInjector.binderWithCleanCallingIdentity(() -> getProfileOwnerAsUser(userId));
        return who != null && who.equals(profileOwner);
    }

    /**
     * Check if the user is a Device Owner
     *
     * @param who to check against
     * @param userId user to check
     * @return if the user is a Device Owner
     */
    public boolean isDeviceOwner(@Nullable ComponentName who, @UserIdInt int userId) {
        synchronized (mLock.getLockObject()) {
            return mOwners.hasDeviceOwner()
                    && mOwners.getDeviceOwnerUserId() == userId
                    && mOwners.getDeviceOwnerComponent().equals(who);
        }
    }

    /**
     * Returns the user id of the Device Owner, or {@code UserHandle.USER_NULL} if there is no
     * Device Owner.
     */
    public int getDeviceOwnerUserId() {
        synchronized (mLock.getLockObject()) {
            return mOwners.getDeviceOwnerUserId();
        }
    }

    private boolean isProfileOwnerOfOrganizationOwnedDevice(@UserIdInt int userId) {
        return mOwners.isProfileOwnerOfOrganizationOwnedDevice(userId);
    }

    private boolean isManagedProfile(@UserIdInt int userHandle) {
        final UserInfo user = getUserInfo(userHandle);
        return user != null && user.isManagedProfile();
    }

    private int getDeviceOwnerUserIdUnchecked() {
        return mOwners.hasDeviceOwner() ? mOwners.getDeviceOwnerUserId() : UserHandle.USER_NULL;
    }

    private UserInfo getUserInfo(@UserIdInt int userId) {
        return mInjector.binderWithCleanCallingIdentity(() -> mUserManager.getUserInfo(userId));
    }

    /**
     * Returns the policy data for the given user.
     *
     * <p>If the policy data is not loaded yet, this will load the policy data from XML.
     *
     * @param userHandle the user for whom to load the policy data
     * @return
     */
    @NonNull
    public DevicePolicyData getUserData(int userHandle) {
        synchronized (mLock.getLockObject()) {
            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy == null) {
                policy = new DevicePolicyData(userHandle);
                // `policy` must be inserted into `mUserData` before we call
                // `initializePolicyDataFromXml`, since the initialize code has
                //  side-effects which in return call `getUserData` again :(
                mUserData.append(userHandle, policy);
                mDelegate.initializePolicyDataFromXml(userHandle, policy);
            }
            return policy;
        }
    }

    /** Removes the in-memory policy data for the given user. */
    public void removeUserData(int userHandle) {
        synchronized (mLock.getLockObject()) {
            mUserData.remove(userHandle);
        }
    }

    /**
     * Returns the (first) user that has completed the setup, or {@code UserHandle.USER_NULL} if no
     * such user exists.
     */
    public int getUserWithSetupCompleted() {
        synchronized (mLock.getLockObject()) {
            for (int i = 0; i < mUserData.size(); i++) {
                int userId = mUserData.keyAt(i);
                if (mInjector.hasUserSetupCompleted(getUserData(userId))) {
                    return userId;
                }
            }
            return UserHandle.USER_NULL;
        }
    }

    /**
     * Get all users for which policy data is stored and which no longer exist.
     *
     * <p>This is needed in case the broadcast {@link Intent.ACTION_USER_REMOVED} was not handled
     * before reboot.
     */
    public Set<Integer> getDeletedUsers() {
        Set<Integer> usersWithProfileOwners;
        Set<Integer> usersWithData;
        synchronized (mLock.getLockObject()) {
            usersWithProfileOwners = mOwners.getProfileOwnerKeys();
            usersWithData = new ArraySet<>();
            for (int i = 0; i < mUserData.size(); i++) {
                usersWithData.add(mUserData.keyAt(i));
            }
        }
        List<UserInfo> allUsers = mUserManager.getUsers();

        Set<Integer> deletedUsers = new ArraySet<>();
        deletedUsers.addAll(usersWithProfileOwners);
        deletedUsers.addAll(usersWithData);
        for (UserInfo userInfo : allUsers) {
            deletedUsers.remove(userInfo.id);
        }
        return deletedUsers;
    }

    public void dumpPerUserPolicyData(IndentingPrintWriter pw) {
        synchronized (mLock.getLockObject()) {
            int userCount = mUserData.size();
            for (int i = 0; i < userCount; i++) {
                int userId = mUserData.keyAt(i);
                DevicePolicyData policy = getUserData(userId);
                policy.dump(pw);
                pw.println();
            }
        }
    }

    private int getTargetSdk(String packageName, @UserIdInt int userId) {
        return mDelegate.getTargetSdk(packageName, userId);
    }

    private boolean hasPermission(CallerIdentity caller, String permission) {
        return mInjector.hasPermission(permission, caller.getPid(), caller.getUid());
    }
}
