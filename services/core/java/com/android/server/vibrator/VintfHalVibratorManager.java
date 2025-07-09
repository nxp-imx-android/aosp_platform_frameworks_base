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

package com.android.server.vibrator;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.vibrator.IVibrationSession;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorCallback;
import android.hardware.vibrator.IVibratorManager;
import android.hardware.vibrator.VibrationSessionConfig;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.vibrator.Flags;
import android.util.IndentingPrintWriter;
import android.util.LongSparseArray;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.vibrator.VintfUtils.VintfSupplier;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/** Implementations for {@link HalVibratorManager} backed by VINTF objects. */
class VintfHalVibratorManager {
    private static final String TAG = "VintfHalVibratorManager";
    private static final int DEFAULT_VIBRATOR_ID = 0;

    /** Create {@link HalVibratorManager} based on declared services on device. */
    static HalVibratorManager createHalVibratorManager() {
        // TODO(b/422944962): Replace this with Vintf HalVibrator
        IntFunction<HalVibrator> vibratorFactory = VibratorController::new;

        if (ServiceManager.isDeclared(IVibratorManager.DESCRIPTOR + "/default")) {
            Slog.v(TAG, "Loading default IVibratorManager service.");
            return new DefaultHalVibratorManager(
                    new DefaultVibratorManagerSupplier(), vibratorFactory);
        }
        if (ServiceManager.isDeclared(IVibrator.DESCRIPTOR + "/default")) {
            Slog.v(TAG, "Loading default IVibrator service.");
            return new LegacyHalVibratorManager(new int[] { DEFAULT_VIBRATOR_ID }, vibratorFactory);
        }
        Slog.v(TAG, "No default services declared for IVibratorManager or IVibrator."
                + " Vibrator manager service will proceed without vibrator hardware.");
        return new LegacyHalVibratorManager(new int[0], vibratorFactory);
    }

    /** {@link VintfSupplier} for default {@link IVibratorManager} service. */
    static final class DefaultVibratorManagerSupplier extends VintfSupplier<IVibratorManager> {
        @Nullable
        @Override
        IBinder connectToService() {
            return Binder.allowBlocking(ServiceManager.waitForDeclaredService(
                    IVibratorManager.DESCRIPTOR + "/default"));
        }

        @NonNull
        @Override
        IVibratorManager castService(@NonNull IBinder binder) {
            return IVibratorManager.Stub.asInterface(binder);
        }
    }

    /** Default implementation for devices with {@link IVibratorManager} available. */
    static final class DefaultHalVibratorManager implements HalVibratorManager {
        private static final String TAG = "DefaultHalVibratorManager";

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final LongSparseArray<IVibrationSession> mOngoingSessions = new LongSparseArray<>();
        private final VintfSupplier<IVibratorManager> mHalSupplier;
        private final IntFunction<HalVibrator> mVibratorFactory;
        private final SparseArray<HalVibrator> mVibrators = new SparseArray<>();

        private Callbacks mCallbacks;

        private volatile long mCapabilities = 0;
        private volatile int[] mVibratorIds = new int[0];

        DefaultHalVibratorManager(VintfSupplier<IVibratorManager> supplier,
                IntFunction<HalVibrator> vibratorFactory) {
            mHalSupplier = supplier;
            mVibratorFactory = vibratorFactory;
        }

        @Override
        public void init(@NonNull Callbacks cb, @NonNull HalVibrator.Callbacks vibratorCb) {
            mCallbacks = cb;

            // Load vibrator hardware info. The vibrator ids and manager capabilities are loaded
            // once and assumed unchanged for the lifecycle of this service. Each vibrator can still
            // retry loading each individual vibrator hardware spec once more at systemReady.
            Optional<Integer> capabilities = VintfUtils.getNoThrow(mHalSupplier,
                    IVibratorManager::getCapabilities,
                    e -> Slog.e(TAG, "Error getting capabilities", e));
            Optional<int[]> vibratorIds = VintfUtils.getNoThrow(mHalSupplier,
                    IVibratorManager::getVibratorIds,
                    e -> Slog.e(TAG, "Error getting vibrator ids", e));
            mCapabilities = capabilities.orElse(0).longValue();
            mVibratorIds = vibratorIds.orElseGet(() -> new int[0]);
            for (int id : mVibratorIds) {
                HalVibrator vibrator = mVibratorFactory.apply(id);
                vibrator.init(vibratorCb);
                mVibrators.put(id, vibrator);
            }

            // Reset the hardware to a default state.
            // In case this is a runtime restart instead of a fresh boot.
            cancelSynced();
            if (Flags.vendorVibrationEffects()) {
                clearSessions();
            }
        }

        @Override
        public void onSystemReady() {
            for (int i = 0; i < mVibrators.size(); i++) {
                mVibrators.valueAt(i).onSystemReady();
            }
        }

        @Override
        public long getCapabilities() {
            return mCapabilities;
        }

        @NonNull
        @Override
        public int[] getVibratorIds() {
            return mVibratorIds;
        }

        @Nullable
        @Override
        public HalVibrator getVibrator(int id) {
            return mVibrators.get(id);
        }

        @Override
        public boolean prepareSynced(@NonNull int[] vibratorIds) {
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                Slog.w(TAG, "No capability to synchronize vibrations, ignoring prepare request.");
                return false;
            }
            return VintfUtils.runNoThrow(mHalSupplier,
                    hal -> hal.prepareSynced(vibratorIds),
                    e -> Slog.e(TAG, "Error preparing synced vibration on vibrator ids: "
                            + Arrays.toString(vibratorIds), e));
        }

        @Override
        public boolean triggerSynced(long vibrationId) {
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                Slog.w(TAG, "No capability to synchronize vibrations, ignoring trigger request.");
                return false;
            }
            final IVibratorCallback callback =
                    hasCapability(IVibratorManager.CAP_TRIGGER_CALLBACK)
                            ? new SyncedVibrationCallback(this, vibrationId)
                            : null;
            return VintfUtils.runNoThrow(mHalSupplier,
                    hal -> hal.triggerSynced(callback),
                    e -> Slog.e(TAG, "Error triggering synced vibration " + vibrationId, e));
        }

        @Override
        public boolean cancelSynced() {
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                Slog.w(TAG, "No capability to synchronize vibrations, ignoring cancel request.");
                return false;
            }
            return VintfUtils.runNoThrow(mHalSupplier,
                    IVibratorManager::cancelSynced,
                    e -> Slog.e(TAG, "Error canceling synced vibration", e));
        }

        @Override
        public boolean startSession(long sessionId, @NonNull int[] vibratorIds) {
            if (!hasCapability(IVibratorManager.CAP_START_SESSIONS)) {
                Slog.w(TAG, "No capability to start sessions, ignoring start session request.");
                return false;
            }
            final IVibratorCallback callback = new SessionCallback(this, sessionId);
            VibrationSessionConfig config = new VibrationSessionConfig();
            Optional<IVibrationSession> session = VintfUtils.getNoThrow(mHalSupplier,
                    hal -> hal.startSession(vibratorIds, config, callback),
                    e -> Slog.e(TAG, "Error starting vibration session " + sessionId
                            + " on vibrators " + Arrays.toString(vibratorIds), e));
            if (session.isPresent()) {
                synchronized (mLock) {
                    mOngoingSessions.put(sessionId, session.get());
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean endSession(long sessionId, boolean shouldAbort) {
            if (!hasCapability(IVibratorManager.CAP_START_SESSIONS)) {
                Slog.w(TAG, "No capability to start sessions, ignoring end session request.");
                return false;
            }
            IVibrationSession session;
            synchronized (mLock) {
                session = mOngoingSessions.get(sessionId);
            }
            if (session == null) {
                Slog.w(TAG, "No session with id " + sessionId + " to end, ignoring request.");
                return false;
            }
            try {
                if (shouldAbort) {
                    session.abort();
                } else {
                    session.close();
                }
                return true;
            } catch (RemoteException | RuntimeException e) {
                if (e instanceof DeadObjectException) {
                    removeSession(sessionId);
                }
                Slog.e(TAG, "Error ending session " + sessionId + " with abort=" + shouldAbort, e);
            }
            return false;
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("Default Hal VibratorManager:");
            pw.increaseIndent();

            pw.println("capabilities = " + Arrays.toString(getCapabilitiesNames()));
            pw.println("capabilitiesFlags = " + Long.toBinaryString(mCapabilities));
            pw.println("vibratorIds = " + Arrays.toString(mVibratorIds));
            pw.println("ongoingSessionsCount = " + mOngoingSessions.size());
            pw.println("Vibrators:");
            pw.increaseIndent();
            for (int i = 0; i < mVibrators.size(); i++) {
                mVibrators.valueAt(i).dump(pw);
            }
            pw.decreaseIndent();

            pw.decreaseIndent();
            pw.println();
        }

        @Override
        public String toString() {
            return "DefaultHalVibratorManager{"
                    + ", mCapabilities=" + Arrays.toString(getCapabilitiesNames())
                    + ", mCapabilities flags=" + Long.toBinaryString(mCapabilities)
                    + ", mVibratorIds=" + Arrays.toString(mVibratorIds)
                    + ", mOngoingSessions count=" + mOngoingSessions.size()
                    + '}';
        }

        private void clearSessions() {
            if (!hasCapability(IVibratorManager.CAP_START_SESSIONS)) {
                Slog.w(TAG, "No capability to start sessions, ignoring clear sessions request.");
                return;
            }
            VintfUtils.runNoThrow(mHalSupplier,
                    IVibratorManager::clearSessions,
                    e -> Slog.e(TAG, "Error clearing vibration sessions", e));
        }

        private void removeSession(long sessionId) {
            synchronized (mLock) {
                mOngoingSessions.remove(sessionId);
            }
        }

        private String[] getCapabilitiesNames() {
            List<String> names = new ArrayList<>();
            if (hasCapability(IVibratorManager.CAP_SYNC)) {
                names.add("SYNC");
            }
            if (hasCapability(IVibratorManager.CAP_PREPARE_ON)) {
                names.add("PREPARE_ON");
            }
            if (hasCapability(IVibratorManager.CAP_PREPARE_PERFORM)) {
                names.add("PREPARE_PERFORM");
            }
            if (hasCapability(IVibratorManager.CAP_PREPARE_COMPOSE)) {
                names.add("PREPARE_COMPOSE");
            }
            if (hasCapability(IVibratorManager.CAP_TRIGGER_CALLBACK)) {
                names.add("TRIGGER_CALLBACK");
            }
            if (hasCapability(IVibratorManager.CAP_MIXED_TRIGGER_ON)) {
                names.add("MIXED_TRIGGER_ON");
            }
            if (hasCapability(IVibratorManager.CAP_MIXED_TRIGGER_PERFORM)) {
                names.add("MIXED_TRIGGER_PERFORM");
            }
            if (hasCapability(IVibratorManager.CAP_MIXED_TRIGGER_COMPOSE)) {
                names.add("MIXED_TRIGGER_COMPOSE");
            }
            if (hasCapability(IVibratorManager.CAP_START_SESSIONS)) {
                names.add("START_SESSIONS");
            }
            return names.toArray(new String[names.size()]);
        }

        /** Provides {@link IVibratorCallback} without references to local instances. */
        private static final class SyncedVibrationCallback extends IVibratorCallback.Stub {
            private final WeakReference<DefaultHalVibratorManager> mManagerRef;
            private final long mVibrationId;

            SyncedVibrationCallback(DefaultHalVibratorManager manager, long vibrationId) {
                mManagerRef = new WeakReference<>(manager);
                mVibrationId = vibrationId;
            }

            @Override
            public void onComplete() {
                DefaultHalVibratorManager manager = mManagerRef.get();
                if (manager == null) {
                    return;
                }
                Callbacks callbacks = manager.mCallbacks;
                if (callbacks != null) {
                    callbacks.onSyncedVibrationComplete(mVibrationId);
                }
            }

            @Override
            public int getInterfaceVersion() {
                return IVibratorCallback.VERSION;
            }

            @Override
            public String getInterfaceHash() {
                return IVibratorCallback.HASH;
            }
        }

        /** Provides {@link IVibratorCallback} without references to local instances. */
        private static final class SessionCallback extends IVibratorCallback.Stub {
            private final WeakReference<DefaultHalVibratorManager> mManagerRef;
            private final long mSessionId;

            SessionCallback(DefaultHalVibratorManager manager, long sessionId) {
                mManagerRef = new WeakReference<>(manager);
                mSessionId = sessionId;
            }

            @Override
            public void onComplete() {
                DefaultHalVibratorManager manager = mManagerRef.get();
                if (manager == null) {
                    return;
                }
                manager.removeSession(mSessionId);
                Callbacks callbacks = manager.mCallbacks;
                if (callbacks != null) {
                    callbacks.onVibrationSessionComplete(mSessionId);
                }
            }

            @Override
            public int getInterfaceVersion() {
                return IVibratorCallback.VERSION;
            }

            @Override
            public String getInterfaceHash() {
                return IVibratorCallback.HASH;
            }
        }
    }

    /** Legacy implementation for devices without a declared {@link IVibratorManager} service. */
    static final class LegacyHalVibratorManager implements HalVibratorManager {
        private final int[] mVibratorIds;
        private final SparseArray<HalVibrator> mVibrators;

        LegacyHalVibratorManager(@NonNull int[] vibratorIds,
                IntFunction<HalVibrator> vibratorFactory) {
            mVibratorIds = vibratorIds;
            mVibrators = new SparseArray<>(vibratorIds.length);
            for (int id : vibratorIds) {
                mVibrators.put(id, vibratorFactory.apply(id));
            }
        }

        @Override
        public void init(@NonNull Callbacks cb, @NonNull HalVibrator.Callbacks vibratorCb) {
            for (int i = 0; i < mVibrators.size(); i++) {
                mVibrators.valueAt(i).init(vibratorCb);
            }
        }

        @Override
        public void onSystemReady() {
            for (int i = 0; i < mVibrators.size(); i++) {
                mVibrators.valueAt(i).onSystemReady();
            }
        }

        @Override
        public long getCapabilities() {
            return 0;
        }

        @NonNull
        @Override
        public int[] getVibratorIds() {
            return mVibratorIds;
        }

        @Nullable
        @Override
        public HalVibrator getVibrator(int id) {
            return mVibrators.get(id);
        }

        @Override
        public boolean prepareSynced(@NonNull int[] vibratorIds) {
            return false;
        }

        @Override
        public boolean triggerSynced(long vibrationId) {
            return false;
        }

        @Override
        public boolean cancelSynced() {
            return false;
        }

        @Override
        public boolean startSession(long sessionId, @NonNull int[] vibratorIds) {
            return false;
        }

        @Override
        public boolean endSession(long sessionId, boolean shouldAbort) {
            return false;
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("Legacy HAL VibratorManager:");
            pw.increaseIndent();

            pw.println("vibratorIds = " + Arrays.toString(mVibratorIds));
            pw.println("Vibrators:");
            pw.increaseIndent();
            for (int i = 0; i < mVibrators.size(); i++) {
                mVibrators.valueAt(i).dump(pw);
            }
            pw.decreaseIndent();

            pw.decreaseIndent();
            pw.println();
        }

        @Override
        public String toString() {
            return "LegacyHalVibratorManager{"
                    + ", mVibratorIds=" + Arrays.toString(mVibratorIds)
                    + '}';
        }
    }
}
