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

package com.android.server.privatecompute;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.app.privatecompute.IPccService;
import android.app.privatecompute.IResultCallback;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.ParcelableException;
import android.os.Process;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerService;
import com.android.server.pm.pkg.AndroidPackage;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
/**
 * Local interface for the PccSandboxManager that is used by other system services to interact with
 * the PCC sandbox.
 *
 * @hide Only for use within system_server.
 */
public final class PccSandboxManagerInternal implements OnRoleHoldersChangedListener {
    private static final String TAG = "PccSandboxManagerInternal";

    private final PackageManagerInternal mPackageManagerInternal;
    private final ExecutorService mExecutor;
    private final Future<?> mPopulateTrustedAndAllowedPackagesFuture;

    @VisibleForTesting
    @GuardedBy("mLock")
    final Set<String> mPccTrustedPackages = new ArraySet<>();

    @VisibleForTesting
    @GuardedBy("mLock")
    final SparseArray<Set<String>> mPccAllowedPackages = new SparseArray<>();

    @GuardedBy("mLock")
    final Set<String> mPccAllowedPackagesForTesting = new ArraySet<>();

    @VisibleForTesting
    static final int[] TRUSTED_UIDS = new int[] {
            Process.BLUETOOTH_UID,
            Process.SYSTEM_UID,
            Process.PHONE_UID
    };

    // Only packages that hold one or more of these roles are allowed to have
    // PCC components.
    private static final List<String> ALLOWED_ROLES = Arrays.asList(
            "android.app.role.ASSISTANT",
            "android.app.role.SYSTEM_AMBIENT_AUDIO_INTELLIGENCE",
            "android.app.role.SYSTEM_APP_PROTECTION_SERVICE",
            "android.app.role.SYSTEM_AUDIO_INTELLIGENCE",
            "android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE",
            "android.app.role.SYSTEM_TEXT_INTELLIGENCE",
            "android.app.role.SYSTEM_VISUAL_INTELLIGENCE",
            "android.app.role.SYSTEM_VENDOR_INTELLIGENCE",
            "android.app.role.SYSTEM_UI_INTELLIGENCE"
    );

    private final Context mContext;
    private final PccSandboxManagerServiceImpl mPccSandboxManagerService;
    private final Object mLock = new Object();

    @VisibleForTesting
    @GuardedBy("mLock")
    final Map<IBinder, PccServiceInfo> mPccServiceConnections = new ArrayMap<>();

    public PccSandboxManagerInternal(
            Context context, PccSandboxManagerServiceImpl pccSandboxManagerService) {
        mContext = context;
        mPccSandboxManagerService = pccSandboxManagerService;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mExecutor = pccSandboxManagerService.getExecutorService();
        mPopulateTrustedAndAllowedPackagesFuture = mExecutor.submit(() -> {
            populatePccTrustedPackages();
            populatePccAllowedPackages();
        });
        mPackageManagerInternal.getPackageList(new PackageReceiver());

        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        if (roleManager != null) {
            roleManager.addOnRoleHoldersChangedListenerAsUser(mExecutor, this, UserHandle.ALL);
        }
    }

    /**
     * Waits for the list of trusted and allowed PCC packages to be populated.
     */
    void awaitPccInitialization() {
        try {
            mPopulateTrustedAndAllowedPackagesFuture.get();
        } catch (Exception e) {
            Slog.e(TAG, "Error populating PCC packages", e);
        }
    }

    @VisibleForTesting
    void populatePccTrustedPackages() {
        String settingsIntelligencePackage = mContext.getString(
                com.android.internal.R.string.config_systemSettingsIntelligence);
        String systemUiPackage = mContext.getString(
                com.android.internal.R.string.config_systemUi);
        String recentsUiComponent = mContext.getString(
                com.android.internal.R.string.config_recentsComponentName);
        String recentsUiPackage = null;
        if (recentsUiComponent != null && !recentsUiComponent.isEmpty()) {
            ComponentName cn = ComponentName.unflattenFromString(recentsUiComponent);
            if (cn != null) {
                recentsUiPackage = cn.getPackageName();
            }
        }

        String mediaStorePackage = resolveProviderPackageName(
                android.provider.MediaStore.AUTHORITY);
        String telephonyPackage = resolveProviderPackageName("telephony");
        String contactsPackage = resolveProviderPackageName(
                android.provider.ContactsContract.AUTHORITY);
        String calendarPackage = resolveProviderPackageName(
                android.provider.CalendarContract.AUTHORITY);
        String downloadsPackage = resolveProviderPackageName(
                android.provider.Downloads.Impl.AUTHORITY);
        String externalStoragePackage = resolveProviderPackageName(
                android.provider.DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY);
        String settingsPackage = resolveProviderPackageName(
                android.provider.Settings.AUTHORITY);
        String blockedNumberPackage = resolveProviderPackageName(
                android.provider.BlockedNumberContract.AUTHORITY);

        Intent setupWizardIntent = new Intent(Intent.ACTION_MAIN);
        setupWizardIntent.addCategory(Intent.CATEGORY_SETUP_WIZARD);
        List<ResolveInfo> setupMatches = mContext.getPackageManager().queryIntentActivities(
                setupWizardIntent,
                PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);

        synchronized (mLock) {
            if (settingsIntelligencePackage != null && !settingsIntelligencePackage.isEmpty()) {
                mPccTrustedPackages.add(settingsIntelligencePackage);
            }

            if (systemUiPackage != null && !systemUiPackage.isEmpty()) {
                mPccTrustedPackages.add(systemUiPackage);
            }

            if (recentsUiPackage != null && !recentsUiPackage.isEmpty()) {
                mPccTrustedPackages.add(recentsUiPackage);
            }

            if (mediaStorePackage != null) {
                mPccTrustedPackages.add(mediaStorePackage);
            }
            if (telephonyPackage != null) {
                mPccTrustedPackages.add(telephonyPackage);
            }
            if (contactsPackage != null) {
                mPccTrustedPackages.add(contactsPackage);
            }
            if (calendarPackage != null) {
                mPccTrustedPackages.add(calendarPackage);
            }
            if (downloadsPackage != null) {
                mPccTrustedPackages.add(downloadsPackage);
            }
            if (externalStoragePackage != null) {
                mPccTrustedPackages.add(externalStoragePackage);
            }
            if (settingsPackage != null) {
                mPccTrustedPackages.add(settingsPackage);
            }
            if (blockedNumberPackage != null) {
                mPccTrustedPackages.add(blockedNumberPackage);
            }
            for (ResolveInfo info : setupMatches) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    mPccTrustedPackages.add(info.activityInfo.packageName);
                }
            }

            Slog.d(TAG, "Trusted PCC Packages: " + mPccTrustedPackages);
        }
    }

    @VisibleForTesting
    @SuppressLint("AndroidFrameworkRequiresPermission")
    void populatePccAllowedPackages() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userManager != null) {
            List<UserHandle> users = userManager.getUserHandles(true);
            for (UserHandle user : users) {
                updateAllowedPackagesForUser(user.getIdentifier());
            }
        }
    }

    @VisibleForTesting
    void updateAllowedPackagesForUser(int userId) {
        String defaultOnDeviceIntelligenceServicePackage = mContext.getString(
                com.android.internal.R.string.config_defaultOnDeviceIntelligenceService);
        String defaultOnDeviceIntelligencePackageName = null;
        if (defaultOnDeviceIntelligenceServicePackage != null
                && !defaultOnDeviceIntelligenceServicePackage.isEmpty()) {
            ComponentName componentName = ComponentName.unflattenFromString(
                    defaultOnDeviceIntelligenceServicePackage);
            if (componentName != null) {
                defaultOnDeviceIntelligencePackageName = componentName.getPackageName();
            }
        }

        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        Set<String> rolePackages = new ArraySet<>();
        if (roleManager != null) {
            UserHandle user = UserHandle.of(userId);
            for (String role : ALLOWED_ROLES) {
                try {
                    List<String> holders = roleManager.getRoleHoldersAsUser(role, user);
                    if (holders != null) {
                        rolePackages.addAll(holders);
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Error fetching role holders for role: " + role + " user: "
                            + userId, e);
                }
            }
        }

        if (defaultOnDeviceIntelligencePackageName != null) {
            rolePackages.add(defaultOnDeviceIntelligencePackageName);
        }

        synchronized (mLock) {
            mPccAllowedPackages.put(userId, rolePackages);
            Slog.d(TAG, "Updated allowed PCC Packages for user " + userId + ": " + rolePackages);
        }
    }

    @Nullable
    private String resolveProviderPackageName(String authority) {
        final PackageManager pm = mContext.getPackageManager();
        int flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_SYSTEM_ONLY;
        ProviderInfo providerInfo = pm.resolveContentProvider(authority, flags);
        return providerInfo != null ? providerInfo.packageName : null;
    }

    /**
     * Returns true if the app is considered a "Trusted System Component" by the framework.
     * This includes:
     * <ul>
     *     <li>Trusted System UIDs (System, Bluetooth, Phone)</li>
     *     <li>Private Compute Services (PCS) packages (which extend the framework trust boundary)
     *     </li>
     *     <li>Explicitly allowlisted system packages</li>
     * </ul>
     *
     * @param appUid The UID of the application.
     * @param appPackage The package name of the application.
     */
    public boolean isPccTrustedSystemComponent(int appUid, String appPackage) {
        for (int uid : TRUSTED_UIDS) {
            if (UserHandle.isSameApp(appUid, uid)) {
                return true;
            }
        }

        if (appPackage != null) {
            // Verify that the provided package name belongs to the provided UID.
            if (!mPackageManagerInternal.isSameApp(appPackage, appUid,
                    UserHandle.getUserId(appUid))) {
                return false;
            }
        }

        // PCS applications are trusted.
        if (isPrivateComputeServicesUid(appUid)) {
            return true;
        }

        if (appPackage != null) {
            synchronized (mLock) {
                if (mPccTrustedPackages.contains(appPackage)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given UID belongs to a package that provides Private Compute
     * services.
     */
    public boolean isPrivateComputeServicesUid(int uid) {
        return mPccSandboxManagerService.isPrivateComputeServicesUid(uid);
    }

    /**
     * Returns {@code true} if the given package is allowed to run in a PCC process for the given
     * user.
     */
    public boolean isPccAllowedPackage(String packageName, int userId) {
        synchronized (mLock) {
            Set<String> userAllowedPackages = mPccAllowedPackages.get(userId);
            return (userAllowedPackages != null && userAllowedPackages.contains(packageName))
                    || mPccAllowedPackagesForTesting.contains(packageName);
        }
    }

    @VisibleForTesting
    void addTestAllowedPackage(String packageName) {
        synchronized (mLock) {
            mPccAllowedPackagesForTesting.add(packageName);
        }
    }

    @VisibleForTesting
    void removeTestAllowedPackage(String packageName) {
        synchronized (mLock) {
            mPccAllowedPackagesForTesting.remove(packageName);
        }
    }

    /**
     * Called when a new client binds to a PCC service.
     *
     * <p>This method tracks the new client connection. The client is untrusted and external to the
     * PCC sandbox, hence we return a wrapped {@link PccServiceProxy} binder that sanitizes the
     * input data.
     *
     * @param name      The ComponentName of the PCC service.
     * @param userId    The user ID of the client process.
     * @param intent    The Intent used to bind to the service.
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     * @return one of the following:
     * <ul>
     *     <li>The original binder if the client is trusted</li>
     *     <li>The wrapped {@link PccServiceProxy} binder if the client is untrusted and the
     *     binder extends {@link android.app.privatecompute.IPccService}</li>
     *     <li>null if the service doesn't extend {@link android.app.privatecompute.IPccService}
     *     </li>
     * </ul>
     */
    public IBinder createPccProxyIfNeeded(ComponentName name, int userId, Intent intent,
            IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return binder;
        }

        binder = validatePccServiceBinder(binder);
        if (binder == null) {
            return null;
        }

        synchronized (mLock) {
            PccServiceConnectionInfo newConnectionInfo = new PccServiceConnectionInfo(name, userId,
                    intent);
            PccServiceInfo pccServiceInfo = mPccServiceConnections.get(binder);
            if (pccServiceInfo == null) {
                PccServiceProxy proxyBinder = new PccServiceProxy(binder);
                DeathRecipient deathRecipient = new DeathRecipient(binder);
                try {
                    binder.linkToDeath(deathRecipient, 0);
                    pccServiceInfo = new PccServiceInfo(proxyBinder, deathRecipient);
                    pccServiceInfo.mConnectionInfos.add(newConnectionInfo);
                    mPccServiceConnections.put(binder, pccServiceInfo);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to link to death recipient, service has died: " + binder,
                            e);
                    proxyBinder.destroy();
                    return null;
                }
            } else {
                pccServiceInfo.mConnectionInfos.add(newConnectionInfo);
            }

            return pccServiceInfo.getWrappedBinder();
        }
    }

    /**
     * Retrieves the existing PCC proxy binder associated with the given raw service binder.
     *
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     * @return one of the following:
     * <ul>
     *     <li>The original binder if the client is trusted</li>
     *     <li>The wrapped {@link PccServiceProxy} binder if the client is untrusted and proxy
     *     connection exists.</li>
     *     <li>null if the proxy connection doesn't exist.</li>
     * </ul>
     */
    public IBinder fetchPccProxyIfNeeded(IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return binder;
        }

        synchronized (mLock) {
            PccServiceInfo pccServiceInfo = mPccServiceConnections.get(binder);
            if (pccServiceInfo != null) {
                return pccServiceInfo.getWrappedBinder();
            }
            return null;
        }
    }

    /**
     * Called when a client unbinds from a PCC service.
     *
     * <p>This method removes the client from the connection records. If it's the last client for a
     * given PCC service, it cleans up the associated resources, including destroying the proxy
     * binder to release binder references.
     *
     * @param name      The ComponentName of the PCC service.
     * @param userId    The user ID of the client process.
     * @param intent    The Intent used to bind to the service.
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     */
    public void removePccProxyIfNeeded(ComponentName name, int userId, Intent intent,
            IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return;
        }

        synchronized (mLock) {
            if (!mPccServiceConnections.containsKey(binder)) {
                Slog.w(TAG, "Cannot find PCC connection for the binder: " + binder);
                return;
            }

            PccServiceInfo serviceInfo = mPccServiceConnections.get(binder);
            PccServiceConnectionInfo connectionInfo = new PccServiceConnectionInfo(name, userId,
                    intent);
            serviceInfo.mConnectionInfos.remove(connectionInfo);

            if (serviceInfo.mConnectionInfos.isEmpty()) {
                mPccServiceConnections.remove(binder);
                binder.unlinkToDeath(serviceInfo.mDeathRecipient, 0);
                serviceInfo.getWrappedBinder().destroy();
            }
        }
    }

    private IBinder validatePccServiceBinder(IBinder binder) {
        try {
            if (binder == null || !IPccService.DESCRIPTOR.equals(binder.getInterfaceDescriptor())) {
                return null;
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get interface descriptor for binder: " + binder, e);
            return null;
        }
        return binder;
    }

    private boolean isTrustedClient(int clientUid) {
        if (Process.isPrivateComputeCoreUid(clientUid)) {
            return true;
        }
        AndroidPackage androidPackage = mPackageManagerInternal.getPackage(clientUid);
        if (androidPackage != null) {
            return isPccTrustedSystemComponent(clientUid, androidPackage.getPackageName());
        }
        return isPccTrustedSystemComponent(clientUid, null);
    }

    /**
     * Returns true if the package {@code callerPackage} running under user
     * handle {@code callerUid} is allowed association with the package
     * {@code targetPackage} running under user handle {@code targetUid}.
     * {@code extras} are checked for some associations, to ensure one-way
     * information flow into the PCC sandbox.
     */
    public boolean validateAssociationAllowed(
            int callerUid, String callerPackage, int targetUid, String targetPackage,
            @ActivityManagerService.AssociationType int associationType, @Nullable Bundle extras) {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                "PccSandboxManagerInternal#validateAssociationAllowed");
        Slog.d(TAG, "AllowAssociation validation for callerUid :" + callerUid
                + " and callerPackage :" + callerPackage);
        try {
            // Self-association is allowed.
            if (callerUid == targetUid) {
                return true;
            }

            final boolean callerIsPcc = Process.isPrivateComputeCoreUid(callerUid);
            final boolean targetIsPcc = Process.isPrivateComputeCoreUid(targetUid);
            // PCC to PCC association is allowed.
            if (callerIsPcc && targetIsPcc) {
                return true;
            }

            // Allow some non-PCC to PCC association, with one-way data flow enforced.
            if (!callerIsPcc && targetIsPcc) {
                switch (associationType) {
                    case ActivityManagerService.ASSOCIATION_TYPE_PROVIDER -> {
                        // ContentProvider association from regular to pcc components is disallowed
                        // because it can be used to egress sensitive data.
                        return isPccTrustedSystemComponent(callerUid, callerPackage);
                    }
                    case ActivityManagerService.ASSOCIATION_TYPE_RECEIVER,
                         ActivityManagerService.ASSOCIATION_TYPE_SERVICE -> {
                        try {
                            if (!isPccTrustedSystemComponent(callerUid, callerPackage)) {
                                PccBundleSanitizationUtil.sanitizeBundle(extras);
                            }
                            return true;
                        } catch (IllegalArgumentException e) {
                            Slog.e(TAG, "Intent extras have disallowed data types", e);
                            return false;
                        }
                    }
                    default -> {
                        // Should not be reached.
                        return false;
                    }
                }
            }

            // Since this method is only called if either caller or target is PCC,
            // if we're here, the caller is a PCC UID and the target is not.
            // Allow PCC to trusted component association.
            if (isPccTrustedSystemComponent(targetUid, targetPackage)) {
                return true;
            }

            return false;
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }


    @Override
    public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
        if (ALLOWED_ROLES.contains(roleName)) {
            Slog.i(TAG, "Role " + roleName + " changed, refreshing allowed packages for user "
                    + user.getIdentifier());
            updateAllowedPackagesForUser(user.getIdentifier());
        }
    }

    private final class PackageReceiver implements PackageManagerInternal.PackageListObserver {
        @Override
        public void onPackageAdded(@NonNull String packageName, int uid) {
            logWhenPccNotAllowed(packageName, uid);
        }

        @Override
        public void onPackageChanged(@NonNull String packageName, int uid) {
            logWhenPccNotAllowed(packageName, uid);
        }

        private void logWhenPccNotAllowed(@NonNull String packageName, int uid) {
            int userId = UserHandle.getUserId(uid);
            ApplicationInfo appInfo = mPackageManagerInternal.getApplicationInfo(
                    packageName, 0, Process.SYSTEM_UID, userId);
            if (appInfo != null && Process.isPrivateComputeCoreUid(appInfo.pccUid)) {
                if (!isPccAllowedPackage(packageName, userId)) {
                    Slog.w(TAG, "New/Updated package " + packageName + " (pccUid " + appInfo.pccUid
                            + ") provides PCC components but is currently not allowed to "
                            + "start any.");
                }
            }
        }
    }

    @VisibleForTesting
    final class PccServiceProxy extends IPccService.Stub {
        private volatile IBinder mRealBinder;

        PccServiceProxy(IBinder realBinder) {
            this.mRealBinder = realBinder;
        }

        @RequiresNoPermission
        @Override
        public void sendData(Bundle data, String packageName, IResultCallback callback) {
            Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                    "PccSandboxManagerInternal.PccServiceProxy#sendData()");
            try {
                if (mRealBinder == null) {
                    callback.onFailure(new ParcelableException(
                            new IllegalStateException("PCC service is already closed.")));
                    return;
                }

                IPccService realService = IPccService.Stub.asInterface(mRealBinder);

                final int callingUid = Binder.getCallingUid();

                if (mPackageManagerInternal.isSameApp(packageName, callingUid,
                        UserHandle.getUserId(callingUid))) {
                    try {
                        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                                "PccBundleSanitizationUtil#sanitizeBundle()");
                        try {
                            PccBundleSanitizationUtil.sanitizeBundle(data);
                        } finally {
                            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
                        }

                        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER,
                                "PccSandboxManagerInternal.realService#sendData()");
                        try {
                            realService.sendData(data, packageName, null);
                        } finally {
                            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
                        }
                    } catch (RemoteException | IllegalArgumentException e) {
                        callback.onFailure(new ParcelableException(e));
                        return;
                    } finally {
                        closeBundleResources(data);
                    }

                    callback.onSuccess();
                } else {
                    callback.onFailure(new ParcelableException(new SecurityException(
                            "Calling UID: " + callingUid + " is not associated with package: "
                                    + packageName)));
                }

            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke " + IResultCallback.class.getSimpleName()
                        + " for client: " + packageName, e);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
            }
        }

        /**
         * A cleanup method to clear the reference to the real binder once the service is brought
         * down. This is to reduce the memory footprint.
         */
        public void destroy() {
            mRealBinder = null;
        }

        @VisibleForTesting
        IBinder getRealBinder() {
            return mRealBinder;
        }
    }

    private static void closeBundleResources(Bundle bundle) {
        if (bundle == null || !bundle.hasFileDescriptors()) {
            return;
        }

        for (String key : bundle.keySet()) {
            Object value;
            try {
                value = bundle.get(key);
                if (value == null) {
                    continue;
                }
            } catch (BadParcelableException e) {
                continue;
            }

            switch (value) {
                case Closeable closeable -> {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        Slog.e(TAG, "Failed to close resource for key: " + key, e);
                    }
                }
                case Parcelable[] parcelables -> {
                    for (Parcelable p : parcelables) {
                        if (p instanceof Bundle b) {
                            closeBundleResources(b);
                        }
                    }
                }
                case Bundle subBundle -> closeBundleResources(subBundle);
                default -> {
                }
            }
        }
    }


    private record PccServiceConnectionInfo(ComponentName name, int userId,
                                            Intent.FilterComparison intentFilter) {
        PccServiceConnectionInfo(ComponentName name, int userId, Intent intent) {
            this(name, userId, new Intent.FilterComparison(intent));
        }
    }

    @VisibleForTesting
    static final class PccServiceInfo {
        private final List<PccServiceConnectionInfo> mConnectionInfos;
        private final PccServiceProxy mProxy;
        private final IBinder.DeathRecipient mDeathRecipient;

        PccServiceInfo(PccServiceProxy proxy, IBinder.DeathRecipient deathRecipient) {
            this.mProxy = proxy;
            this.mConnectionInfos = new ArrayList<>(1);
            this.mDeathRecipient = deathRecipient;
        }

        public PccServiceProxy getWrappedBinder() {
            return mProxy;
        }

        @VisibleForTesting
        int getConnectionCount() {
            return mConnectionInfos.size();
        }

        @VisibleForTesting
        IBinder.DeathRecipient getDeathRecipient() {
            return mDeathRecipient;
        }

    }

    private final class DeathRecipient implements IBinder.DeathRecipient {
        private final IBinder mRealBinder;

        DeathRecipient(IBinder realBinder) {
            this.mRealBinder = realBinder;
        }

        @Override
        public void binderDied() {
            synchronized (mLock) {
                PccServiceInfo serviceInfo = mPccServiceConnections.remove(mRealBinder);
                if (serviceInfo != null) {
                    serviceInfo.getWrappedBinder().destroy();
                }
            }
        }
    }

}
