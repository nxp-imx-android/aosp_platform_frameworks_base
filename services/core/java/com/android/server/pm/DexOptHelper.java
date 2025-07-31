/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.content.pm.PackageManager.INSTALL_REASON_DEVICE_RESTORE;
import static android.content.pm.PackageManager.INSTALL_REASON_DEVICE_SETUP;
import static android.os.Trace.TRACE_TAG_DALVIK;
import static android.os.Trace.TRACE_TAG_PACKAGE_MANAGER;
import static android.os.incremental.IncrementalManager.isIncrementalPath;

import static com.android.server.LocalManagerRegistry.ManagerNotFoundException;
import static com.android.server.pm.ApexManager.ActiveApexInfo;
import static com.android.server.pm.PackageManagerService.DEBUG_DEXOPT;
import static com.android.server.pm.PackageManagerService.PLATFORM_PACKAGE_NAME;
import static com.android.server.pm.PackageManagerService.REASON_BOOT_AFTER_MAINLINE_UPDATE;
import static com.android.server.pm.PackageManagerService.REASON_BOOT_AFTER_OTA;
import static com.android.server.pm.PackageManagerService.REASON_CMDLINE;
import static com.android.server.pm.PackageManagerService.REASON_FIRST_BOOT;
import static com.android.server.pm.PackageManagerService.SCAN_AS_APEX;
import static com.android.server.pm.PackageManagerService.SCAN_AS_INSTANT_APP;
import static com.android.server.pm.PackageManagerService.TAG;
import static com.android.server.pm.PackageManagerServiceUtils.REMOVE_IF_APEX_PKG;
import static com.android.server.pm.PackageManagerServiceUtils.REMOVE_IF_NULL_PKG;
import static com.android.server.pm.PackageManagerServiceUtils.getPackageManagerLocal;

import static dalvik.system.DexFile.isProfileGuidedCompilerFilter;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppGlobals;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApexStagedEvent;
import android.content.pm.IPackageManagerNative;
import android.content.pm.IStagedApexObserver;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.jar.StrictJarFile;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalManagerRegistry;
import com.android.server.LocalServices;
import com.android.server.art.ArtManagerLocal;
import com.android.server.art.DexUseManagerLocal;
import com.android.server.art.ReasonMapping;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.pinner.PinnerService;
import com.android.server.pm.dex.InstallScenarioHelper;
import com.android.server.pm.dex.DexoptOptions;
import com.android.server.pm.local.PackageManagerLocalImpl;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageStateInternal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

/**
 * Helper class for dex optimization operations in PackageManagerService.
 */
public final class DexOptHelper {
    private static final long SEVEN_DAYS_IN_MILLISECONDS = 7 * 24 * 60 * 60 * 1000;

    @NonNull
    private static final ThreadPoolExecutor sDexoptExecutor =
            new ThreadPoolExecutor(1 /* corePoolSize */, 1 /* maximumPoolSize */,
                    60 /* keepAliveTime */, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private static boolean sArtManagerLocalIsInitialized = false;

    private final PackageManagerService mPm;
    private final InstallScenarioHelper mInstallScenarioHelper;

    // Start time for the boot dexopt in performPackageDexOptUpgradeIfNeeded when ART Service is
    // used, to make it available to the onDexoptDone callback.
    private volatile long mBootDexoptStartTime;

    static {
        // Recycle the thread if it's not used for `keepAliveTime`.
        sDexoptExecutor.allowsCoreThreadTimeOut();
    }

    DexOptHelper(PackageManagerService pm) {
        mPm = pm;
        mInstallScenarioHelper = new InstallScenarioHelper(mPm.mContext);
    }

    /**
     * Called during startup to do any boot time dexopting. This can occasionally be time consuming
     * (30+ seconds) and the function will block until it is complete.
     */
    public void performPackageDexOptUpgradeIfNeeded() {
        PackageManagerServiceUtils.enforceSystemOrRoot(
                "Only the system can request package update");

        int reason;
        if (mPm.isFirstBoot()) {
            reason = REASON_FIRST_BOOT; // First boot or factory reset.
        } else if (mPm.isDeviceUpgrading()) {
            reason = REASON_BOOT_AFTER_OTA;
        } else if (hasBcpApexesChanged()) {
            reason = REASON_BOOT_AFTER_MAINLINE_UPDATE;
        } else {
            return;
        }

        Log.i(TAG,
                "Starting boot dexopt for reason "
                        + DexoptOptions.convertToArtServiceDexoptReason(reason));

        final long startTime = System.nanoTime();

        mBootDexoptStartTime = startTime;
        getArtManagerLocal().onBoot(DexoptOptions.convertToArtServiceDexoptReason(reason),
                null /* progressCallbackExecutor */, null /* progressCallback */);
    }

    private void reportBootDexopt(long startTime, int numDexopted, int numSkipped, int numFailed) {
        final int elapsedTimeSeconds =
                (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);

        final Computer newSnapshot = mPm.snapshotComputer();

        MetricsLogger.histogram(mPm.mContext, "opt_dialog_num_dexopted", numDexopted);
        MetricsLogger.histogram(mPm.mContext, "opt_dialog_num_skipped", numSkipped);
        MetricsLogger.histogram(mPm.mContext, "opt_dialog_num_failed", numFailed);
        MetricsLogger.histogram(mPm.mContext, "opt_dialog_num_total",
                numDexopted + numSkipped + numFailed);
        MetricsLogger.histogram(mPm.mContext, "opt_dialog_time_s", elapsedTimeSeconds);
    }

     /**
     * Requests that files preopted on a secondary system partition be copied to the data partition
     * if possible.  Note that the actual copying of the files is accomplished by init for security
     * reasons. This simply requests that the copy takes place and awaits confirmation of its
     * completion. See platform/system/extras/cppreopt/ for the implementation of the actual copy.
     */
    public static void requestCopyPreoptedFiles() {
        final int WAIT_TIME_MS = 100;
        final String CP_PREOPT_PROPERTY = "sys.cppreopt";
        if (SystemProperties.getInt("ro.cp_system_other_odex", 0) == 1) {
            SystemProperties.set(CP_PREOPT_PROPERTY, "requested");
            // We will wait for up to 100 seconds.
            final long timeStart = SystemClock.uptimeMillis();
            final long timeEnd = timeStart + 100 * 1000;
            long timeNow = timeStart;
            while (!SystemProperties.get(CP_PREOPT_PROPERTY).equals("finished")) {
                try {
                    Thread.sleep(WAIT_TIME_MS);
                } catch (InterruptedException e) {
                    // Do nothing
                }
                timeNow = SystemClock.uptimeMillis();
                if (timeNow > timeEnd) {
                    SystemProperties.set(CP_PREOPT_PROPERTY, "timed-out");
                    Slog.wtf(TAG, "cppreopt did not finish!");
                    break;
                }
            }

            Slog.i(TAG, "cppreopts took " + (timeNow - timeStart) + " ms");
        }
    }

    /**
     * Dumps the dexopt state for the given package, or all packages if it is null.
     */
    public static void dumpDexoptState(
            @NonNull IndentingPrintWriter ipw, @Nullable String packageName) {
        try (PackageManagerLocal.FilteredSnapshot snapshot =
                        getPackageManagerLocal().withFilteredSnapshot()) {
            if (packageName != null) {
                try {
                    DexOptHelper.getArtManagerLocal().dumpPackage(ipw, snapshot, packageName);
                } catch (IllegalArgumentException e) {
                    // Package isn't found, but that should only happen due to race.
                    ipw.println(e);
                }
            } else {
                DexOptHelper.getArtManagerLocal().dump(ipw, snapshot);
            }
        }
    }

    /**
     * Returns the module names of the APEXes that contribute to bootclasspath.
     */
    private static List<String> getBcpApexes() {
        String bcp = System.getenv("BOOTCLASSPATH");
        if (TextUtils.isEmpty(bcp)) {
            Log.e(TAG, "Unable to get BOOTCLASSPATH");
            return List.of();
        }

        ArrayList<String> bcpApexes = new ArrayList<>();
        for (String pathStr : bcp.split(":")) {
            Path path = Paths.get(pathStr);
            // Check if the path is in the format of `/apex/<apex-module-name>/...` and extract the
            // apex module name from the path.
            if (path.getNameCount() >= 2 && path.getName(0).toString().equals("apex")) {
                bcpApexes.add(path.getName(1).toString());
            }
        }

        return bcpApexes;
    }

    /**
     * Returns true of any of the APEXes that contribute to bootclasspath has changed during this
     * boot.
     */
    private static boolean hasBcpApexesChanged() {
        Set<String> bcpApexes = new HashSet<>(getBcpApexes());
        ApexManager apexManager = ApexManager.getInstance();
        for (ActiveApexInfo apexInfo : apexManager.getActiveApexInfos()) {
            if (bcpApexes.contains(apexInfo.apexModuleName) && apexInfo.activeApexChanged) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@link DexUseManagerLocal} if ART Service should be used for package optimization.
     */
    public static @Nullable DexUseManagerLocal getDexUseManagerLocal() {
        try {
            return LocalManagerRegistry.getManagerOrThrow(DexUseManagerLocal.class);
        } catch (ManagerNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private class DexoptDoneHandler implements ArtManagerLocal.DexoptDoneCallback {
        /**
         * Called after every package dexopt operation done by {@link ArtManagerLocal} (when ART
         * Service is in use).
         */
        @Override
        public void onDexoptDone(@NonNull DexoptResult result) {
            switch (result.getReason()) {
                case ReasonMapping.REASON_FIRST_BOOT:
                case ReasonMapping.REASON_BOOT_AFTER_OTA:
                case ReasonMapping.REASON_BOOT_AFTER_MAINLINE_UPDATE:
                    int numDexopted = 0;
                    int numSkipped = 0;
                    int numFailed = 0;
                    for (DexoptResult.PackageDexoptResult pkgRes :
                            result.getPackageDexoptResults()) {
                        switch (pkgRes.getStatus()) {
                            case DexoptResult.DEXOPT_PERFORMED:
                                numDexopted += 1;
                                break;
                            case DexoptResult.DEXOPT_SKIPPED:
                                numSkipped += 1;
                                break;
                            case DexoptResult.DEXOPT_FAILED:
                                numFailed += 1;
                                break;
                        }
                    }

                    reportBootDexopt(mBootDexoptStartTime, numDexopted, numSkipped, numFailed);
                    break;
            }

            for (DexoptResult.PackageDexoptResult pkgRes : result.getPackageDexoptResults()) {
                CompilerStats.PackageStats stats =
                        mPm.getOrCreateCompilerPackageStats(pkgRes.getPackageName());
                for (DexoptResult.DexContainerFileDexoptResult dexRes :
                        pkgRes.getDexContainerFileDexoptResults()) {
                    stats.setCompileTime(
                            dexRes.getDexContainerFile(), dexRes.getDex2oatWallTimeMillis());
                }
            }

            synchronized (mPm.mLock) {
                mPm.mCompilerStats.maybeWriteAsync();
            }

            if (result.getReason().equals(ReasonMapping.REASON_INACTIVE)) {
                for (DexoptResult.PackageDexoptResult pkgRes : result.getPackageDexoptResults()) {
                    if (pkgRes.getStatus() == DexoptResult.DEXOPT_PERFORMED) {
                        long pkgSizeBytes = 0;
                        long pkgSizeBeforeBytes = 0;
                        for (DexoptResult.DexContainerFileDexoptResult dexRes :
                                pkgRes.getDexContainerFileDexoptResults()) {
                            long dexContainerSize = new File(dexRes.getDexContainerFile()).length();
                            pkgSizeBytes += dexRes.getSizeBytes() + dexContainerSize;
                            pkgSizeBeforeBytes += dexRes.getSizeBeforeBytes() + dexContainerSize;
                        }
                        FrameworkStatsLog.write(FrameworkStatsLog.APP_DOWNGRADED,
                                pkgRes.getPackageName(), pkgSizeBeforeBytes, pkgSizeBytes,
                                false /* aggressive */);
                    }
                }
            }

            var updatedPackages = new ArraySet<String>();
            for (DexoptResult.PackageDexoptResult pkgRes : result.getPackageDexoptResults()) {
                if (pkgRes.hasUpdatedArtifacts()) {
                    updatedPackages.add(pkgRes.getPackageName());
                }
            }
            if (!updatedPackages.isEmpty()) {
                LocalServices.getService(PinnerService.class)
                        .update(updatedPackages, false /* force */);
            }
        }
    }

    /**
     * Initializes {@link ArtManagerLocal} before {@link getArtManagerLocal} is called.
     */
    public static void initializeArtManagerLocal(
            @NonNull Context systemContext, @NonNull PackageManagerService pm) {
        ArtManagerLocal artManager = new ArtManagerLocal(systemContext);
        artManager.addDexoptDoneCallback(false /* onlyIncludeUpdates */, Runnable::run,
                pm.getDexOptHelper().new DexoptDoneHandler());
        LocalManagerRegistry.addManager(ArtManagerLocal.class, artManager);
        sArtManagerLocalIsInitialized = true;

        // Schedule the background job when boot is complete. This decouples us from when
        // JobSchedulerService is initialized.
        systemContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                artManager.scheduleBackgroundDexoptJob();
            }
        }, new IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED));

        StagedApexObserver.registerForStagedApexUpdates(artManager);
    }

    /**
     * Returns true if an {@link ArtManagerLocal} instance has been created.
     *
     * Avoid this function if at all possible, because it may hide initialization order problems.
     */
    public static boolean artManagerLocalIsInitialized() {
        return sArtManagerLocalIsInitialized;
    }

    /**
     * Returns the registered {@link ArtManagerLocal} instance, or else throws an unchecked error.
     */
    public static @NonNull ArtManagerLocal getArtManagerLocal() {
        try {
            return LocalManagerRegistry.getManagerOrThrow(ArtManagerLocal.class);
        } catch (ManagerNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns DexoptOptions by the given InstallRequest. */
    private DexoptOptions getDexoptOptionsByInstallRequest(InstallRequest installRequest) {
        final PackageSetting ps = installRequest.getScannedPackageSetting();
        final String packageName = ps.getPackageName();
        final boolean isBackupOrRestore =
                installRequest.getInstallReason() == INSTALL_REASON_DEVICE_RESTORE
                        || installRequest.getInstallReason() == INSTALL_REASON_DEVICE_SETUP;
        final int dexoptFlags = DexoptOptions.DEXOPT_BOOT_COMPLETE
                | DexoptOptions.DEXOPT_CHECK_FOR_PROFILES_UPDATES
                | DexoptOptions.DEXOPT_INSTALL_WITH_DEX_METADATA_FILE
                | (isBackupOrRestore ? DexoptOptions.DEXOPT_FOR_RESTORE : 0);
        // Compute the compilation reason from the installation scenario.
        final int compilationReason =
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        installRequest.getInstallScenario());
        final AndroidPackage pkg = ps.getPkg();
        var options = new DexoptOptions(packageName, compilationReason, dexoptFlags);
        if (installRequest.getDexoptCompilerFilter() != null) {
            options = options.overrideCompilerFilter(installRequest.getDexoptCompilerFilter());
        } else if (shouldSkipDexopt(installRequest)) {
            options = options.overrideCompilerFilter(DexoptParams.COMPILER_FILTER_NOOP);
        }
        return options;
    }

    /** Perform dexopt asynchronously if needed for the installation. */
    CompletableFuture<Void> performDexoptIfNeededAsync(InstallRequest installRequest) {
        if (!shouldCallArtService(installRequest)) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                        () -> {
                            try {
                                Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "dexopt");
                                DexoptOptions dexoptOptions =
                                        getDexoptOptionsByInstallRequest(installRequest);
                                // Don't fail application installs if the dexopt step fails.
                                // TODO(b/393076925): Make this async in ART Service.
                                DexoptResult dexOptResult =
                                        DexOptHelper.dexoptPackageUsingArtService(
                                                installRequest, dexoptOptions);
                                installRequest.onDexoptFinished(dexOptResult);
                            } finally {
                                Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
                            }
                        },
                        sDexoptExecutor)
                .exceptionally(
                        (t) -> {
                            // This should never happen. A normal dexopt failure should result
                            // in a DexoptResult.DEXOPT_FAILED, not an exception.
                            Slog.wtf(TAG, "Dexopt encountered a fatal error", t);
                            return null;
                        });
    }

    /**
     * Use ArtService to perform dexopt by the given InstallRequest.
     */
    static DexoptResult dexoptPackageUsingArtService(InstallRequest installRequest,
            DexoptOptions dexoptOptions) {
        final PackageSetting ps = installRequest.getScannedPackageSetting();
        final String packageName = ps.getPackageName();

        PackageManagerLocal packageManagerLocal =
                LocalManagerRegistry.getManager(PackageManagerLocal.class);
        try (PackageManagerLocal.FilteredSnapshot snapshot =
                     PackageManagerLocalImpl.withFilteredSnapshot(packageManagerLocal, ps)) {
            boolean ignoreDexoptProfile =
                    (installRequest.getInstallFlags()
                            & PackageManager.INSTALL_IGNORE_DEXOPT_PROFILE)
                            != 0;
            /*@DexoptFlags*/ int extraFlags =
                    ignoreDexoptProfile ? ArtFlags.FLAG_IGNORE_PROFILE : 0;
            DexoptParams params = dexoptOptions.convertToDexoptParams(extraFlags);
            DexoptResult dexOptResult = getArtManagerLocal().dexoptPackage(
                    snapshot, packageName, params);

            return dexOptResult;
        }
    }

    private static boolean shouldSkipDexopt(InstallRequest installRequest) {
        PackageSetting ps = installRequest.getScannedPackageSetting();
        AndroidPackage pkg = ps.getPkg();
        boolean onIncremental = isIncrementalPath(ps.getPathString());
        return pkg == null || pkg.isDebuggable() || onIncremental;
    }

    /**
     * Returns whether to call ART Service to perform dexopt for the given InstallRequest. Note that
     * ART Service may still skip dexopt, depending on the specified compiler filter, compilation
     * reason, and other conditions.
     */
    private static boolean shouldCallArtService(InstallRequest installRequest) {
        final boolean isApex = ((installRequest.getScanFlags() & SCAN_AS_APEX) != 0);
        // Historically, we did not dexopt instant apps,  and we have no plan to do so in the
        // future, so there is no need to call into ART Service.
        final boolean instantApp = ((installRequest.getScanFlags() & SCAN_AS_INSTANT_APP) != 0);
        final PackageSetting ps = installRequest.getScannedPackageSetting();
        final AndroidPackage pkg = ps.getPkg();
        final boolean performDexOptForRollback =
                !(installRequest.isRollback()
                        && installRequest
                                .getInstallSource()
                                .mInitiatingPackageName
                                .equals("android"));

        // THINK TWICE when you add a new condition here. You probably want to add a condition to
        // `shouldSkipDexopt` instead. In that way, ART Service will be called with the "skip"
        // compiler filter and it will have the chance to decide whether to skip dexopt.
        return !instantApp && pkg != null && !isApex && performDexOptForRollback;
    }

    /**
     * Returns true if the archive located at {@code fileName} has uncompressed dex file that can be
     * directly mapped.
     */
    public static boolean checkUncompressedDexInApk(String fileName) {
        StrictJarFile jarFile = null;
        try {
            jarFile = new StrictJarFile(fileName,
                    false /*verify*/, false /*signatureSchemeRollbackProtectionsEnforced*/);
            Iterator<ZipEntry> it = jarFile.iterator();
            boolean allCorrect = true;
            while (it.hasNext()) {
                ZipEntry entry = it.next();
                if (entry.getName().endsWith(".dex")) {
                    if (entry.getMethod() != ZipEntry.STORED) {
                        allCorrect = false;
                        Slog.w(TAG, "APK " + fileName + " has compressed dex code " +
                                entry.getName());
                    } else if ((entry.getDataOffset() & 0x3) != 0) {
                        allCorrect = false;
                        Slog.w(TAG, "APK " + fileName + " has unaligned dex code " +
                                entry.getName());
                    }
                }
            }
            return allCorrect;
        } catch (IOException ignore) {
            Slog.wtf(TAG, "Error when parsing APK " + fileName);
            return false;
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (IOException ignore) {}
        }
    }

    private static class StagedApexObserver extends IStagedApexObserver.Stub {
        private final @NonNull ArtManagerLocal mArtManager;

        static void registerForStagedApexUpdates(@NonNull ArtManagerLocal artManager) {
            IPackageManagerNative packageNative = IPackageManagerNative.Stub.asInterface(
                    ServiceManager.getService("package_native"));
            if (packageNative == null) {
                Log.e(TAG, "No IPackageManagerNative");
                return;
            }

            try {
                packageNative.registerStagedApexObserver(new StagedApexObserver(artManager));
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to register staged apex observer", e);
            }
        }

        private StagedApexObserver(@NonNull ArtManagerLocal artManager) {
            mArtManager = artManager;
        }

        @Override
        public void onApexStaged(@NonNull ApexStagedEvent event) {
            mArtManager.onApexStaged(Arrays.stream(event.stagedApexInfos)
                    .map(info -> info.moduleName).toArray(String[]::new));
        }
    }
}
