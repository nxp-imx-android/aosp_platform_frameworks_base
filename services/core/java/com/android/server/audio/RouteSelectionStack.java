/*
 * Copyright 2025 The Android Open Source Project
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

import static com.android.server.utils.EventLogger.Event.ALOGI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.media.AudioDeviceAttributes;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecordingConfiguration;
import android.media.AudioSystem;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.function.TriFunction;
import com.android.server.utils.EventLogger;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maintains a stack of preferred devices for a set of clients. The latest added session is the
 * winner, unless a mode owner is present, in which case it always wins.
 */
public class RouteSelectionStack implements IBinder.DeathRecipient {
    private final Consumer<RouteClient> mDeathCb;
    private final EventLogger mLogger;

    // Invariant: client.getToken is unique per client
    @GuardedBy("this") private final List<RouteClient> mClients = new ArrayList<>();

    // Token of the current mode owner (matching the route client)
    @GuardedBy("this") private IBinder mModeOwnerToken;

    // threading note: deathCallable should post
    RouteSelectionStack(Consumer<RouteClient> deathCallable, EventLogger logger) {
        mDeathCb = deathCallable;
        mLogger = logger;
    }

    public synchronized void addClient(RouteClient client) {
        if (removeClient(client.getToken()) != null) {
            mLogger.enqueueAndSlog("client replacement: " + client, ALOGI);
        }
        mClients.add(client);
        try {
            client.getToken().linkToDeath(this, 0);
        } catch (RemoteException e) {
            mDeathCb.accept(client);
        }
        mLogger.enqueueAndSlog("addClient: " + client + " -> " + getStackStateString(), ALOGI);
    }

    public synchronized RouteClient removeClient(IBinder token) {
        RouteClient client = null;
        // really Java..
        for (int i = mClients.size() - 1; i >= 0; i--) {
            if (mClients.get(i).getToken().equals(token)) {
                token.unlinkToDeath(this, 0);
                client = mClients.remove(i);
                break;
            }
        }
        mLogger.enqueueAndSlog("removeClient: " + client + " -> " + getStackStateString(), ALOGI);
        return client;
    }

    /**
     * Returns the communication client with the highest priority:
     * - 1) the client which is currently also controlling the audio mode
     * - 2) the top client in the stack if there is no audio mode owner
     * - 3) null otherwise
     *   TODO: evaluate call-sites excluding returning the winning device
     * @return CommunicationRouteClient the client driving the communication use case routing.
     */
    public synchronized RouteClient topClient() {
        for (var client : mClients) {
            // TODO: migrate to isActive
            if (!client.isDisabled() && client.getToken().equals(mModeOwnerToken)) {
                return client;
            }
        }
        return mModeOwnerToken == null &&
                !mClients.isEmpty() && mClients.getLast().isActive() ? mClients.getLast() : null;
    }

    /**
     * Set the token corresponding to the current mode owner (i.e. the same as the token in the
     * route client). Null to clear.
     */
    public synchronized void setModeOwnerToken(@Nullable IBinder who) {
        mModeOwnerToken = who;
        mLogger.enqueueAndSlog("setModeOwnerToken: " + who + " -> " + getStackStateString(), ALOGI);
    }

    /**
     * Disable, or un-disable the preference of all clients whose device preference based on a
     * predicate.
     *
     * @param pred Input: client device pref -> Non-null ADA iff client selecting this device
     *         should be enabled, with the ADA updated to this value
     * TODO: temporary until this logic is folded in.
     */
    public synchronized void applyDeviceRestrictions(
            Function<AudioDeviceAttributes, AudioDeviceAttributes> pred) {
        for (var client : mClients) {
            var newDevice = pred.apply(client.getDevice());
            if (newDevice != null && !newDevice.equals(client.getDevice())) {
                // Routing fixup
                client.setDevice(newDevice);
            }
            client.setDisabled(newDevice == null);
        }
        mLogger.enqueueAndSlog("applyDeviceRestrictions " + getStackStateString(), ALOGI);
    }

    public RouteClient getClientForToken(IBinder token) {
        for (var client : mClients) {
            if (client.getToken() == token) {
                return client;
            }
        }
        return null;
    }

    // TODO: All identification should be binder-mediated, either via static tokens generated in
    // AudioManager for existing/legacy clients (which moves matching from uid to process, but this
    // is better, and almost certainly non-breaking), or via API exposed sessions which hold
    // explicit tokens.
    public RouteClient getClientForUid(int uid) {
        for (var client : mClients) {
            if (client.getUid() == uid) {
                return client;
            }
        }
        return null;
    }

    public synchronized void updateClientActivities(
            List<AudioPlaybackConfiguration> playbackConfigs,
            List<AudioRecordingConfiguration> recordConfigs,
            TriFunction<RouteClient, Boolean, Boolean, Object> captor) {
        for (var client : mClients) {
            boolean wasActive = client.isActive();
            boolean updateClientState = false;
            if (playbackConfigs != null) {
                client.setPlaybackActive(false);
                for (AudioPlaybackConfiguration config : playbackConfigs) {
                    if (config.getClientUid() == client.getUid() && config.isActive()) {
                        client.setPlaybackActive(true);
                        updateClientState = true;
                        break;
                    }
                }
            }
            if (recordConfigs != null) {
                client.setRecordingActive(false);
                for (AudioRecordingConfiguration config : recordConfigs) {
                    if (config.getClientUid() == client.getUid() && !config.isClientSilenced()) {
                        client.setRecordingActive(true);
                        updateClientState = true;
                        break;
                    }
                }
            }
            captor.apply(client, updateClientState, wasActive);
        }
    }

    @Override
    public void binderDied() {
        throw new IllegalStateException("Unexpected binderDied cb called");
    }

    // TODO: layer inversion
    @Override
    public void binderDied(IBinder who) {
        mDeathCb.accept(getClientForToken(who));
    }

    public void dump(PrintWriter pw, String prefix) {
        synchronized (this) {
            pw.println(prefix + "RouteSelectionStack:");
            pw.println(prefix + "  Mode owner token: " + mModeOwnerToken);
            pw.println(prefix + "  Clients (top of stack first):");
            for (int i = mClients.size() - 1; i >= 0; i--) {
                RouteClient client = mClients.get(i);
                String ownerSuffix = client.getToken() == mModeOwnerToken ? " (mode owner)" : "";
                pw.println(prefix + "    " + client + ownerSuffix);
            }
        }
    }

    private synchronized String getStackStateString() {
        synchronized (this) {
            return mClients.stream()
                    .map(c
                            -> c.toShortString() + ":"
                                    + (c.getToken() == mModeOwnerToken ? "o" : ""))
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    static class RouteClient {
        private final IBinder mToken;
        @NonNull private final AttributionSource mAttributionSource;
        private final boolean mIsPrivileged;
        @NonNull private AudioDeviceAttributes mDevice;
        private boolean mPlaybackActive;
        private boolean mRecordingActive;

        // Route selection should not be considered due to client state or device invalidation
        private boolean mDisabled;

        RouteClient(IBinder cb, @NonNull AttributionSource attributionSource,
                @NonNull AudioDeviceAttributes device, boolean isPrivileged,
                boolean playbackActive, boolean recordingActive) {
            mToken = cb;
            mAttributionSource = attributionSource;
            mDevice = device;
            mIsPrivileged = isPrivileged;
            mPlaybackActive = playbackActive;
            mRecordingActive = recordingActive;
            mDisabled = false;
        }

        IBinder getToken() {
            return mToken;
        }

        @NonNull
        AttributionSource getAttributionSource() {
            return mAttributionSource;
        }

        int getUid() {
            return mAttributionSource.getUid();
        }

        boolean isPrivileged() {
            return mIsPrivileged;
        }

        @NonNull
        AudioDeviceAttributes getDevice() {
            return mDevice;
        }

        public boolean isActive() {
            return !mDisabled && (mIsPrivileged || mRecordingActive || mPlaybackActive);
        }

        public boolean isDisabled() {
            return mDisabled;
        }

        private void setDevice(@NonNull AudioDeviceAttributes device) {
            mDevice = device;
        }

        // TODO: internalize for synchronization
        public void setPlaybackActive(boolean active) {
            mPlaybackActive = active;
        }

        public void setRecordingActive(boolean active) {
            mRecordingActive = active;
        }

        public void setDisabled(boolean disabled) {
            mDisabled = disabled;
        }

        @Override
        public String toString() {
            return "[RouteClient: " + mAttributionSource.getUid() + "("
                    + mAttributionSource.getPackageName() + ")"
                    + " mDevice: " + mDevice + " mIsPrivileged: " + mIsPrivileged
                    + " mPlaybackActive: " + mPlaybackActive
                    + " mRecordingActive: " + mRecordingActive + " mDisabled: " + mDisabled + "]";
        }

        public String toShortString() {
            String deviceName = AudioSystem.getDeviceName(getDevice().getType());
            return String.valueOf(getUid()) + "("
                    + abbrevPackage(getAttributionSource().getPackageName()) + ")"
                    + ":" + clampedSubstr(deviceName, 0, 3) + "_" + getDevice().getAddress() + ":"
                    + (isActive() ? "a" : "") + ":" + (isDisabled() ? "d" : "");
        }
    }

    private static String abbrevPackage(String packageName) {
        if (packageName == null) {
            return "";
        }
        var cursor = packageName.lastIndexOf('.') + 1;
        return clampedSubstr(packageName, cursor, cursor + 5);
    }

    private static String clampedSubstr(String str, int beginInd, int endInd) {
        return str.substring(Math.max(0, beginInd), Math.min(endInd, str.length()));
    }
}
