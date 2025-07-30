/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.settingslib.media;

import static android.media.MediaRoute2Info.CONNECTION_STATE_CONNECTING;
import static android.media.MediaRoute2Info.TYPE_AUX_LINE;
import static android.media.MediaRoute2Info.TYPE_BLE_HEADSET;
import static android.media.MediaRoute2Info.TYPE_BLUETOOTH_A2DP;
import static android.media.MediaRoute2Info.TYPE_BUILTIN_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_DOCK;
import static android.media.MediaRoute2Info.TYPE_GROUP;
import static android.media.MediaRoute2Info.TYPE_HDMI;
import static android.media.MediaRoute2Info.TYPE_HDMI_ARC;
import static android.media.MediaRoute2Info.TYPE_HDMI_EARC;
import static android.media.MediaRoute2Info.TYPE_HEARING_AID;
import static android.media.MediaRoute2Info.TYPE_LINE_ANALOG;
import static android.media.MediaRoute2Info.TYPE_LINE_DIGITAL;
import static android.media.MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_CAR;
import static android.media.MediaRoute2Info.TYPE_REMOTE_COMPUTER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_GAME_CONSOLE;
import static android.media.MediaRoute2Info.TYPE_REMOTE_SMARTPHONE;
import static android.media.MediaRoute2Info.TYPE_REMOTE_SMARTWATCH;
import static android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER;
import static android.media.MediaRoute2Info.TYPE_REMOTE_TABLET;
import static android.media.MediaRoute2Info.TYPE_REMOTE_TABLET_DOCKED;
import static android.media.MediaRoute2Info.TYPE_REMOTE_TV;
import static android.media.MediaRoute2Info.TYPE_UNKNOWN;
import static android.media.MediaRoute2Info.TYPE_USB_ACCESSORY;
import static android.media.MediaRoute2Info.TYPE_USB_DEVICE;
import static android.media.MediaRoute2Info.TYPE_USB_HEADSET;
import static android.media.MediaRoute2Info.TYPE_WIRED_HEADPHONES;
import static android.media.MediaRoute2Info.TYPE_WIRED_HEADSET;
import static android.media.session.MediaController.PlaybackInfo;

import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED;
import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING;
import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED;
import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED;
import static com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaRoute2Info;
import android.media.RouteListingPreference;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.media.SuggestedDeviceInfo;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.R;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** InfoMediaManager provide interface to get InfoMediaDevice list. */
@RequiresApi(Build.VERSION_CODES.R)
public abstract class InfoMediaManager {
    /** Callback for notifying device is added, removed and attributes changed. */
    public interface MediaDeviceCallback {

        /**
         * Callback for notifying MediaDevice list is added.
         *
         * @param devices the MediaDevice list
         */
        void onDeviceListAdded(@NonNull List<MediaDevice> devices);

        /**
         * Callback for notifying MediaDevice list is removed.
         *
         * @param devices the MediaDevice list
         */
        void onDeviceListRemoved(@NonNull List<MediaDevice> devices);

        /**
         * Callback for notifying connected MediaDevice is changed.
         *
         * @param id the id of MediaDevice
         */
        void onConnectedDeviceChanged(@Nullable String id);

        /**
         * Callback for notifying that transferring is failed.
         *
         * @param reason the reason that the request has failed. Can be one of followings: {@link
         *     android.media.MediaRoute2ProviderService#REASON_UNKNOWN_ERROR}, {@link
         *     android.media.MediaRoute2ProviderService#REASON_REJECTED}, {@link
         *     android.media.MediaRoute2ProviderService#REASON_NETWORK_ERROR}, {@link
         *     android.media.MediaRoute2ProviderService#REASON_ROUTE_NOT_AVAILABLE}, {@link
         *     android.media.MediaRoute2ProviderService#REASON_INVALID_COMMAND},
         */
        void onRequestFailed(int reason);

        /** Callback for notifying that the suggested device has been updated. */
        default void onSuggestedDeviceUpdated(@Nullable SuggestedDeviceState suggestedDevice) {}
        ;
    }

    /**
     * Wrapper class around SuggestedDeviceInfo and the corresponding connection state of the
     * suggestion.
     */
    public static class SuggestedDeviceState {
        private final SuggestedDeviceInfo mSuggestedDeviceInfo;
        private final @LocalMediaManager.MediaDeviceState int mConnectionState;

        @VisibleForTesting
        SuggestedDeviceState(@NonNull SuggestedDeviceInfo suggestedDeviceInfo) {
            mSuggestedDeviceInfo = suggestedDeviceInfo;
            mConnectionState = STATE_DISCONNECTED;
        }

        @VisibleForTesting
        SuggestedDeviceState(
                @NonNull SuggestedDeviceInfo suggestedDeviceInfo,
                @LocalMediaManager.MediaDeviceState int state) {
            mSuggestedDeviceInfo = suggestedDeviceInfo;
            mConnectionState = state;
        }

        @NonNull
        public SuggestedDeviceInfo getSuggestedDeviceInfo() {
            return mSuggestedDeviceInfo;
        }

        public @LocalMediaManager.MediaDeviceState int getConnectionState() {
            return mConnectionState;
        }

        /** Gets the drawable associated with the suggested device type. */
        @NonNull
        public Drawable getIcon(Context context) {
            return getDrawableForSuggestion(context, this);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SuggestedDeviceState)) {
                return false;
            }
            return Objects.equals(
                            mSuggestedDeviceInfo, ((SuggestedDeviceState) obj).mSuggestedDeviceInfo)
                    && Objects.equals(
                            mConnectionState, ((SuggestedDeviceState) obj).mConnectionState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSuggestedDeviceInfo, mConnectionState);
        }

        @Override
        public String toString() {
            return "info: " + mSuggestedDeviceInfo + " state " + mConnectionState;
        }
    }

    /** Checked exception that signals the specified package is not present in the system. */
    public static class PackageNotAvailableException extends Exception {
        public PackageNotAvailableException(String message) {
            super(message);
        }
    }

    private static final String TAG = "InfoMediaManager";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected final Object mLock = new Object();
    @GuardedBy("mLock")
    protected final List<MediaDevice> mMediaDevices = new ArrayList<>();
    @NonNull protected final Context mContext;
    @NonNull protected final String mPackageName;
    @NonNull protected final UserHandle mUserHandle;
    private final Collection<MediaDeviceCallback> mCallbacks = new CopyOnWriteArrayList<>();
    @GuardedBy("mLock")
    private MediaDevice mCurrentConnectedDevice;
    private MediaController mMediaController;
    private PlaybackInfo mLastKnownPlaybackInfo;
    private final LocalBluetoothManager mBluetoothManager;
    private final Map<String, RouteListingPreference.Item> mPreferenceItemMap =
            new ConcurrentHashMap<>();
    @GuardedBy("mLock")
    private final Map<String, List<SuggestedDeviceInfo>> mSuggestedDeviceMap = new HashMap<>();
    @GuardedBy("mLock")
    @Nullable private SuggestedDeviceState mSuggestedDeviceState;

    private final MediaController.Callback mMediaControllerCallback = new MediaControllerCallback();

    /* package */ InfoMediaManager(
            @NonNull Context context,
            @NonNull String packageName,
            @NonNull UserHandle userHandle,
            @NonNull LocalBluetoothManager localBluetoothManager,
            @Nullable MediaController mediaController) {
        mContext = context;
        mBluetoothManager = localBluetoothManager;
        mPackageName = packageName;
        mUserHandle = userHandle;
        mMediaController = mediaController;
        if (mediaController != null) {
            mLastKnownPlaybackInfo = mediaController.getPlaybackInfo();
        }
    }

    /**
     * Creates an instance of InfoMediaManager.
     *
     * @param context The {@link Context}.
     * @param packageName The package name of the app for which to control routing, or null if the
     *     caller is interested in system-level routing only (for example, headsets, built-in
     *     speakers, as opposed to app-specific routing (for example, casting to another device).
     * @param userHandle The {@link UserHandle} of the user on which the app to control is running,
     *     or null if the caller does not need app-specific routing (see {@code packageName}).
     * @param token The token of the associated {@link MediaSession} for which to do media routing.
     */
    public static InfoMediaManager createInstance(
            Context context,
            @Nullable String packageName,
            @Nullable UserHandle userHandle,
            LocalBluetoothManager localBluetoothManager,
            @Nullable MediaSession.Token token) {
        MediaController mediaController = null;

        if (token != null) {
            mediaController = new MediaController(context, token);
        }

        // The caller is only interested in system routes (headsets, built-in speakers, etc), and is
        // not interested in a specific app's routing. The media routing APIs still require a
        // package name, so we use the package name of the calling app.
        if (TextUtils.isEmpty(packageName)) {
            packageName = context.getPackageName();
        }

        if (userHandle == null) {
            userHandle = android.os.Process.myUserHandle();
        }

        try {
            return new RouterInfoMediaManager(
                    context, packageName, userHandle, localBluetoothManager, mediaController);
        } catch (PackageNotAvailableException ex) {
            // TODO: b/293578081 - Propagate this exception to callers for proper handling.
            Log.w(TAG, "Returning a no-op InfoMediaManager for package " + packageName);
            return new NoOpInfoMediaManager(
                    context, packageName, userHandle, localBluetoothManager, mediaController);
        }
    }

    public void startScan() {
        Log.i(TAG, "startScan()");
        startScanOnRouter();
    }

    private void updateRouteListingPreference() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            RouteListingPreference routeListingPreference =
                    getRouteListingPreference();
            Api34Impl.onRouteListingPreferenceUpdated(routeListingPreference,
                    mPreferenceItemMap);
        }
    }

    public final void stopScan() {
        Log.i(TAG, "stopScan()");
        stopScanOnRouter();
    }

    protected abstract void stopScanOnRouter();

    protected abstract void startScanOnRouter();

    protected abstract void registerRouter();

    protected abstract void unregisterRouter();

    protected abstract void transferToRoute(
            @NonNull MediaRoute2Info route, @NonNull RoutingChangeInfo routingChangeInfo);

    protected abstract void selectRoute(
            @NonNull MediaRoute2Info route, @NonNull RoutingSessionInfo info);

    protected abstract void deselectRoute(
            @NonNull MediaRoute2Info route, @NonNull RoutingSessionInfo info);

    protected abstract void releaseSession(@NonNull RoutingSessionInfo sessionInfo);

    @NonNull
    protected abstract List<MediaRoute2Info> getSelectableRoutes(@NonNull RoutingSessionInfo info);

    @NonNull
    protected abstract List<MediaRoute2Info> getTransferableRoutes(
            @NonNull RoutingSessionInfo info);

    @NonNull
    protected abstract List<MediaRoute2Info> getDeselectableRoutes(
            @NonNull RoutingSessionInfo info);

    @NonNull
    protected abstract List<MediaRoute2Info> getSelectedRoutes(@NonNull RoutingSessionInfo info);

    protected abstract void setSessionVolume(@NonNull RoutingSessionInfo info, int volume);

    protected abstract void setRouteVolume(@NonNull MediaRoute2Info route, int volume);

    @Nullable
    protected abstract RouteListingPreference getRouteListingPreference();

    /**
     * Returns the list of remote {@link RoutingSessionInfo routing sessions} known to the system.
     */
    @NonNull
    protected abstract List<RoutingSessionInfo> getRemoteSessions();

    /**
     * Returns a non-empty list containing the routing sessions associated to the target media app.
     *
     * <p> The first item of the list is always the {@link RoutingSessionInfo#isSystemSession()
     * system session}, followed other remote sessions linked to the target media app.
     */
    @NonNull
    protected abstract List<RoutingSessionInfo> getRoutingSessionsForPackage();

    @Nullable
    protected abstract RoutingSessionInfo getRoutingSessionById(@NonNull String sessionId);

    @NonNull
    protected abstract List<MediaRoute2Info> getAvailableRoutesFromRouter();

    @NonNull
    protected abstract List<MediaRoute2Info> getTransferableRoutes(@NonNull String packageName);

    protected final void rebuildDeviceList() {
        buildAvailableRoutes();
        updateDeviceSuggestion();
    }

    protected final void notifyCurrentConnectedDeviceChanged() {
        MediaDevice device = getCurrentConnectedDevice();
        final String id = device != null ? device.getId() : null;
        dispatchConnectedDeviceChanged(id);
    }

    @RequiresApi(34)
    protected final void notifyRouteListingPreferenceUpdated(
            RouteListingPreference routeListingPreference) {
        Api34Impl.onRouteListingPreferenceUpdated(routeListingPreference, mPreferenceItemMap);
    }

    @VisibleForTesting
    @Nullable
    protected final MediaDevice findMediaDevice(@NonNull String id) {
        return getMediaDevices().stream()
                .filter(device -> device.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @NonNull
    private List<MediaDevice> getMediaDevices() {
        synchronized (mLock) {
            return new ArrayList<>(mMediaDevices);
        }
    }

    /**
     * Registers the specified {@code callback} to receive state updates about routing information.
     *
     * <p>As long as there is a registered {@link MediaDeviceCallback}, {@link InfoMediaManager}
     * will receive state updates from the platform.
     *
     * <p>Call {@link #unregisterCallback(MediaDeviceCallback)} once you no longer need platform
     * updates.
     */
    public final void registerCallback(@NonNull MediaDeviceCallback callback) {
        boolean wasEmpty = mCallbacks.isEmpty();
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
            if (wasEmpty) {
                synchronized (mLock) {
                    mMediaDevices.clear();
                }
                registerRouter();
                if (mMediaController != null) {
                    mMediaController.registerCallback(mMediaControllerCallback);
                }
                updateRouteListingPreference();
                refreshDevices();
            }
        }
    }

    /**
     * Unregisters the specified {@code callback}.
     *
     * @see #registerCallback(MediaDeviceCallback)
     */
    public final void unregisterCallback(@NonNull MediaDeviceCallback callback) {
        if (mCallbacks.remove(callback) && mCallbacks.isEmpty()) {
            if (mMediaController != null) {
                mMediaController.unregisterCallback(mMediaControllerCallback);
            }
            unregisterRouter();
        }
    }

    private void dispatchDeviceListAdded(@NonNull List<MediaDevice> devices) {
        Log.i(TAG, "dispatchDeviceListAdded()");
        if (DEBUG) {
            for (MediaDevice device : devices) {
                Log.d(TAG, device.toString());
            }
        }
        for (MediaDeviceCallback callback : getCallbacks()) {
            callback.onDeviceListAdded(new ArrayList<>(devices));
        }
    }

    private void dispatchConnectedDeviceChanged(String id) {
        Log.i(TAG, "dispatchConnectedDeviceChanged(), id = " + id);
        for (MediaDeviceCallback callback : getCallbacks()) {
            callback.onConnectedDeviceChanged(id);
        }
    }

    protected void dispatchOnRequestFailed(int reason) {
        Log.i(TAG, "dispatchOnRequestFailed(), reason = " + reason);
        for (MediaDeviceCallback callback : getCallbacks()) {
            callback.onRequestFailed(reason);
        }
    }

    private Collection<MediaDeviceCallback> getCallbacks() {
        return new CopyOnWriteArrayList<>(mCallbacks);
    }

    /**
     * Get current device that played media.
     * @return MediaDevice
     */
    MediaDevice getCurrentConnectedDevice() {
        synchronized (mLock) {
            return mCurrentConnectedDevice;
        }
    }

    /* package */ void connectToDevice(MediaDevice device, RoutingChangeInfo routingChangeInfo) {
        Log.i(TAG, "connectToDevice(), device = " + device.getName() + "/" + device.getId());
        if (device.mRouteInfo == null) {
            Log.w(TAG, "Unable to connect. RouteInfo is empty");
            return;
        }

        device.setConnectedRecord();
        transferToRoute(device.mRouteInfo, routingChangeInfo);
    }

    /**
     * Add a MediaDevice to let it play current media.
     *
     * @param device MediaDevice
     * @return If add device successful return {@code true}, otherwise return {@code false}
     */
    boolean addDeviceToPlayMedia(MediaDevice device) {
        Log.i(TAG, "addDeviceToPlayMedia(), device = " + device.getName() + "/" + device.getId());
        final RoutingSessionInfo info = getActiveRoutingSession();
        if (!info.getSelectableRoutes().contains(device.mRouteInfo.getId())) {
            Log.w(TAG, "addDeviceToPlayMedia() Ignoring selecting a non-selectable device : "
                    + device.getName());
            return false;
        }

        selectRoute(device.mRouteInfo, info);
        return true;
    }

    @NonNull
    private RoutingSessionInfo getActiveRoutingSession() {
        // List is never empty.
        final List<RoutingSessionInfo> sessions = getRoutingSessionsForPackage();
        RoutingSessionInfo activeSession = sessions.get(sessions.size() - 1);

        // Logic from MediaRouter2Manager#getRoutingSessionForMediaController
        if (mMediaController == null) {
            return activeSession;
        }

        PlaybackInfo playbackInfo = mMediaController.getPlaybackInfo();
        if (playbackInfo.getPlaybackType() == PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
            // Return system session.
            return sessions.get(0);
        }

        // For PLAYBACK_TYPE_REMOTE.
        String volumeControlId = playbackInfo.getVolumeControlId();
        for (RoutingSessionInfo session : sessions) {
            if (TextUtils.equals(volumeControlId, session.getId())) {
                return session;
            }
            // Workaround for provider not being able to know the unique session ID.
            if (TextUtils.equals(volumeControlId, session.getOriginalId())
                    && TextUtils.equals(
                            mMediaController.getPackageName(), session.getOwnerPackageName())) {
                return session;
            }
        }

        return activeSession;
    }

    boolean isRoutingSessionAvailableForVolumeControl() {
        List<RoutingSessionInfo> sessions = getRoutingSessionsForPackage();

        for (RoutingSessionInfo session : sessions) {
            if (!session.isSystemSession()
                    && session.getVolumeHandling() != MediaRoute2Info.PLAYBACK_VOLUME_FIXED) {
                return true;
            }
        }

        Log.d(TAG, "No routing session for " + mPackageName);
        return false;
    }

    boolean preferRouteListingOrdering() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && Api34Impl.preferRouteListingOrdering(getRouteListingPreference());
    }

    @Nullable
    ComponentName getLinkedItemComponentName() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && TextUtils.isEmpty(
                mPackageName)) {
            return null;
        }
        return Api34Impl.getLinkedItemComponentName(getRouteListingPreference());
    }

    /**
     * Remove a {@code device} from current media.
     *
     * @param device MediaDevice
     * @return If device stop successful return {@code true}, otherwise return {@code false}
     */
    boolean removeDeviceFromPlayMedia(MediaDevice device) {
        Log.i(TAG,
                "removeDeviceFromPlayMedia(), device = " + device.getName() + "/" + device.getId());
        final RoutingSessionInfo info = getActiveRoutingSession();
        if (!info.getSelectedRoutes().contains(device.mRouteInfo.getId())) {
            Log.w(TAG, "removeDeviceFromMedia() Ignoring deselecting a non-deselectable device : "
                    + device.getName());
            return false;
        }

        deselectRoute(device.mRouteInfo, info);
        return true;
    }

    /**
     * Get the current active session's release type.
     *
     * @return the release type of the current active session
     */
    @RoutingSessionInfo.ReleaseType
    int getSessionReleaseType() {
        return getActiveRoutingSession().getReleaseType();
    }

    /**
     * Release session to stop playing media on MediaDevice.
     */
    boolean releaseSession() {
        releaseSession(getActiveRoutingSession());
        return true;
    }

    /* package */ void adjustDeviceVolume(MediaDevice device, int volume) {
        Log.i(TAG, "adjustDeviceVolume(), device = " + device.getName() + "/" + device.getId()
                + " volume = " + volume);
        if (device.mRouteInfo == null) {
            Log.w(TAG, "Unable to set volume. RouteInfo is empty");
            return;
        }
        setRouteVolume(device.mRouteInfo, volume);
    }

    void adjustSessionVolume(RoutingSessionInfo info, int volume) {
        if (info == null) {
            Log.w(TAG, "Unable to adjust session volume. RoutingSessionInfo is empty");
            return;
        }

        setSessionVolume(info, volume);
    }

    /**
     * Adjust the volume of {@link android.media.RoutingSessionInfo}.
     *
     * @param volume the value of volume
     */
    void adjustSessionVolume(int volume) {
        Log.d(TAG, "adjustSessionVolume() adjust volume: " + volume + ", with : " + mPackageName);
        setSessionVolume(getActiveRoutingSession(), volume);
    }

    /**
     * Gets the maximum volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return  maximum volume of the session, and return -1 if not found.
     */
    public int getSessionVolumeMax() {
        return getActiveRoutingSession().getVolumeMax();
    }

    /**
     * Gets the current volume of the {@link android.media.RoutingSessionInfo}.
     *
     * @return current volume of the session, and return -1 if not found.
     */
    public int getSessionVolume() {
        return getActiveRoutingSession().getVolume();
    }

    @Nullable
    CharSequence getSessionName() {
        return getActiveRoutingSession().getName();
    }

    @Nullable
    public SuggestedDeviceState getSuggestedDevice() {
        synchronized (mLock) {
            return mSuggestedDeviceState;
        }
    }

    /** Requests a suggestion from other routers. */
    public abstract void requestDeviceSuggestion();

    @TargetApi(Build.VERSION_CODES.R)
    boolean shouldEnableVolumeSeekBar(RoutingSessionInfo sessionInfo) {
        return sessionInfo.isSystemSession() // System sessions are not remote
                || sessionInfo.getVolumeHandling() != MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
    }

    protected void notifyDeviceSuggestionUpdated(
            String suggestingPackageName, @Nullable List<SuggestedDeviceInfo> suggestions) {
        synchronized (mLock) {
            if (suggestions == null) {
                mSuggestedDeviceMap.remove(suggestingPackageName);
            } else {
                mSuggestedDeviceMap.put(suggestingPackageName, suggestions);
            }
        }
        updateDeviceSuggestion();
    }

    private void updateDeviceSuggestion() {
        if (!com.android.media.flags.Flags.enableSuggestedDeviceApi()) {
            return;
        }
        if (updateSuggestedDeviceState()) {
            dispatchOnSuggestedDeviceUpdated();
        }
        if (updateMediaDevicesSuggestionState()) {
            dispatchDeviceListAdded(getMediaDevices());
        }
    }

    private boolean updateSuggestedDeviceState() {
        if (!com.android.media.flags.Flags.enableSuggestedDeviceApi()) {
            return false;
        }
        SuggestedDeviceInfo topSuggestion = null;
        SuggestedDeviceState newSuggestedDeviceState = null;
        SuggestedDeviceState previousState = getSuggestedDevice();
        List<SuggestedDeviceInfo> suggestions = getSuggestions();
        if (suggestions != null && !suggestions.isEmpty()) {
            topSuggestion = suggestions.get(0);
        }
        if (topSuggestion != null) {
            synchronized (mLock) {
                for (MediaDevice device : mMediaDevices) {
                    if (Objects.equals(device.getId(), topSuggestion.getRouteId())) {
                        newSuggestedDeviceState =
                                new SuggestedDeviceState(topSuggestion, device.getState());
                        break;
                    }
                }
            }
            if (newSuggestedDeviceState == null) {
                if (previousState != null
                        && topSuggestion
                                .getRouteId()
                                .equals(previousState.getSuggestedDeviceInfo().getRouteId())) {
                    return false;
                }
                newSuggestedDeviceState = new SuggestedDeviceState(topSuggestion);
            }
        }
        if (newSuggestedDeviceState != null && isSuggestedDeviceSelected(newSuggestedDeviceState)) {
            newSuggestedDeviceState = null;
        }
        if (!Objects.equals(previousState, newSuggestedDeviceState)) {
            synchronized (mLock) {
                mSuggestedDeviceState = newSuggestedDeviceState;
            }
            return true;
        }
        return false;
    }

    private boolean isSuggestedDeviceSelected(
            @NonNull SuggestedDeviceState newSuggestedDeviceState) {
        synchronized (mLock) {
            return mMediaDevices.stream().anyMatch(device ->
                    device.isSelected()
                            && Objects.equals(
                            device.getId(),
                            newSuggestedDeviceState
                                    .getSuggestedDeviceInfo()
                                    .getRouteId()));
        }
    }

    final void onConnectionAttemptedForSuggestion(@NonNull SuggestedDeviceState suggestion) {
        synchronized (mLock) {
            if (!Objects.equals(suggestion, mSuggestedDeviceState)) {
                return;
            }
            if (mSuggestedDeviceState.getConnectionState() != STATE_DISCONNECTED
                    && mSuggestedDeviceState.getConnectionState() != STATE_CONNECTING_FAILED) {
                return;
            }
            mSuggestedDeviceState =
                    new SuggestedDeviceState(
                            mSuggestedDeviceState.getSuggestedDeviceInfo(), STATE_CONNECTING);
        }
        dispatchOnSuggestedDeviceUpdated();
    }

    final void onConnectionAttemptCompletedForSuggestion(
            @NonNull SuggestedDeviceState suggestion, boolean success) {
        synchronized (mLock) {
            if (!Objects.equals(suggestion, mSuggestedDeviceState)) {
                return;
            }
            int state = success ? STATE_CONNECTED : STATE_CONNECTING_FAILED;
            mSuggestedDeviceState =
                    new SuggestedDeviceState(mSuggestedDeviceState.getSuggestedDeviceInfo(), state);
        }
        dispatchOnSuggestedDeviceUpdated();
    }

    private void dispatchOnSuggestedDeviceUpdated() {
        SuggestedDeviceState state = getSuggestedDevice();
        Log.i(TAG, "dispatchOnSuggestedDeviceUpdated(), state: " + state);
        for (MediaDeviceCallback callback : getCallbacks()) {
            callback.onSuggestedDeviceUpdated(state);
        }
    }

    @Nullable
    private List<SuggestedDeviceInfo> getSuggestions() {
        // Give suggestions in the following order
        // 1. Suggestions from the local router
        // 2. Suggestions from the proxy router if only one proxy router is providing suggestions
        // 3. No suggestion at all if multiple proxy routers are providing suggestions.
        synchronized (mLock) {
            List<SuggestedDeviceInfo> suggestions = mSuggestedDeviceMap.get(mPackageName);
            if (suggestions != null) {
                return suggestions;
            }
            if (mSuggestedDeviceMap.size() == 1) {
                for (List<SuggestedDeviceInfo> packageSuggestions : mSuggestedDeviceMap.values()) {
                    if (packageSuggestions != null) {
                        return packageSuggestions;
                    }
                }
            }
        }
        return null;
    }

    // Go through all current MediaDevices, and update the ones that are suggested.
    private boolean updateMediaDevicesSuggestionState() {
        if (!com.android.media.flags.Flags.enableSuggestedDeviceApi()) {
            return false;
        }
        Set<String> suggestedDevices = new HashSet<>();
        // Prioritize suggestions from the package, otherwise pick any.
        List<SuggestedDeviceInfo> suggestions = getSuggestions();
        if (suggestions != null) {
            for (SuggestedDeviceInfo suggestion : suggestions) {
                suggestedDevices.add(suggestion.getRouteId());
            }
        }
        boolean didUpdate = false;
        synchronized (mLock) {
            for (MediaDevice device : mMediaDevices) {
                if (device.isSuggestedDevice()) {
                    if (!suggestedDevices.contains(device.getId())) {
                        device.setIsSuggested(false);
                        // Case 1: Device was suggested only by setDeviceSuggestions(), and has been
                        // updated to no longer be suggested.
                        if (!device.isSuggestedByRouteListingPreferences()) {
                            didUpdate = true;
                        }
                        // Case 2: Device was suggested by both setDeviceSuggestions() and RLP.
                        // Since it's still suggested by RLP, no update.
                    } else {
                        // Case 3: Device was suggested (either by RLP or by
                        // setDeviceSuggestions()), and should still be suggested.
                        device.setIsSuggested(true);
                    }
                } else {
                    if (suggestedDevices.contains(device.getId())) {
                        // Case 4: Device was not suggested by either RLP or setDeviceSuggestions()
                        // but is now suggested.
                        device.setIsSuggested(true);
                        didUpdate = true;
                    } else {
                        // Case 5: Device was not suggested by either RLP or setDeviceSuggestions()
                        // and is still not suggested.
                        device.setIsSuggested(false);
                    }
                }
            }
        }

        return didUpdate;
    }

    protected final void refreshDevices() {
        rebuildDeviceList();
        dispatchDeviceListAdded(getMediaDevices());
    }

    // MediaRoute2Info.getType was made public on API 34, but exists since API 30.
    @SuppressWarnings("NewApi")
    private void buildAvailableRoutes() {
        synchronized (mLock) {
            mMediaDevices.clear();
            RoutingSessionInfo activeSession = getActiveRoutingSession();

            for (MediaRoute2Info route : getAvailableRoutes(activeSession)) {
                addMediaDeviceLocked(route, activeSession);
            }

            // In practice, mMediaDevices should always have at least one route.
            if (!mMediaDevices.isEmpty()) {
                // First device on the list is always the first selected route.
                mCurrentConnectedDevice = mMediaDevices.get(0);
            }
        }
    }

    private List<MediaRoute2Info> getAvailableRoutes(
            RoutingSessionInfo activeSession) {
        List<MediaRoute2Info> availableRoutes = new ArrayList<>();

        List<MediaRoute2Info> selectedRoutes = getSelectedRoutes(activeSession);
        availableRoutes.addAll(selectedRoutes);
        availableRoutes.addAll(getSelectableRoutes(activeSession));

        final List<MediaRoute2Info> transferableRoutes = getTransferableRoutes(mPackageName);
        for (MediaRoute2Info transferableRoute : transferableRoutes) {
            boolean alreadyAdded = false;
            for (MediaRoute2Info mediaRoute2Info : availableRoutes) {
                if (TextUtils.equals(transferableRoute.getId(), mediaRoute2Info.getId())) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                availableRoutes.add(transferableRoute);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            RouteListingPreference routeListingPreference = getRouteListingPreference();
            if (routeListingPreference != null) {
                availableRoutes = Api34Impl.arrangeRouteListByPreference(selectedRoutes,
                        getAvailableRoutesFromRouter(),
                        routeListingPreference);
            }
            return Api34Impl.filterDuplicatedIds(availableRoutes);
        } else {
            return availableRoutes;
        }
    }

    // MediaRoute2Info.getType was made public on API 34, but exists since API 30.
    @SuppressWarnings("NewApi")
    @GuardedBy("mLock")
    @VisibleForTesting
    void addMediaDeviceLocked(@NonNull MediaRoute2Info route,
            @NonNull RoutingSessionInfo activeSession) {
        DynamicRouteAttributes dynamicRouteAttributes =
                getDynamicRouteAttributes(activeSession, route);
        MediaDevice mediaDevice = createMediaDeviceFromRouteLocked(route, dynamicRouteAttributes);
        if (mediaDevice != null) {
            if (mediaDevice.isSelected()) {
                mediaDevice.setState(STATE_SELECTED);
            } else if (route.getConnectionState() == CONNECTION_STATE_CONNECTING) {
                mediaDevice.setState(STATE_CONNECTING);
            }
            mMediaDevices.add(mediaDevice);
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private MediaDevice createMediaDeviceFromRouteLocked(@NonNull MediaRoute2Info route,
            @NonNull DynamicRouteAttributes dynamicRouteAttributes) {
        final int deviceType = route.getType();
        MediaDevice mediaDevice = null;
        if (isInfoMediaDevice(deviceType)) {
            mediaDevice = new InfoMediaDevice(mContext, route, dynamicRouteAttributes,
                    mPreferenceItemMap.get(route.getId()));

        } else if (isPhoneMediaDevice(deviceType)) {
            mediaDevice = new PhoneMediaDevice(mContext, route, dynamicRouteAttributes,
                    mPreferenceItemMap.getOrDefault(route.getId(), null));

        } else if (isBluetoothMediaDevice(deviceType)) {
            if (route.getAddress() == null) {
                Log.e(TAG, "Ignoring bluetooth route with no set address: " + route);
            } else {
                final BluetoothDevice device =
                        BluetoothAdapter.getDefaultAdapter().getRemoteDevice(route.getAddress());
                final CachedBluetoothDevice cachedDevice =
                        mBluetoothManager.getCachedDeviceManager().findDevice(device);
                if (cachedDevice != null) {
                    mediaDevice = new BluetoothMediaDevice(mContext, cachedDevice, route,
                            dynamicRouteAttributes,
                            mPreferenceItemMap.getOrDefault(route.getId(), null));
                }
            }
        } else if (isComplexMediaDevice(deviceType)) {
            mediaDevice = new ComplexMediaDevice(mContext, route, dynamicRouteAttributes,
                    mPreferenceItemMap.get(route.getId()));

        } else {
            Log.w(TAG, "createRouteToMediaDevice() unknown device type : " + deviceType);
        }

        return mediaDevice;
    }

    @NonNull
    private DynamicRouteAttributes getDynamicRouteAttributes(
            @NonNull RoutingSessionInfo activeSession, @NonNull MediaRoute2Info route) {
        Predicate<MediaRoute2Info> isSameRoute = r -> r.getId().equals(route.getId());
        return new DynamicRouteAttributes(
                getTransferableRoutes(activeSession).stream().anyMatch(isSameRoute),
                getSelectedRoutes(activeSession).stream().anyMatch(isSameRoute),
                getSelectableRoutes(activeSession).stream().anyMatch(isSameRoute),
                getDeselectableRoutes(activeSession).stream().anyMatch(isSameRoute)
        );
    }

    /** Updates the state of the device and updates liteners of the updated device state. */
    public void setDeviceState(MediaDevice device, @LocalMediaManager.MediaDeviceState int state) {
        if (device.getState() == state) {
            return;
        }
        device.setState(state);
        if (device.isSuggestedDevice()) {
            if (updateSuggestedDeviceState()) {
                dispatchOnSuggestedDeviceUpdated();
            }
        }
    }

    private static boolean isInfoMediaDevice(int deviceType) {
        switch (deviceType) {
            case TYPE_UNKNOWN:
            case TYPE_REMOTE_TV:
            case TYPE_REMOTE_SPEAKER:
            case TYPE_GROUP:
            case TYPE_REMOTE_TABLET:
            case TYPE_REMOTE_TABLET_DOCKED:
            case TYPE_REMOTE_COMPUTER:
            case TYPE_REMOTE_GAME_CONSOLE:
            case TYPE_REMOTE_CAR:
            case TYPE_REMOTE_SMARTWATCH:
            case TYPE_REMOTE_SMARTPHONE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isPhoneMediaDevice(int deviceType) {
        switch (deviceType) {
            case TYPE_BUILTIN_SPEAKER:
            case TYPE_USB_DEVICE:
            case TYPE_USB_HEADSET:
            case TYPE_USB_ACCESSORY:
            case TYPE_DOCK:
            case TYPE_HDMI:
            case TYPE_HDMI_ARC:
            case TYPE_HDMI_EARC:
            case TYPE_LINE_DIGITAL:
            case TYPE_LINE_ANALOG:
            case TYPE_AUX_LINE:
            case TYPE_WIRED_HEADSET:
            case TYPE_WIRED_HEADPHONES:
                return true;
            default:
                return false;
        }
    }

    private static boolean isBluetoothMediaDevice(int deviceType) {
        switch (deviceType) {
            case TYPE_HEARING_AID:
            case TYPE_BLUETOOTH_A2DP:
            case TYPE_BLE_HEADSET:
                return true;
            default:
                return false;
        }
    }

    private static boolean isComplexMediaDevice(int deviceType) {
        return deviceType == TYPE_REMOTE_AUDIO_VIDEO_RECEIVER;
    }

    private static Drawable getDrawableForSuggestion(
            Context context, SuggestedDeviceState suggestion) {
        if (suggestion.getConnectionState() == STATE_CONNECTING_FAILED) {
            return context.getDrawable(android.R.drawable.ic_info);
        }
        int deviceType = suggestion.getSuggestedDeviceInfo().getType();
        if (isInfoMediaDevice(deviceType)) {
            return context.getDrawable(InfoMediaDevice.getDrawableResIdByType(deviceType));
        }
        if (isPhoneMediaDevice(deviceType)) {
            return context.getDrawable(
                    new DeviceIconUtil(context).getIconResIdFromMediaRouteType(deviceType));
        }
        return context.getDrawable(R.drawable.ic_media_speaker_device);
    }

    @RequiresApi(34)
    static class Api34Impl {
        @DoNotInline
        static List<MediaRoute2Info> filterDuplicatedIds(List<MediaRoute2Info> infos) {
            List<MediaRoute2Info> filteredInfos = new ArrayList<>();
            Set<String> foundDeduplicationIds = new HashSet<>();
            for (MediaRoute2Info mediaRoute2Info : infos) {
                if (!Collections.disjoint(mediaRoute2Info.getDeduplicationIds(),
                        foundDeduplicationIds)) {
                    continue;
                }
                filteredInfos.add(mediaRoute2Info);
                foundDeduplicationIds.addAll(mediaRoute2Info.getDeduplicationIds());
            }
            return filteredInfos;
        }

        /**
         * Returns an ordered list of available devices based on the provided {@code
         * routeListingPreferenceItems}.
         *
         * <p>The resulting order is:
         *
         * <ol>
         *   <li>Selected routes sorted by route listing preference.
         *   <li>Selected routes not defined by route listing preference.
         *   <li>Not-selected system routes.
         *   <li>Not-selected, non-system, available routes sorted by route listing preference.
         * </ol>
         *
         * @param selectedRoutes List of currently selected routes.
         * @param availableRoutes List of available routes that match the app's requested route
         *     features.
         * @param routeListingPreference Preferences provided by the app to determine route order.
         */
        @DoNotInline
        static List<MediaRoute2Info> arrangeRouteListByPreference(
                List<MediaRoute2Info> selectedRoutes,
                List<MediaRoute2Info> availableRoutes,
                RouteListingPreference routeListingPreference) {
            final List<RouteListingPreference.Item> routeListingPreferenceItems =
                    routeListingPreference.getItems();

            Set<String> sortedRouteIds = new LinkedHashSet<>();
            Set<String> selectedRouteIds = new HashSet<>();

            if (preferRouteListingOrdering(routeListingPreference)) {
                // Add selected RLP items first
                for (MediaRoute2Info selectedRoute : selectedRoutes) {
                    selectedRouteIds.add(selectedRoute.getId());
                }
                for (RouteListingPreference.Item item: routeListingPreferenceItems) {
                    if (selectedRouteIds.contains(item.getRouteId())) {
                        sortedRouteIds.add(item.getRouteId());
                    }
                }
            }

            // Add selected routes first.
            if (sortedRouteIds.size() != selectedRoutes.size()) {
                for (MediaRoute2Info selectedRoute : selectedRoutes) {
                    sortedRouteIds.add(selectedRoute.getId());
                }
            }

            // Add not-yet-added system routes.
            for (MediaRoute2Info availableRoute : availableRoutes) {
                if (availableRoute.isSystemRoute()) {
                    sortedRouteIds.add(availableRoute.getId());
                }
            }

            // Create a mapping from id to route to avoid a quadratic search.
            Map<String, MediaRoute2Info> idToRouteMap =
                    Stream.concat(selectedRoutes.stream(), availableRoutes.stream())
                            .collect(
                                    Collectors.toMap(
                                            MediaRoute2Info::getId,
                                            Function.identity(),
                                            (route1, route2) -> route1));

            // Add not-selected routes that match RLP items. All system routes have already been
            // added at this point.
            for (RouteListingPreference.Item item : routeListingPreferenceItems) {
                MediaRoute2Info route = idToRouteMap.get(item.getRouteId());
                if (route != null) {
                    sortedRouteIds.add(route.getId());
                }
            }

            return sortedRouteIds.stream().map(idToRouteMap::get).collect(Collectors.toList());
        }

        @DoNotInline
        static boolean preferRouteListingOrdering(RouteListingPreference routeListingPreference) {
            return routeListingPreference != null
                    && !routeListingPreference.getUseSystemOrdering();
        }

        @DoNotInline
        @Nullable
        static ComponentName getLinkedItemComponentName(
                RouteListingPreference routeListingPreference) {
            return routeListingPreference == null ? null
                    : routeListingPreference.getLinkedItemComponentName();
        }

        @DoNotInline
        static void onRouteListingPreferenceUpdated(
                RouteListingPreference routeListingPreference,
                Map<String, RouteListingPreference.Item> preferenceItemMap) {
            Log.i(TAG, "onRouteListingPreferenceUpdated(), hasRLP: " + (routeListingPreference
                    != null));
            if (DEBUG) {
                if (routeListingPreference != null) {
                    Log.d(TAG, "RouteListingPreference. useSystemOrder = "
                            + routeListingPreference.getUseSystemOrdering());
                    for (RouteListingPreference.Item rlpItem : routeListingPreference.getItems()) {
                        Log.d(TAG, rlpItem.toString());
                    }
                }
            }
            preferenceItemMap.clear();
            if (routeListingPreference != null) {
                routeListingPreference.getItems().forEach((item) ->
                        preferenceItemMap.put(item.getRouteId(), item));
            }
        }
    }

    private final class MediaControllerCallback extends MediaController.Callback {
        @Override
        public void onSessionDestroyed() {
            mMediaController = null;
            refreshDevices();
        }

        @Override
        public void onAudioInfoChanged(@NonNull PlaybackInfo info) {
            if (info.getPlaybackType() != mLastKnownPlaybackInfo.getPlaybackType()
                    || !TextUtils.equals(
                            info.getVolumeControlId(),
                            mLastKnownPlaybackInfo.getVolumeControlId())) {
                refreshDevices();
            }
            mLastKnownPlaybackInfo = info;
        }
    }
}
