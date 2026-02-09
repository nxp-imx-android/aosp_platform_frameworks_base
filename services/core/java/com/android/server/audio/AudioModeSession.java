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

package com.android.server.audio;

import static android.media.AudioModeSession.ROUTING_RESULT_PREEMPTED;
import static android.media.AudioModeSession.ROUTING_RESULT_SUCCESSFUL;
import static android.media.AudioDeviceAttributes.ROLE_OUTPUT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.content.AttributionSource;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audio.AudioModeSessionRequest;
import android.media.audio.DeviceIdentity;
import android.media.audio.IAudioModeSession;
import android.media.audio.IAudioModeSessionCallback;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.FunctionalUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service-side implementation of the {@link android.media.AudioModeSession} interface. Tracks the
 * state of a single client's unified routing and mode session. All operations on this session apply
 * to state internally within this class, in a synchronous manner. However, the application of the
 * route/mode (when the session is in an unpaused state), is asynchronous, and results are reported
 * separately to the client.
 *
 * <p>Threading note: We are currently *above* the AudioService locks (i.e. we call into
 * AudioService synchronously, holding locks).
 */
public final class AudioModeSession extends IAudioModeSession.Stub {
    private static final String TAG = "AudioModeSession";

    private final Object mLock = new Object();
    private final IAudioModeSessionCallback mCallback;
    private final AttributionSource mAttributionSource;
    private final AttributionSource mClientAttribution;
    private final AudioService mAudioService;
    private final int[] mNoFocusModes;
    private final AudioManager mAudioManager;
    private final ExecutorService mExecutor;

    // Session State
    @GuardedBy("mLock")
    private int mMode;

    @GuardedBy("mLock")
    private boolean mIsDisplayActiveUseCase;

    @GuardedBy("mLock")
    private IAudioModeSession.Route mRequestedRoute;

    @GuardedBy("mLock")
    private boolean mIsClientPaused;

    @GuardedBy("mLock")
    private boolean mIsServerPaused;

    @GuardedBy("mLock")
    private boolean mIsClosed;

    @GuardedBy("mLock")
    // TODO: This is currently a layer inversion, since we still call into AudioService to get the
    // list of eligible communication devices. After this flips, we can hold the route list directly
    // instead.
    private List<AudioDeviceInfo> mAvailableRoutes = new ArrayList<>();

    @GuardedBy("mLock")
    private int mCurrentRequestId = 42;

    @GuardedBy("mLock")
    private int mPendingRequestId = 0;

    @GuardedBy("mLock")
    private IAudioModeSession.Route mPendingRoute = null;

    @GuardedBy("mLock")
    private AudioDeviceInfo mSetCommDevice = null;

    private final AudioDeviceCallback mAudioDevicesListener =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    updateAvailableRoutes();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    updateAvailableRoutes();
                }
            };

    private final AudioManager.OnCommunicationDeviceChangedListener
            mOnCommunicationDeviceChangedListener =
                    new AudioManager.OnCommunicationDeviceChangedListener() {
                        @Override
                        public void onCommunicationDeviceChanged(@Nullable AudioDeviceInfo device) {
                            synchronized (mLock) {
                                mSetCommDevice = device;
                                // No async result for null devices at the moment.
                                if (device == null) {
                                    return;
                                }
                                if (mPendingRequestId != 0) {
                                    // non-null pending route
                                    if (matches(mPendingRoute.output, mSetCommDevice)) {
                                        final int pRequestId = mPendingRequestId;
                                        final IAudioModeSession.Route pRoute = mPendingRoute;
                                        dispatchRemote(
                                                () ->
                                                        mCallback.onRoutingResult(
                                                                pRequestId,
                                                                pRoute,
                                                                ROUTING_RESULT_SUCCESSFUL));
                                        mPendingRequestId = 0;
                                        mPendingRoute = null;
                                    }
                                }
                            }
                        }
                    };

    public AudioModeSession(
            @NonNull AudioService audioService,
            @NonNull AudioModeSessionRequest request,
            @NonNull IAudioModeSessionCallback callback) {
        mAudioService = audioService;
        mCallback = callback;
        mAttributionSource = new AttributionSource(request.attributionSource);
        mClientAttribution =
                request.clientAttribution == null
                        ? null
                        : new AttributionSource(request.clientAttribution);
        mExecutor = Executors.newSingleThreadExecutor();

        mAudioManager = mAudioService.mContext.getSystemService(AudioManager.class);
        synchronized (mLock) {
            // Initialize state from request
            mMode = request.mode;
            mIsDisplayActiveUseCase = request.isDisplayActiveUseCase;
            mNoFocusModes = request.noFocusModes;
            mSetCommDevice = mAudioManager.getCommunicationDevice();
        }

        mAudioService.setMode(
                mMode, mCallback.asBinder(), mAttributionSource.getPackageName());

        // on main thread, temporary
        mAudioManager.registerAudioDeviceCallback(mAudioDevicesListener, null);
        mAudioManager.addOnCommunicationDeviceChangedListener(
                mAudioService.mContext.getMainExecutor(), mOnCommunicationDeviceChangedListener);
        updateAvailableRoutes();
    }

    @Override
    @PermissionManuallyEnforced
    public void setDisplayActiveUseCase(boolean isDisplayActiveUseCase) {
        synchronized (mLock) {
            if (mIsClosed || mIsDisplayActiveUseCase == isDisplayActiveUseCase) {
                return;
            }
            mIsDisplayActiveUseCase = isDisplayActiveUseCase;
        }
    }

    @Override
    @PermissionManuallyEnforced
    public void setMode(int mode) {
        synchronized (mLock) {
            if (mIsClosed || mMode == mode) {
                return;
            }
            mMode = mode;
            if (isApplicable()) {
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
            }
        }
    }

    @Override
    @PermissionManuallyEnforced
    public int setRequestedRoute(@Nullable IAudioModeSession.Route route) {
        synchronized (mLock) {
            if (mIsClosed) {
                return 0;
            }
            mRequestedRoute = route;
            return applyRouteAndTrack();
        }
    }

    @Override
    @PermissionManuallyEnforced
    public void setClientPaused(boolean isPaused) {
        synchronized (mLock) {
            if (mIsClosed || mIsClientPaused == isPaused) {
                return;
            }
            mIsClientPaused = isPaused;
            if (mIsServerPaused) {
                return;
            }

            if (mIsClientPaused) {
                mAudioService.setMode(
                        AudioManager.MODE_NORMAL,
                        mCallback.asBinder(),
                        mAttributionSource.getPackageName());
                mAudioService.setCommunicationDevice(
                        mCallback.asBinder(), /*clear*/ 0, mAttributionSource);
                dispatchRemote(() -> mCallback.onPaused());
            } else {
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
                dispatchRemote(() -> mCallback.onResumed(applyRouteAndTrack()));
            }
        }
    }

    @Override
    @PermissionManuallyEnforced
    public List<IAudioModeSession.Route> getAvailableRoutes() {
        synchronized (mLock) {
            return getSessionRoutes(mAvailableRoutes);
        }
    }

    @Override
    @PermissionManuallyEnforced
    public void close() {
        synchronized (mLock) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;

            mAudioService.setMode(
                    AudioManager.MODE_NORMAL,
                    mCallback.asBinder(),
                    mAttributionSource.getPackageName());
            mAudioService.setCommunicationDevice(
                    mCallback.asBinder(), /*clear*/ 0, mAttributionSource);
        }
        mAudioManager.unregisterAudioDeviceCallback(mAudioDevicesListener);
        mAudioManager.removeOnCommunicationDeviceChangedListener(
                mOnCommunicationDeviceChangedListener);
        dispatchRemote(() -> mCallback.onClosed());
        mExecutor.shutdown();
    }

    public void pause() {
        synchronized (mLock) {
            if (mIsClosed || mIsServerPaused) {
                return;
            }
            mIsServerPaused = true;
            if (!mIsClientPaused) {
                mAudioService.setMode(
                        AudioManager.MODE_NORMAL,
                        mCallback.asBinder(),
                        mAttributionSource.getPackageName());
                mAudioService.setCommunicationDevice(
                        mCallback.asBinder(), /*clear*/ 0, mAttributionSource);
            }
            dispatchRemote(() -> mCallback.onPaused());
        }
    }

    public void resume() {
        synchronized (mLock) {
            if (mIsClosed || !mIsServerPaused) {
                return;
            }
            mIsServerPaused = false;
            if (!mIsClientPaused) {
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
                dispatchRemote(() -> mCallback.onResumed(applyRouteAndTrack()));
            }
        }
    }

    public void preemptClientRoute(@Nullable IAudioModeSession.Route route) {
        synchronized (mLock) {
            if (mIsClosed || Objects.equals(route, mRequestedRoute)) {
                return;
            }
            mRequestedRoute = route;
            dispatchRemote(() -> mCallback.onExternalRequestedRouteChanged(
                    route, applyRouteAndTrack()));
        }
    }

    @GuardedBy("mLock")
    private int applyRouteAndTrack() {
        int requestId = getNextRequestId();
        if (isApplicable()) {
            // Preempt previous if exists
            if (mPendingRequestId != 0) {
                final int pRequestId = mPendingRequestId;
                final IAudioModeSession.Route pRoute = mPendingRoute;
                dispatchRemote(() -> mCallback.onRoutingResult(pRequestId, pRoute,
                        ROUTING_RESULT_PREEMPTED));
                mPendingRequestId = 0;
                mPendingRoute = null;
            }

            // succeed current if already set
            if ((mSetCommDevice != null && mRequestedRoute != null &&
                    matches(mRequestedRoute.output, mSetCommDevice)) ||
                    (mSetCommDevice == null && mRequestedRoute == null)) {
                dispatchRemote(() -> mCallback.onRoutingResult(requestId, mRequestedRoute,
                        ROUTING_RESULT_SUCCESSFUL));
                return requestId;
            }

            mAudioService.setCommunicationDevice(mCallback.asBinder(), getRouteId(),
                    mAttributionSource);
            if (mRequestedRoute == null) {
                // TODO: track default routes
                dispatchRemote(() -> mCallback.onRoutingResult(requestId, null,
                        ROUTING_RESULT_SUCCESSFUL));
            } else {
                mPendingRequestId = requestId;
                mPendingRoute = mRequestedRoute;
            }
        }
        return requestId;
    }



    @GuardedBy("mLock")
    private int getNextRequestId() {
        return ++mCurrentRequestId;
    }

    private void dispatchRemote(FunctionalUtils.ThrowingRunnable r) {
        mExecutor.execute(
                () -> {
                    try {
                        r.runOrThrow();
                    } catch (Exception e) {
                        Slog.wtf(TAG, "Client died", e);
                    }
                });
    }

    private boolean isApplicable() {
        return !mIsClosed && !mIsClientPaused && !mIsServerPaused;
    }

    private int getRouteId() {
        int portId = 0;
        if (mRequestedRoute != null) {
            for (AudioDeviceInfo device : mAvailableRoutes) {
                if (matches(mRequestedRoute.output, device)) {
                    portId = device.getId();
                    break;
                }
            }
        }
        return portId;
    }

    private void updateAvailableRoutes() {
        List<AudioDeviceInfo> routes = AudioDeviceBroker.getAvailableCommunicationDevices();
        synchronized (mLock) {
            if (Objects.equals(mAvailableRoutes, routes)) {
                return;
            }
            mAvailableRoutes = routes;
            dispatchRemote(() -> mCallback.onAvailableRoutesChanged(getSessionRoutes(routes)));
        }
    }

    private List<IAudioModeSession.Route> getSessionRoutes(List<AudioDeviceInfo> devices) {
        List<IAudioModeSession.Route> routes = new ArrayList<>();
        for (AudioDeviceInfo device : devices) {
            if (device.isSink()) {
                IAudioModeSession.Route route = new IAudioModeSession.Route();
                DeviceIdentity output = new DeviceIdentity();
                output.role = ROLE_OUTPUT;
                output.type = device.getType();
                output.address = device.getAddress();
                route.output = output;
                routes.add(route);
            }
        }
        return routes;
    }

    private static boolean matches(@NonNull DeviceIdentity identity,
                                   @NonNull AudioDeviceInfo info) {
        if (identity.type != info.getType()) {
            return false;
        }
        if (!Objects.equals(identity.address, info.getAddress())) {
            return false;
        }
        return info.isSink() == (identity.role == ROLE_OUTPUT);
    }
}
