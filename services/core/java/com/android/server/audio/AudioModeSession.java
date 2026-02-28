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
import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.content.AttributionSource;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioFocusDispatcher;
import android.media.audio.AudioModeSessionRequest;
import android.media.audio.DeviceIdentity;
import android.media.audio.IAudioModeSession;
import android.media.audio.IAudioModeSessionCallback;
import android.os.Binder;
import android.os.Build;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FunctionalUtils;

import java.util.concurrent.Executor;
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

    @SuppressLint("DebugTrue")
    private static final boolean DEBUG = true;

    private static final AudioAttributes CALL_AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build();

    private final Object mLock = new Object();
    private final IAudioModeSessionCallback mCallback;
    private final AttributionSource mAttributionSource;
    private final AttributionSource mClientAttribution;
    private final AudioService mAudioService;
    private final int[] mNoFocusModes;
    private final AudioDeviceBroker mDeviceBroker;
    private final Executor mExecutor;

    // Intentionally no-op; session focus is locked, so doesn't have to respond to loss
    private final IAudioFocusDispatcher mAudioFocusDispatcher = new IAudioFocusDispatcher.Stub() {
        @Override
        @RequiresNoPermission
        public void dispatchAudioFocusChange(int focusChange, String clientId) {}
        @Override
        @RequiresNoPermission
        public void dispatchFocusResultFromExtPolicy(int requestResult, String clientId) {}
    };

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
    private boolean mHasFocus = false;

    @GuardedBy("mLock")
    // TODO: This is currently a layer inversion, since we still call into AudioService to get the
    // list of eligible communication devices. After this flips, we can hold the route list directly
    // instead.
    private List<AudioDeviceInfo> mAvailableDevices = new ArrayList<>();

    @GuardedBy("mLock")
    private int mCurrentRequestId = 42;

    @GuardedBy("mLock")
    private int mPendingRequestId = 0;

    @GuardedBy("mLock")
    private IAudioModeSession.Route mPendingRoute = null;

    @GuardedBy("mLock")
    private AudioDeviceInfo mSetCommDevice = null;

    /*package*/ void onAvailableDevicesChanged(List<AudioDeviceInfo> availableDevices) {
        if (DEBUG) {
            Slog.d(TAG, "onAvailableDevicesChanged: " + availableDevices);
        }
        synchronized (mLock) {
            if (Objects.equals(mAvailableDevices, availableDevices)) {
                return;
            }
            mAvailableDevices = availableDevices;
            dispatchRemote(() ->
                        mCallback.onAvailableRoutesChanged(getSessionRoutes(availableDevices)));
            if (mRequestedRoute != null) {
                for (var route : mAvailableDevices) {
                    if (matches(mRequestedRoute.output, route)) {
                        return;
                    }
                }
                preemptClientRoute(null);
            }
        }
    }

    /*package*/ void onCommunicationDeviceChanged(AudioDeviceInfo device) {
        if (DEBUG) {
            Slog.d(TAG, "onCommunicationDeviceChanged: " + device);
        }
        synchronized (mLock) {
            mSetCommDevice = device;
            updateCommunicationRoute(deviceToRoute(device));
        }
    }

    @GuardedBy("mLock")
    private void updateCommunicationRoute(@Nullable IAudioModeSession.Route route) {
        // No async result for null devices at the moment.
        if (route == null) {
            return;
        }
        if (mPendingRequestId != 0) {
            // non-null pending route
            if (Objects.equals(mPendingRoute, route)) {
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

    @GuardedBy("mLock")
    private void updateAudioFocus() {
        boolean shouldHaveFocus = isLive() && !ArrayUtils.contains(mNoFocusModes, mMode);

        if (shouldHaveFocus && !mHasFocus) {
            int res = mAudioService.requestAudioFocusForModeSession(
                    mAttributionSource,
                    mCallback.asBinder(),
                    CALL_AUDIO_ATTRIBUTES,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                    mAudioFocusDispatcher);
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mHasFocus = true;
            } else {
                Slog.wtf(TAG, "Unexpected focus result: " + res);
            }
        } else if (!shouldHaveFocus && mHasFocus) {
            mAudioService.abandonAudioFocusForModeSession(
                    mAttributionSource,
                    mCallback.asBinder(),
                    CALL_AUDIO_ATTRIBUTES,
                    mAudioFocusDispatcher);
            mHasFocus = false;
        }
    }

    public AudioModeSession(
            @NonNull AudioService audioService,
            @NonNull AudioDeviceBroker deviceBroker,
            @NonNull AudioModeSessionRequest request,
            @NonNull IAudioModeSessionCallback callback,
            @NonNull Executor executor) {
        if (DEBUG) {
            Slog.d(TAG, "AudioModeSession created with request: mode=" + request.mode
                    + " isDisplayActiveUseCase=" + request.isDisplayActiveUseCase);
        }
        mAudioService = audioService;
        mDeviceBroker = deviceBroker;
        mCallback = callback;
        mAttributionSource = new AttributionSource(request.attributionSource);
        mClientAttribution =
                request.clientAttribution == null
                        ? null
                        : new AttributionSource(request.clientAttribution);
        mExecutor = executor;

        synchronized (mLock) {
            // Initialize state from request
            mMode = request.mode;
            mIsDisplayActiveUseCase = request.isDisplayActiveUseCase;
            mNoFocusModes = request.noFocusModes;
            mSetCommDevice = mDeviceBroker.getCommunicationDevice();
            updateAudioFocus();
        }

        mAudioService.setMode(
                mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
    }

    @Override
    @PermissionManuallyEnforced
    public void setDisplayActiveUseCase(boolean isDisplayActiveUseCase) {
        if (DEBUG) {
            Slog.d(TAG, "setDisplayActiveUseCase: " + isDisplayActiveUseCase);
        }
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
        if (DEBUG) {
            Slog.d(TAG, "setMode: " + mode);
        }
        synchronized (mLock) {
            if (mIsClosed || mMode == mode) {
                return;
            }
            mMode = mode;
            if (isLive()) {
                updateAudioFocus();
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
            }
        }
    }

    @Override
    @PermissionManuallyEnforced
    public int setRequestedRoute(@Nullable IAudioModeSession.Route route) {
        if (DEBUG) {
            Slog.d(TAG, "setRequestedRoute: " + route);
        }
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
        if (DEBUG) {
            Slog.d(TAG, "setClientPaused: " + isPaused);
        }
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
                mDeviceBroker.setCommunicationDevice(
                        mCallback.asBinder(), mAttributionSource, null, true, TAG);
                updateAudioFocus();
                dispatchRemote(() -> mCallback.onPaused());
            } else {
                updateAudioFocus();
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
                dispatchRemote(() -> mCallback.onResumed(applyRouteAndTrack()));
            }
        }
    }

    @Override
    @PermissionManuallyEnforced
    public List<IAudioModeSession.Route> getAvailableRoutes() {
        if (DEBUG) {
            Slog.d(TAG, "getAvailableRoutes");
        }
        synchronized (mLock) {
            return getSessionRoutes(mAvailableDevices);
        }
    }

    @Override
    @PermissionManuallyEnforced
    public void close() {
        if (DEBUG) {
            Slog.d(TAG, "close");
        }
        synchronized (mLock) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
            mAudioService.setMode(
                    AudioManager.MODE_NORMAL,
                    mCallback.asBinder(),
                    mAttributionSource.getPackageName());
            mDeviceBroker.setCommunicationDevice(
                    mCallback.asBinder(), mAttributionSource, null, true, TAG);
            updateAudioFocus();
        }
        mDeviceBroker.removeAudioModeSession(this);
        dispatchRemote(() -> mCallback.onClosed());
    }

    public void pause() {
        if (DEBUG) {
            Slog.d(TAG, "pause");
        }
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
                mDeviceBroker.setCommunicationDevice(
                        mCallback.asBinder(), mAttributionSource, null, true, TAG);
                updateAudioFocus();
            }
            dispatchRemote(() -> mCallback.onPaused());
        }
    }

    public void resume() {
        if (DEBUG) {
            Slog.d(TAG, "resume");
        }
        synchronized (mLock) {
            if (mIsClosed || !mIsServerPaused) {
                return;
            }
            mIsServerPaused = false;
            if (!mIsClientPaused) {
                updateAudioFocus();
                mAudioService.setMode(
                        mMode, mCallback.asBinder(), mAttributionSource.getPackageName());
                dispatchRemote(() -> mCallback.onResumed(applyRouteAndTrack()));
            }
        }
    }

    public void preemptClientRoute(@Nullable IAudioModeSession.Route route) {
        if (DEBUG) {
            Slog.d(TAG, "preemptClientRoute: " + route);
        }
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
        if (isLive()) {
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

            mDeviceBroker.setCommunicationDevice(mCallback.asBinder(), mAttributionSource,
                    getRouteDevice(), true, TAG);
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

    private boolean isLive() {
        return !mIsClosed && !mIsClientPaused && !mIsServerPaused;
    }

    private AudioDeviceInfo getRouteDevice() {
        if (mRequestedRoute != null) {
            for (AudioDeviceInfo device : mAvailableDevices) {
                if (matches(mRequestedRoute.output, device)) {
                    return device;
                }
            }
        }
        return null;
    }

    private List<IAudioModeSession.Route> getSessionRoutes(List<AudioDeviceInfo> devices) {
        List<IAudioModeSession.Route> routes = new ArrayList<>();
        for (AudioDeviceInfo device : devices) {
            if (device.isSink()) {
                routes.add(deviceToRoute(device));
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

    private static IAudioModeSession.Route deviceToRoute(AudioDeviceInfo info) {
        if (info == null) return null;
        var route = new IAudioModeSession.Route();
        DeviceIdentity output = new DeviceIdentity();
        output.role = ROLE_OUTPUT;
        output.type = info.getType();
        output.address = info.getAddress();
        route.output = output;
        return route;
    }
}
