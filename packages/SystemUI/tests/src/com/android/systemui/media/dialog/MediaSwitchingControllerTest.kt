/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.media.dialog

import android.app.KeyguardManager
import android.app.Notification
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.AudioDeviceAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaRoute2Info
import android.media.NearbyDevice
import android.media.RouteListingPreference
import android.media.RoutingChangeInfo
import android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER
import android.media.RoutingSessionInfo
import android.media.session.ISessionController
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.PowerExemptionManager
import android.os.UserHandle
import android.permission.flags.Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.annotations.UsesFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.notification.StatusBarNotification
import android.testing.TestableLooper
import android.view.View
import androidx.core.graphics.drawable.IconCompat
import androidx.test.filters.SmallTest
import com.android.media.flags.Flags
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager
import com.android.settingslib.media.InfoMediaManager
import com.android.settingslib.media.InputMediaDevice
import com.android.settingslib.media.InputRouteManager
import com.android.settingslib.media.LocalMediaManager
import com.android.settingslib.media.MediaDevice
import com.android.settingslib.media.MissingPermissionsInfo
import com.android.settingslib.volume.data.repository.AudioSharingRepository
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.media.dialog.MediaItem.DeviceGroupMediaItem
import com.android.systemui.media.dialog.MediaItem.DeviceMediaItem
import com.android.systemui.media.dialog.MediaItem.GroupDividerMediaItem
import com.android.systemui.media.nearby.NearbyMediaDevicesManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.kotlin.JavaAdapter
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.panel.domain.interactor.VolumePanelGlobalStateInteractor
import com.android.systemui.volume.panel.domain.interactor.volumePanelGlobalStateInteractor
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlin.jvm.java
import kotlin.test.fail
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.same
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@UsesFlags(Flags::class)
class MediaSwitchingControllerTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val mDialogTransitionAnimator = mock<DialogTransitionAnimator>()
    private val mActivityTransitionAnimatorController =
        mock<ActivityTransitionAnimator.Controller>()
    private val mNearbyMediaDevicesManager = mock<NearbyMediaDevicesManager>()
    private val mPackageName = mContext.packageName
    private val mPlaybackState = mock<PlaybackState>()
    private val mUserHandle = UserHandle.of(0)

    private val mSessionMediaController =
        mock<MediaController> {
            on { packageName } doReturn mPackageName
            on { playbackState } doReturn mPlaybackState
        }
    private val mMediaSessionManager =
        mock<MediaSessionManager> {
            on { getActiveSessionsForUser(eq(null), eq(mUserHandle)) }
                .doReturn(listOf(mSessionMediaController))
        }

    private val mAssistantProfile = mock<LocalBluetoothLeBroadcastAssistant>()
    private val mProfileManager =
        mock<LocalBluetoothProfileManager> {
            on { leAudioBroadcastAssistantProfile } doReturn mAssistantProfile
        }
    private val mLocalBluetoothManager =
        mock<LocalBluetoothManager> { on { this.profileManager } doReturn mProfileManager }

    private val mCb = mock<MediaSwitchingController.Callback>()
    private val mMediaDevice1 = mock<MediaDevice> { on { id } doReturn TEST_DEVICE_1_ID }
    private val mMediaDevice2 = mock<MediaDevice> { on { id } doReturn TEST_DEVICE_2_ID }
    private val mMediaDevice3 = mock<MediaDevice>()
    private val mMediaDevice4 = mock<MediaDevice>()
    private val mMediaDevice5 = mock<MediaDevice>()
    private val mMediaDevice6 = mock<MediaDevice>()
    private val mMediaDevice7 = mock<MediaDevice>()
    private val mMediaDevices = mutableListOf(mMediaDevice1, mMediaDevice2)
    private val mNearbyDevice1 =
        mock<NearbyDevice> {
            on { mediaRoute2Id } doReturn TEST_DEVICE_1_ID
            on { rangeZone } doReturn NearbyDevice.RANGE_FAR
        }
    private val mNearbyDevice2 =
        mock<NearbyDevice> {
            on { mediaRoute2Id } doReturn TEST_DEVICE_2_ID
            on { rangeZone } doReturn NearbyDevice.RANGE_CLOSE
        }
    private val mNearbyDevices = listOf(mNearbyDevice1, mNearbyDevice2)
    private val mediaDescription =
        MediaDescription.Builder().setTitle(TEST_SONG).setSubtitle(TEST_ARTIST).build()
    private val mMediaMetadata =
        mock<MediaMetadata> { on { description } doReturn mediaDescription }
    private val mRemoteSessionInfo = mock<RoutingSessionInfo>()
    private val mStarter = mock<ActivityStarter>()
    private val mAudioManager =
        mock<AudioManager> {
            on { getDevices(AudioManager.GET_DEVICES_INPUTS) } doReturn emptyArray()
        }
    private val mKeyguardManager = mock<KeyguardManager>()
    private val mController = mock<ActivityTransitionAnimator.Controller>()
    private val mPowerExemptionManager = mock<PowerExemptionManager>()
    private val mNotifCollection = mock<CommonNotifCollection>()
    private val mPackageManager = mock<PackageManager>()
    private val mDrawable = mock<Drawable>()
    private val mInfoMediaManager = mock<InfoMediaManager>()

    private val mUserTracker = mock<UserTracker> { on { this.userHandle } doReturn mUserHandle }
    private val mAudioSharingRepository = mock<AudioSharingRepository>()
    private val mExpandedAudioTileDetailsFeatureInteractor =
        mock<ExpandedAudioTileDetailsFeatureInteractor> { on { isEnabled() } doReturn false }

    private val mKosmos: Kosmos = this.testKosmos()
    private val mJavaAdapter = mock<JavaAdapter>()
    private val mInAudioSharingCaptor = argumentCaptor<Consumer<Boolean>>()

    private val mClock = FakeSystemClock()
    private val mFakeBackgroundExecutor = FakeExecutor(mClock)

    private val mDialogLaunchView: View = mock<View>()
    private val mCallback: MediaSwitchingController.Callback =
        mock<MediaSwitchingController.Callback>()

    val mNotification: Notification = mock<Notification>()
    private val mVolumePanelGlobalStateInteractor: VolumePanelGlobalStateInteractor =
        mKosmos.volumePanelGlobalStateInteractor

    private lateinit var mSpyContext: Context
    private lateinit var mMediaSwitchingController: MediaSwitchingController
    private lateinit var mLocalMediaManager: LocalMediaManager
    private lateinit var mInputRouteManager: InputRouteManager
    private val mRoutingSessionInfos: MutableList<RoutingSessionInfo> = ArrayList()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        mContext.setMockPackageManager(mPackageManager)
        mSpyContext =
            spy(mContext).stub {
                on { getSystemService(MediaSessionManager::class.java) }
                    .doReturn(mMediaSessionManager)
            }

        mMediaSwitchingController = createDefaultMediaSwitchingController()

        mLocalMediaManager =
            spy(mMediaSwitchingController.mLocalMediaManager) {
                on { isPreferenceRouteListingExist } doReturn false
            }
        mMediaSwitchingController.mLocalMediaManager = mLocalMediaManager

        mInputRouteManager = spy(InputRouteManager(mContext, mAudioManager, mInfoMediaManager))
        mMediaSwitchingController.mInputRouteManager = mInputRouteManager

        setupNotificationMock()
    }

    @Test
    fun start_verifyLocalMediaManagerInit() {
        mMediaSwitchingController.start(mCb)

        verify(mLocalMediaManager).registerCallback(mMediaSwitchingController)
        verify(mLocalMediaManager).startScan()
    }

    @Test
    fun stop_verifyLocalMediaManagerDeinit() {
        mMediaSwitchingController.start(mCb)
        reset(mLocalMediaManager)

        mMediaSwitchingController.stop()

        verify(mLocalMediaManager).unregisterCallback(mMediaSwitchingController)
        verify(mLocalMediaManager).stopScan()
    }

    @Test
    fun start_notificationNotFound_mediaControllerInitFromSession() {
        mMediaSwitchingController.start(mCb)

        verify(mSessionMediaController).registerCallback(any())
    }

    @Test
    fun start_MediaNotificationFound_mediaControllerNotInitFromSession() {
        whenever(mNotification.isMediaNotification).thenReturn(true)
        mMediaSwitchingController.start(mCb)

        verify(mSessionMediaController, never()).registerCallback(any())
        verifyNoMoreInteractions(mMediaSessionManager)
    }

    @Test
    fun start_withoutPackageName_verifyMediaControllerInit() {
        mMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        mMediaSwitchingController.start(mCb)

        verify(mSessionMediaController, never()).registerCallback(any())
    }

    @Test
    fun start_nearbyMediaDevicesManagerNotNull_registersNearbyDevicesCallback() {
        mMediaSwitchingController.start(mCb)

        verify(mNearbyMediaDevicesManager).registerNearbyDevicesCallback(any())
    }

    @Test
    fun stop_withPackageName_verifyMediaControllerDeinit() {
        mMediaSwitchingController.start(mCb)
        reset(mSessionMediaController)

        mMediaSwitchingController.stop()

        verify(mSessionMediaController).unregisterCallback(any())
    }

    @Test
    fun stop_withoutPackageName_verifyMediaControllerDeinit() {
        mMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        mMediaSwitchingController.start(mCb)

        mMediaSwitchingController.stop()

        verify(mSessionMediaController, never()).unregisterCallback(any())
    }

    @Test
    fun stop_nearbyMediaDevicesManagerNotNull_unregistersNearbyDevicesCallback() {
        mMediaSwitchingController.start(mCb)
        reset(mSessionMediaController)

        mMediaSwitchingController.stop()

        verify(mNearbyMediaDevicesManager).unregisterNearbyDevicesCallback(any())
    }

    @Test
    fun tryToLaunchMediaApplication_nullIntent_skip() {
        mMediaSwitchingController.tryToLaunchMediaApplication(mDialogLaunchView)

        verify(mCb, never()).dismissDialog()
    }

    @Test
    fun tryToLaunchMediaApplication_intentNotNull_startActivity() {
        whenever(
                mDialogTransitionAnimator.createActivityTransitionController(
                    any<View>(),
                    anyOrNull(),
                )
            )
            .thenReturn(mController)
        val intent = Intent(mPackageName)
        doReturn(intent).whenever(mPackageManager).getLaunchIntentForPackage(mPackageName)
        mMediaSwitchingController.start(mCallback)

        mMediaSwitchingController.tryToLaunchMediaApplication(mDialogLaunchView)

        verify(mStarter).startActivity(any<Intent>(), any(), eq(mController))
    }

    @Test
    fun tryToLaunchInAppRoutingIntent_componentNameNotNull_startActivity() {
        whenever(
                mDialogTransitionAnimator.createActivityTransitionController(
                    any<View>(),
                    anyOrNull(),
                )
            )
            .thenReturn(mController)
        mMediaSwitchingController.start(mCallback)
        whenever(mLocalMediaManager.linkedItemComponentName)
            .thenReturn(ComponentName(mPackageName, ""))

        mMediaSwitchingController.tryToLaunchInAppRoutingIntent(TEST_DEVICE_1_ID, mDialogLaunchView)

        verify(mStarter).startActivity(any<Intent>(), any(), eq(mController))
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun tryToLaunchMissingPermissionsResolveIntent_noMissingPermissions_doesNothing() {
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(null)
        mMediaSwitchingController.start(mCallback)

        mMediaSwitchingController.tryToLaunchMissingPermissionsResolveIntent()

        verify(mCallback, never()).dismissDialog()
        verify(mSpyContext, never()).startActivityAsUser(any(), any())
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun tryToLaunchMissingPermissionsResolveIntent_hasMissingPermissions_launchesActivity() {
        val componentName = ComponentName(mPackageName, "class")
        val perms = setOf("perm1", "perm2")
        val info = MissingPermissionsInfo(componentName, perms)
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(info)
        val user = UserHandle.of(123)
        whenever(mLocalMediaManager.userHandle).thenReturn(user)
        mMediaSwitchingController.start(mCallback)

        mMediaSwitchingController.tryToLaunchMissingPermissionsResolveIntent()

        verify(mCallback).dismissDialog()
        val intentCaptor = argumentCaptor<Intent>()
        verify(mSpyContext).startActivityAsUser(intentCaptor.capture(), eq(user))
        assertThat(intentCaptor.firstValue.action)
            .isEqualTo(RouteListingPreference.ACTION_RESOLVE_MISSING_PERMISSIONS)
    }

    @Test
    fun onDevicesUpdated_unregistersNearbyDevicesCallback() {
        mMediaSwitchingController.start(mCb)

        mMediaSwitchingController.onDevicesUpdated(listOf())

        verify(mNearbyMediaDevicesManager).unregisterNearbyDevicesCallback(any())
    }

    @Test
    fun onDeviceListUpdate_withNearbyDevices_updatesRangeInformation() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        verify(mMediaDevice1).setRangeZone(NearbyDevice.RANGE_FAR)
        verify(mMediaDevice2).setRangeZone(NearbyDevice.RANGE_CLOSE)
    }

    @Test
    fun onDeviceListUpdate_withNearbyDevices_rankByRangeInformation() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaDevices[0].id).isEqualTo(TEST_DEVICE_1_ID)
    }

    @Test
    fun routeProcessSupport_onDeviceListUpdate_preferenceExist_NotUpdatesRangeInformation() {
        whenever(mLocalMediaManager.isPreferenceRouteListingExist).thenReturn(true)
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDevicesUpdated(mNearbyDevices)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        verify(mMediaDevice1, never()).setRangeZone(any())
        verify(mMediaDevice2, never()).setRangeZone(any())
    }

    @Test
    fun onDeviceListUpdate_repeatedWithinThresholdPeriod_rearrangesList() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        var items = mMediaSwitchingController.getMediaItemList()
        assertThat((items[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))
        assertThat((items[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)
        assertThat((items[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)

        mClock.advanceTime(1500) // < 2 seconds.

        // Make the second device suggested
        whenever(mMediaDevice2.isSuggestedDevice).thenReturn(true)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        // The list is rearranged - The "Suggested" section added and the order got updated.
        items = mMediaSwitchingController.getMediaItemList()
        assertThat((items[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_suggested))
        assertThat((items[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)
        assertThat((items[2] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))
        assertThat((items[3] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)
    }

    @Test
    fun onDeviceListUpdate_repeatedAfterThresholdPeriod_appendsItemsToTheList() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        var items = mMediaSwitchingController.getMediaItemList()
        assertThat((items[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))
        assertThat((items[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)
        assertThat((items[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)

        mClock.advanceTime(2100) // > 2 seconds.

        // Make the second device suggested
        whenever(mMediaDevice2.isSuggestedDevice).thenReturn(true)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        // The list remains unchanged.
        items = mMediaSwitchingController.getMediaItemList()
        assertThat((items[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))
        assertThat((items[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)
        assertThat((items[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)
    }

    @Test
    fun onDeviceListUpdate_verifyDeviceListCallback() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.containsAll(mMediaDevices)).isTrue()
        assertThat(devices.size).isEqualTo(mMediaDevices.size)
        // There should be 1 non-MediaDevice item - the "Speakers & Display" title.
        assertThat(mMediaSwitchingController.getMediaItemList().size)
            .isEqualTo(mMediaDevices.size + 1)
        verify(mCb).onDeviceListChanged()
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun onDeviceListUpdate_verifyDeviceListCallback_inputRouting() {
        enableInputRoutingConfig()
        mMediaSwitchingController = createDefaultMediaSwitchingController()
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.containsAll(mMediaDevices)).isTrue()
        assertThat(devices.size).isEqualTo(mMediaDevices.size)
        // When input routing is enabled, there should be 3 non-MediaDevice items: one for
        // the "Output" title, one for the "Speakers & Displays" title, and one for the "Input"
        // title.
        assertThat(mMediaSwitchingController.getMediaItemList().size)
            .isEqualTo(mMediaDevices.size + 3)
        verify(mCb).onDeviceListChanged()
    }

    @Test
    fun advanced_onDeviceListUpdateWithConnectedDeviceRemote_verifyItemSize() {
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1)
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.containsAll(mMediaDevices)).isTrue()
        assertThat(devices.size).isEqualTo(mMediaDevices.size)
        // There should be 1 non-MediaDevice item: the "Speakers & Display" title.
        assertThat(mMediaSwitchingController.getMediaItemList().size)
            .isEqualTo(mMediaDevices.size + 1)
        verify(mCb).onDeviceListChanged()
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun advanced_onDeviceListUpdateWithConnectedDeviceRemote_verifyItemSize_inputRouting() {
        enableInputRoutingConfig()
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1)
        mMediaSwitchingController = createDefaultMediaSwitchingController()
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.containsAll(mMediaDevices)).isTrue()
        assertThat(devices.size).isEqualTo(mMediaDevices.size)
        // When input routing is enabled, there should be 3 non-MediaDevice items: one for
        // the "Output" title, one for the "Speakers & Displays" title, and one for the "Input"
        // title.
        assertThat(mMediaSwitchingController.getMediaItemList().size)
            .isEqualTo(mMediaDevices.size + 3)
        verify(mCb).onDeviceListChanged()
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun onInputDeviceListUpdateWithInputType_verifyDeviceListCallback_inputRouting() {
        enableInputRoutingConfig()
        mMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName,
                mContext.user,
                mToken = null,
                MediaSwitchingType.INPUT,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )
        val audioDeviceInfos = arrayOf<AudioDeviceInfo>()
        whenever(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
            .thenReturn(audioDeviceInfos)
        mMediaSwitchingController.start(mCb)

        // Output devices have changed.
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val inputDevices = listOf(mMediaDevice6, mMediaDevice7)

        // Input devices have changed.
        mMediaSwitchingController.mInputDeviceCallback.onInputDeviceListUpdated(inputDevices)

        val resultList = mMediaSwitchingController.getMediaItemList()
        val devices = getMediaDevices(resultList)

        assertThat(resultList[0]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[0] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice6)

        assertThat(resultList[1]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice7)

        assertThat(resultList.size).isEqualTo(2)

        // Only contains input devices.
        assertThat(devices).containsNoneIn(mMediaDevices)
        assertThat(devices).hasSize(inputDevices.size)
        verify(mCb, atLeastOnce()).onDeviceListChanged()
        verify(mNearbyMediaDevicesManager, never()).registerNearbyDevicesCallback(any())
        verify(mLocalMediaManager, never()).registerCallback(any())
        verify(mLocalMediaManager, never()).startScan()
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun onInputDeviceListUpdateWithOutputType_verifyDeviceListCallback_inputRouting() {
        enableInputRoutingConfig()
        mMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName,
                mContext.user,
                mToken = null,
                MediaSwitchingType.OUTPUT,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )
        val audioDeviceInfos = arrayOf<AudioDeviceInfo>()
        whenever(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
            .thenReturn(audioDeviceInfos)
        mMediaSwitchingController.start(mCb)

        // Output devices have changed.
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val inputDevices = listOf(mMediaDevice6, mMediaDevice7)

        // Input devices have changed.
        mMediaSwitchingController.mInputDeviceCallback.onInputDeviceListUpdated(inputDevices)

        val resultList = mMediaSwitchingController.getMediaItemList()
        val devices = getMediaDevices(resultList)

        assertThat(resultList[0]).isInstanceOf(GroupDividerMediaItem::class.java)
        assertThat((resultList[0] as GroupDividerMediaItem).hasTopSeparator).isTrue()
        assertThat((resultList[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))

        assertThat(resultList[1]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)

        assertThat(resultList[2]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)

        assertThat(resultList.size).isEqualTo(3)

        assertThat(mMediaSwitchingController.hasConnectDeviceButton()).isTrue()

        // Only contains output devices.
        assertThat(devices).containsExactlyElementsIn(mMediaDevices)
        verify(mCb, atLeastOnce()).onDeviceListChanged()
        verify(mInputRouteManager, never()).registerCallback(any())
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun onInputDeviceListUpdate_verifyDeviceListCallback() {
        enableInputRoutingConfig()
        val audioDeviceInfos = arrayOf<AudioDeviceInfo>()
        whenever(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
            .thenReturn(audioDeviceInfos)
        mMediaSwitchingController = createDefaultMediaSwitchingController()
        mMediaSwitchingController.start(mCb)

        // Output devices have changed.
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        val inputDevices = listOf(mMediaDevice6, mMediaDevice7)

        // Input devices have changed.
        mMediaSwitchingController.mInputDeviceCallback.onInputDeviceListUpdated(inputDevices)

        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices).containsAtLeastElementsIn(mMediaDevices)
        assertThat(devices).hasSize(mMediaDevices.size + inputDevices.size)
        verify(mCb, atLeastOnce()).onDeviceListChanged()
    }

    @Test
    fun advanced_categorizeMediaItems_withSuggestedDevice_verifyDeviceListSize() {
        whenever(mMediaDevice1.isSuggestedDevice).thenReturn(true)
        whenever(mMediaDevice2.isSuggestedDevice).thenReturn(false)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.clearMediaItemList()
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices: MutableList<MediaDevice> = ArrayList()
        var dividerSize = 0
        for (item in mMediaSwitchingController.getMediaItemList()) {
            if (item is DeviceMediaItem) {
                devices.add(item.mediaDevice)
            }
            if (item is GroupDividerMediaItem) {
                dividerSize++
            }
        }

        assertThat(devices.containsAll(mMediaDevices)).isTrue()
        assertThat(devices.size).isEqualTo(mMediaDevices.size)
        assertThat(dividerSize).isEqualTo(2)
        verify(mCb).onDeviceListChanged()
    }

    @Test
    fun onDeviceListUpdate_withMutingExpectedDevice_putItOnTop() {
        whenever(mMediaDevice1.isSuggestedDevice).thenReturn(false)
        whenever(mMediaDevice2.isMutingExpectedDevice).thenReturn(true)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.clearMediaItemList()
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.first().isMutingExpectedDevice).isTrue()
        assertThat(mMediaSwitchingController.hasMutingExpectedDevice()).isTrue()
    }

    @Test
    fun onDeviceListUpdate_noMutingExpectedDevice_processListNormally() {
        whenever(mMediaDevice1.isSuggestedDevice).thenReturn(false)
        whenever(mMediaDevice2.isMutingExpectedDevice).thenReturn(false)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.clearMediaItemList()
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)
        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())

        assertThat(devices.first().isMutingExpectedDevice).isFalse()
        assertThat(mMediaSwitchingController.hasMutingExpectedDevice()).isFalse()
    }

    @Test
    fun onDeviceListUpdate_groupPlaybackAndExpanded_allSelectedDevicesOnTop() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        mMediaSwitchingController.setGroupListCollapsed(false)

        doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                callback.onDeviceListUpdate(mMediaDevices)
                null
            }
            .whenever(mLocalMediaManager)
            .registerCallback(any())
        doReturn(true).whenever(mLocalMediaManager).isMediaSessionAvailableForVolumeControl

        mMediaSwitchingController.start(mCb)

        val resultList = mMediaSwitchingController.getMediaItemList()

        assertThat(resultList[0]).isInstanceOf(GroupDividerMediaItem::class.java)
        assertThat((resultList[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_connected_speakers))
        assertThat((resultList[0] as GroupDividerMediaItem).isExpandable).isTrue()

        assertThat(resultList[1]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)

        assertThat(resultList[2]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)

        assertThat(resultList.size).isEqualTo(3)
    }

    @Test
    fun onDeviceListUpdate_groupPlaybackAndCollapsed_groupControlAtTheTop() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        mMediaSwitchingController.setGroupListCollapsed(true)

        doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                callback.onDeviceListUpdate(mMediaDevices)
                null
            }
            .whenever(mLocalMediaManager)
            .registerCallback(any())
        doReturn(true).whenever(mLocalMediaManager).isMediaSessionAvailableForVolumeControl

        mMediaSwitchingController.start(mCb)
        val resultList = mMediaSwitchingController.getMediaItemList()

        assertThat(resultList[0]).isInstanceOf(GroupDividerMediaItem::class.java)
        assertThat((resultList[0] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_connected_speakers))
        assertThat((resultList[0] as GroupDividerMediaItem).isExpandable).isTrue()

        assertThat(resultList[1]).isInstanceOf(DeviceGroupMediaItem::class.java)

        assertThat(resultList.size).isEqualTo(2)
    }

    @Test
    fun onDeviceListUpdate_sessionVolumeUnavailable_noGroupControl() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        mMediaSwitchingController.setGroupListCollapsed(true)

        doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                callback.onDeviceListUpdate(mMediaDevices)
                null
            }
            .whenever(mLocalMediaManager)
            .registerCallback(any())
        doReturn(false).whenever(mLocalMediaManager).isMediaSessionAvailableForVolumeControl

        mMediaSwitchingController.start(mCb)

        mMediaSwitchingController.setGroupListCollapsed(true)
        mMediaSwitchingController.clearMediaItemList()
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        val resultList = mMediaSwitchingController.getMediaItemList()

        assertThat(resultList[0]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[0] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)

        assertThat(resultList[1]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)

        assertThat(resultList.size).isEqualTo(2)
    }

    @Test
    fun onDeviceListUpdate_groupPlaybackCreatedLater_noGroupControl() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(false)

        mMediaSwitchingController.setGroupListCollapsed(true)
        doReturn(false).whenever(mLocalMediaManager).isMediaSessionAvailableForVolumeControl

        doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                callback.onDeviceListUpdate(mMediaDevices)
                null
            }
            .whenever(mLocalMediaManager)
            .registerCallback(any())

        mMediaSwitchingController.start(mCb)

        // Add second selected device after the initial update.
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        // Skip 2+ seconds to prevent the list cleanup on refresh.
        mClock.advanceTime(2500)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        val resultList = mMediaSwitchingController.getMediaItemList()

        assertThat(resultList[0]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[0] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)

        assertThat(resultList[1]).isInstanceOf(GroupDividerMediaItem::class.java)
        assertThat((resultList[1] as GroupDividerMediaItem).hasTopSeparator).isTrue()
        assertThat((resultList[1] as GroupDividerMediaItem).title)
            .isEqualTo(mContext.getString(R.string.media_output_group_title_speakers_and_displays))

        assertThat(resultList[2]).isInstanceOf(DeviceMediaItem::class.java)
        assertThat((resultList[2] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)
        assertThat(resultList.size).isEqualTo(3)
    }

    @Test
    fun onDeviceListUpdate_isRefreshing_updatesNeedRefreshToTrue() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.setRefreshing(true)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.mNeedRefresh).isTrue()
    }

    @Test
    fun advanced_onDeviceListUpdate_isRefreshing_updatesNeedRefreshToTrue() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.setRefreshing(true)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.mNeedRefresh).isTrue()
    }

    @Test
    fun cancelMuteAwaitConnection_cancelsWithMediaManager() {
        whenever(mAudioManager.mutingExpectedDevice).thenReturn(mock<AudioDeviceAttributes>())
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.cancelMuteAwaitConnection()

        verify(mAudioManager).cancelMuteAwaitConnection(any())
    }

    @Test
    fun cancelMuteAwaitConnection_audioManagerIsNull_noAction() {
        whenever(mAudioManager.mutingExpectedDevice).thenReturn(null)
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.cancelMuteAwaitConnection()

        verify(mAudioManager, never()).cancelMuteAwaitConnection(any())
    }

    @Test
    fun getAppSourceName_packageNameIsNull_returnsNull() {
        val testMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = "",
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )
        testMediaSwitchingController.start(mCb)
        reset(mCb)

        testMediaSwitchingController.getAppSourceName()

        assertThat(testMediaSwitchingController.getAppSourceName()).isNull()
    }

    @Test
    fun getAppIcon_packageNameIsNull_returnsNull() {
        val testMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = "",
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )
        testMediaSwitchingController.start(mCb)
        reset(mCb)

        testMediaSwitchingController.getAppSourceName()

        assertThat(testMediaSwitchingController.getAppIcon()).isNull()
    }

    @Test
    fun refreshDataSetIfNeeded_needRefreshIsTrue_setsToFalse() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.mNeedRefresh = true

        mMediaSwitchingController.refreshDataSetIfNeeded()

        assertThat(mMediaSwitchingController.mNeedRefresh).isFalse()
    }

    @Test
    fun isCurrentConnectedDeviceRemote_containsFeatures_returnsTrue() {
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))
        whenever(mLocalMediaManager.currentConnectedDevice).thenReturn(mMediaDevice1)

        assertThat(mMediaSwitchingController.isCurrentConnectedDeviceRemote()).isTrue()
    }

    @Test
    fun isSingleConnected_Device_sameDevice_returnsTrue() {
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1)

        assertThat(mMediaSwitchingController.isSingleConnectedDevice(mMediaDevice1)).isTrue()
    }

    @Test
    fun isCurrentlyConnected_singleSelected_returnsTrue() {
        whenever(mMediaDevice2.isSelected()).thenReturn(true)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.isSingleConnectedDevice(mMediaDevice2)).isTrue()
    }

    @Test
    fun isSingleConnected_Device_groupPlayback_returnsFalse() {
        whenever(mMediaDevice1.isSelected()).thenReturn(true)
        whenever(mMediaDevice2.isSelected()).thenReturn(true)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.isSingleConnectedDevice(mMediaDevice1)).isFalse()
        assertThat(mMediaSwitchingController.isSingleConnectedDevice(mMediaDevice2)).isFalse()
    }

    @Test
    fun isSingleConnected_Device_differentDevice_returnsFalse() {
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1)

        assertThat(mMediaSwitchingController.isSingleConnectedDevice(mMediaDevice2)).isFalse()
    }

    @Test
    fun addDeviceToPlayMedia_callsLocalMediaManager() {
        val testMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        val mockLocalMediaManager = mock<LocalMediaManager>()
        testMediaSwitchingController.mLocalMediaManager = mockLocalMediaManager

        testMediaSwitchingController.addDeviceToPlayMedia(mMediaDevice2)
        val captor = argumentCaptor<RoutingChangeInfo>()
        verify(mockLocalMediaManager).addDeviceToPlayMedia(eq(mMediaDevice2), captor.capture())
        val capturedInfo = captor.firstValue
        assertThat(capturedInfo.entryPoint).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER)
        assertThat(capturedInfo.isSuggested).isEqualTo(false)
    }

    @Test
    fun removeDeviceFromPlayMedia_callsLocalMediaManager() {
        val testMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        val mockLocalMediaManager = mock<LocalMediaManager>()
        testMediaSwitchingController.mLocalMediaManager = mockLocalMediaManager

        testMediaSwitchingController.removeDeviceFromPlayMedia(mMediaDevice2)
        val argumentCaptor = argumentCaptor<RoutingChangeInfo>()
        verify(mockLocalMediaManager)
            .removeDeviceFromPlayMedia(eq(mMediaDevice2), argumentCaptor.capture())
        val capturedInfo = argumentCaptor.firstValue
        assertThat(capturedInfo.entryPoint).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER)
        assertThat(capturedInfo.isSuggested).isFalse()
    }

    @Test
    fun adjustSessionVolume_adjustWithoutId_triggersFromLocalMediaManager() {
        val testVolume = 10
        mMediaSwitchingController.adjustSessionVolume(testVolume)

        verify(mLocalMediaManager).adjustSessionVolume(testVolume)
    }

    @Test
    fun adjustDeviceVolume_callsLocalMediaManager() {
        val mediaDevice = mock<MediaDevice>()
        mMediaSwitchingController.adjustVolume(mediaDevice, 15)
        mFakeBackgroundExecutor.runAllReady()

        verify(mLocalMediaManager).adjustDeviceVolume(mediaDevice, 15)
    }

    @Test
    fun logInteractionAdjustVolume_triggersFromMetricLogger() {
        val spyMediaOutputMetricLogger = spy(mMediaSwitchingController.mMetricLogger)
        mMediaSwitchingController.mMetricLogger = spyMediaOutputMetricLogger

        mMediaSwitchingController.logInteractionAdjustVolume(mMediaDevice1)

        verify(spyMediaOutputMetricLogger).logInteractionAdjustVolume(mMediaDevice1)
    }

    @Test
    fun getSessionVolumeMax_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionVolumeMax()

        verify(mLocalMediaManager).getSessionVolumeMax()
    }

    @Test
    fun getSessionVolume_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionVolume()

        verify(mLocalMediaManager).getSessionVolume()
    }

    @Test
    fun getSessionName_triggersFromLocalMediaManager() {
        mMediaSwitchingController.getSessionName()

        verify(mLocalMediaManager).getSessionName()
    }

    @Test
    fun releaseSession_triggersFromLocalMediaManager() {
        mMediaSwitchingController.releaseSession()

        verify(mLocalMediaManager).releaseSession()
    }

    @Test
    fun isAnyDeviceTransferring_noDevicesStateIsConnecting_returnsFalse() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isFalse()
    }

    @Test
    fun isAnyDeviceTransferring_deviceStateIsConnecting_returnsTrue() {
        whenever(mMediaDevice1.state)
            .thenReturn(LocalMediaManager.MediaDeviceState.STATE_CONNECTING)
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isTrue()
    }

    @Test
    fun isAnyDeviceTransferring_advancedLayoutSupport() {
        whenever(mMediaDevice1.state)
            .thenReturn(LocalMediaManager.MediaDeviceState.STATE_CONNECTING)
        mMediaSwitchingController.start(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.isAnyDeviceTransferring()).isTrue()
    }

    @Test
    fun isPlaying_stateIsNull() {
        whenever(mSessionMediaController.playbackState).thenReturn(null)

        assertThat(mMediaSwitchingController.isPlaying()).isFalse()
    }

    @Test
    fun onSelectedDeviceStateChanged_verifyCallback() {
        whenever(mLocalMediaManager.currentConnectedDevice).thenReturn(mMediaDevice2)
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.connectDevice(mMediaDevice1)

        mMediaSwitchingController.onSelectedDeviceStateChanged(
            mMediaDevice1,
            LocalMediaManager.MediaDeviceState.STATE_CONNECTED,
        )

        verify(mCb).onRouteChanged()
    }

    @Test
    fun onDeviceAttributesChanged_verifyCallback() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onDeviceAttributesChanged()

        verify(mCb).onRouteChanged()
    }

    @Test
    fun onRequestFailed_verifyCallback() {
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice1)
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.connectDevice(mMediaDevice2)

        mMediaSwitchingController.onRequestFailed(0 /* reason */)

        verify(mCb, atLeastOnce()).onRouteChanged()
    }

    @Test
    fun getHeaderTitle_withoutMetadata_returnDefaultString() {
        whenever(mSessionMediaController.getMetadata()).thenReturn(null)

        mMediaSwitchingController.start(mCb)

        assertThat(mMediaSwitchingController.getHeaderTitle())
            .isEqualTo(mContext.getText(R.string.controls_media_title))
    }

    @Test
    fun getHeaderTitle_withMetadata_returnSongName() {
        whenever(mSessionMediaController.getMetadata()).thenReturn(mMediaMetadata)

        mMediaSwitchingController.start(mCb)

        assertThat(mMediaSwitchingController.getHeaderTitle()).isEqualTo(TEST_SONG)
    }

    @Test
    fun getHeaderSubTitle_withoutMetadata_returnNull() {
        whenever(mSessionMediaController.getMetadata()).thenReturn(null)

        mMediaSwitchingController.start(mCb)

        assertThat(mMediaSwitchingController.getHeaderSubTitle()).isNull()
    }

    @Test
    fun getHeaderSubTitle_withMetadata_returnArtistName() {
        whenever(mSessionMediaController.getMetadata()).thenReturn(mMediaMetadata)

        mMediaSwitchingController.start(mCb)

        assertThat(mMediaSwitchingController.getHeaderSubTitle()).isEqualTo(TEST_ARTIST)
    }

    @Test
    fun getActiveRemoteMediaDevices() {
        mRemoteSessionInfo.stub {
            on { id } doReturn TEST_SESSION_ID
            on { name } doReturn TEST_SESSION_NAME
            on { volumeMax } doReturn 100
            on { volume } doReturn 10
            on { isSystemSession } doReturn false
        }
        mRoutingSessionInfos.add(mRemoteSessionInfo)
        whenever(mLocalMediaManager.remoteRoutingSessions).thenReturn(mRoutingSessionInfos)

        assertThat(mMediaSwitchingController.getActiveRemoteMediaDevices())
            .containsExactly(mRemoteSessionInfo)
    }

    @Test
    fun getNotificationLargeIcon_withoutPackageName_returnsNull() {
        mMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull()
    }

    @Test
    fun getNotificationLargeIcon_withoutLargeIcon_returnsNull() {
        mNotification.stub {
            on { isMediaNotification } doReturn true
            on { getLargeIcon() } doReturn null
        }

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull()
    }

    @Test
    fun getNotificationLargeIcon_withPackageNameAndMediaSession_returnsIconCompat() {
        mNotification.stub {
            on { isMediaNotification } doReturn true
            on { getLargeIcon() } doReturn mock<Icon>()
        }

        assertThat(mMediaSwitchingController.getNotificationIcon())
            .isInstanceOf(IconCompat::class.java)
    }

    @Test
    fun getNotificationLargeIcon_withPackageNameAndNoMediaSession_returnsNull() {
        mNotification.stub {
            on { isMediaNotification } doReturn false
            on { getLargeIcon() } doReturn mock<Icon>()
        }

        assertThat(mMediaSwitchingController.getNotificationIcon()).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getAppIcon_noNotificationIconAndNoPackageIcon_returnsNull() {
        mNotification.stub {
            on { isMediaNotification } doReturn true
            on { getSmallIcon() } doReturn null
        }

        whenever(mPackageManager.getApplicationIcon(mPackageName))
            .thenThrow(PackageManager.NameNotFoundException())

        assertThat(mMediaSwitchingController.getAppIcon()).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getAppIcon_noNotificationIcon_returnsPackageIcon() {
        // no notification icon
        mNotification.stub {
            on { isMediaNotification } doReturn true
            on { getSmallIcon() } doReturn null
        }
        // Fallback to package icon
        val packageIcon = mock<Drawable>()
        whenever(mPackageManager.getApplicationIcon(mPackageName)).thenReturn(packageIcon)

        assertThat(mMediaSwitchingController.getAppIcon()).isEqualTo(packageIcon)
    }

    @Test
    fun getAppIcon_withPackageNameAndMediaSession_returnsNotificationSmallIcon() {
        val drawable = mock<Drawable>()
        val icon = mock<Icon> { on { loadDrawable(any()) } doReturn drawable }
        mNotification.stub {
            on { isMediaNotification } doReturn true
            on { getSmallIcon() } doReturn icon
        }

        assertThat(mMediaSwitchingController.getAppIcon()).isEqualTo(drawable)
    }

    @Test
    fun getDeviceIconCompat_deviceIconIsNotNull_returnsIcon() {
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice2)
        whenever(mMediaDevice1.getIcon()).thenReturn(mDrawable)

        assertThat(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice1))
            .isInstanceOf(IconCompat::class.java)
    }

    @Test
    fun getDeviceIconCompat_deviceIconIsNull_returnsIcon() {
        whenever(mLocalMediaManager.getCurrentConnectedDevice()).thenReturn(mMediaDevice2)
        whenever(mMediaDevice1.getIcon()).thenReturn(null)

        assertThat(mMediaSwitchingController.getDeviceIconCompat(mMediaDevice1))
            .isInstanceOf(IconCompat::class.java)
    }

    @Test
    fun isVolumeControlEnabled_isCastWithVolumeFixed_returnsFalse() {
        whenever(mMediaDevice1.deviceType).thenReturn(MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE)

        whenever(mMediaDevice1.isVolumeFixed).thenReturn(true)

        assertThat(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).isFalse()
    }

    @Test
    fun isVolumeControlEnabled_isCastWithVolumeNotFixed_returnsTrue() {
        whenever(mMediaDevice1.deviceType).thenReturn(MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE)

        whenever(mMediaDevice1.isVolumeFixed).thenReturn(false)

        assertThat(mMediaSwitchingController.isVolumeControlEnabled(mMediaDevice1)).isTrue()
    }

    @Test
    fun setTemporaryAllowListExceptionIfNeeded_fromRemoteToBluetooth_addsAllowList() {
        whenever(mLocalMediaManager.currentConnectedDevice).thenReturn(mMediaDevice1)
        whenever(mMediaDevice1.deviceType).thenReturn(MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE)
        whenever(mMediaDevice1.features)
            .thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK))
        whenever(mMediaDevice2.deviceType)
            .thenReturn(MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE)

        mMediaSwitchingController.setTemporaryAllowListExceptionIfNeeded()

        verify(mPowerExemptionManager).addToTemporaryAllowList(any(), any(), any(), any())
    }

    @Test
    fun setTemporaryAllowListExceptionIfNeeded_packageNameIsNull_NoAction() {
        val testMediaSwitchingController =
            MediaSwitchingController(
                mSpyContext,
                mPackageName = null,
                mSpyContext.user,
                mToken = null,
                mediaSwitchingType = null,
                mMediaSessionManager,
                mLocalBluetoothManager,
                mStarter,
                mNotifCollection,
                mDialogTransitionAnimator,
                mNearbyMediaDevicesManager,
                mAudioManager,
                mPowerExemptionManager,
                mKeyguardManager,
                mClock,
                mFakeBackgroundExecutor,
                mVolumePanelGlobalStateInteractor,
                mUserTracker,
                mJavaAdapter,
                mAudioSharingRepository,
                mExpandedAudioTileDetailsFeatureInteractor,
            )

        testMediaSwitchingController.setTemporaryAllowListExceptionIfNeeded()

        verify(mPowerExemptionManager, never()).addToTemporaryAllowList(any(), any(), any(), any())
    }

    @Test
    fun onMetadataChanged_triggersOnMetadataChanged() {
        mMediaSwitchingController.mCallback = this.mCallback

        mMediaSwitchingController.mCb.onMetadataChanged(mMediaMetadata)

        verify(mMediaSwitchingController.mCallback).onMediaChanged()
    }

    @Test
    fun onPlaybackStateChanged_updateWithNullState_onMediaStoppedOrPaused() {
        whenever(mPlaybackState.state).thenReturn(PlaybackState.STATE_PLAYING)
        mMediaSwitchingController.mCallback = this.mCallback
        mMediaSwitchingController.start(mCb)

        mMediaSwitchingController.mCb.onPlaybackStateChanged(null)

        verify(mMediaSwitchingController.mCallback).onMediaStoppedOrPaused()
    }

    @Test
    fun launchBluetoothPairing_isKeyguardLocked_dismissDialog() {
        whenever(mDialogTransitionAnimator.createActivityTransitionController(mDialogLaunchView))
            .thenReturn(mActivityTransitionAnimatorController)
        whenever(mKeyguardManager.isKeyguardLocked).thenReturn(true)
        mMediaSwitchingController.mCallback = this.mCallback

        mMediaSwitchingController.launchBluetoothPairing(mDialogLaunchView)

        verify(mCallback).dismissDialog()
    }

    @Test
    fun hasGroupPlayback_singleOutputDevice_returnsFalse() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.hasGroupPlayback()).isFalse()
    }

    @Test
    fun hasGroupPlayback_multipleOutputDevices_returnsTrue() {
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(true)

        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.hasGroupPlayback()).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL)
    @Test
    fun selectInputDevice() {
        enableInputRoutingConfig()
        val inputMediaDevice =
            InputMediaDevice.create(
                mContext,
                TEST_DEVICE_1_ID,
                /* address = */ "",
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                MAX_VOLUME,
                CURRENT_VOLUME,
                VOLUME_FIXED_TRUE,
                /* isSelected= */ false,
                PRODUCT_NAME_BUILTIN_MIC,
            )
        mMediaSwitchingController.connectDevice(inputMediaDevice!!)
        mFakeBackgroundExecutor.runAllReady()

        verify(mLocalMediaManager, never()).connectDevice(inputMediaDevice)
        verify(mInputRouteManager).selectDevice(inputMediaDevice)
    }

    @Test
    fun selectOutputDevice() {
        enableInputRoutingConfig()
        val outputMediaDevice = mock<MediaDevice>()
        mMediaSwitchingController.connectDevice(outputMediaDevice)
        mFakeBackgroundExecutor.runAllReady()

        verify(mInputRouteManager, never()).selectDevice(outputMediaDevice)
        val captor = argumentCaptor<RoutingChangeInfo>()
        verify(mLocalMediaManager).connectDevice(eq(outputMediaDevice), captor.capture())
        val capturedInfo = captor.firstValue
        assertThat(capturedInfo.entryPoint).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER)
        assertThat(capturedInfo.isSuggested).isEqualTo(false)
    }

    @Test
    fun selectSuggestedOutputDevice() {
        val outputMediaDevice = mock<MediaDevice>()
        whenever(outputMediaDevice.isSuggestedDevice).thenReturn(true)
        mMediaSwitchingController.connectDevice(outputMediaDevice)
        mFakeBackgroundExecutor.runAllReady()

        verify(mInputRouteManager, never()).selectDevice(outputMediaDevice)
        val captor = argumentCaptor<RoutingChangeInfo>()
        verify(mLocalMediaManager).connectDevice(eq(outputMediaDevice), captor.capture())
        val capturedInfo = captor.firstValue
        assertThat(capturedInfo.entryPoint).isEqualTo(ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER)
        assertThat(capturedInfo.isSuggested).isEqualTo(true)
    }

    @Test
    fun connectDeviceButton_remoteDevice_noButton() {
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))
        whenever(mLocalMediaManager.currentConnectedDevice).thenReturn(mMediaDevice1)
        mMediaSwitchingController.start(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.hasConnectDeviceButton()).isFalse()
    }

    @Test
    fun connectDeviceButton_localDevice_hasButton() {
        whenever(mLocalMediaManager.currentConnectedDevice).thenReturn(mMediaDevice1)
        mMediaSwitchingController.start(mCb)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.hasConnectDeviceButton()).isTrue()
    }

    @Test
    fun connectDeviceButton_presentAtAllTimesForNonGroupOutputs() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        // Mock the selected output device.
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(false)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        // Verify that there is initially the "Connect Device" button present.
        assertThat(mMediaSwitchingController.hasConnectDeviceButton()).isTrue()

        // Change the selected device, and verify that the "Connect Device" button is still present.
        whenever(mMediaDevice1.isSelected).thenReturn(false)
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        assertThat(mMediaSwitchingController.hasConnectDeviceButton()).isTrue()
    }

    @Test
    fun selectedDevicesAddedInSameOrder() {
        whenever(mLocalMediaManager.isPreferenceRouteListingExist).thenReturn(true)
        whenever(mMediaDevice1.isSelected).thenReturn(true)
        whenever(mMediaDevice2.isSelected).thenReturn(true)
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.clearMediaItemList()

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        val items = mMediaSwitchingController.getMediaItemList()
        assertThat((items[0] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice1)
        assertThat((items[1] as DeviceMediaItem).mediaDevice).isEqualTo(mMediaDevice2)
    }

    @Test
    fun selectedDevicesAddedInSameOrderWhenRlpDoesNotExist() {
        setUpSelectedDevicesAndOrdering()

        mMediaSwitchingController.onDeviceListUpdate(mMediaDevices)

        val devices = getMediaDevices(mMediaSwitchingController.getMediaItemList())
        assertThat(devices)
            .containsExactly(
                mMediaDevice4,
                mMediaDevice3,
                mMediaDevice5,
                mMediaDevice1,
                mMediaDevice2,
            )
            .inOrder()
    }

    private fun setUpSelectedDevicesAndOrdering() {
        whenever(mMediaDevice1.id).thenReturn(TEST_DEVICE_1_ID)
        whenever(mMediaDevice2.id).thenReturn(TEST_DEVICE_2_ID)
        whenever(mMediaDevice3.id).thenReturn(TEST_DEVICE_3_ID)
        whenever(mMediaDevice4.id).thenReturn(TEST_DEVICE_4_ID)
        whenever(mMediaDevice5.id).thenReturn(TEST_DEVICE_5_ID)
        mMediaDevices.clear()
        mMediaDevices.addAll(
            listOf(mMediaDevice2, mMediaDevice1, mMediaDevice4, mMediaDevice3, mMediaDevice5)
        )
        whenever(mMediaDevice3.isSelected).thenReturn(true)
        whenever(mMediaDevice4.isSelected).thenReturn(true)
        whenever(mMediaDevice5.isSelected).thenReturn(true)
        // Sort the media devices in the order they appear in the deviceOrder list
        val deviceOrder: MutableList<MediaDevice> = ArrayList()
        deviceOrder.addAll(
            listOf(mMediaDevice1, mMediaDevice2, mMediaDevice3, mMediaDevice4, mMediaDevice5)
        )
        for (i in deviceOrder.indices) {
            for (j in i + 1 until deviceOrder.size) {
                whenever(deviceOrder[i].compareTo(deviceOrder[j])).thenReturn(-1)
                whenever(deviceOrder[j].compareTo(deviceOrder[i])).thenReturn(1)
            }
        }
        whenever(mLocalMediaManager.isPreferenceRouteListingExist).thenReturn(false)
        mMediaSwitchingController.start(mCb)
        reset(mCb)
        mMediaSwitchingController.clearMediaItemList()
    }

    @Test
    fun getAudioSharingButtonState_noConnectedBroadcastAssistantDevice_returnsNull() {
        mAssistantProfile.stub { on { allConnectedDevices } doReturn emptyList() }
        mMediaSwitchingController.start(mCb)

        val buttonState = mMediaSwitchingController.getAudioSharingButtonState()

        assertThat(buttonState).isNull()
    }

    @Test
    fun getAudioSharingButtonState_inAudioSharing_returnsVisible() {
        val inAudioSharingFlow = MutableStateFlow(false)
        whenever(mAudioSharingRepository.inAudioSharing).thenReturn(inAudioSharingFlow)
        mMediaSwitchingController.start(mCb)
        verify(mJavaAdapter)
            .alwaysCollectFlow(same(inAudioSharingFlow), mInAudioSharingCaptor.capture())
        val capturedConsumer = mInAudioSharingCaptor.firstValue
        capturedConsumer.accept(true)

        val buttonState = mMediaSwitchingController.getAudioSharingButtonState()

        assertThat(buttonState).isNotNull()
        assertThat(buttonState!!.isActive).isTrue()
        assertThat(buttonState.resId).isEqualTo(R.string.media_output_dialog_button_sharing_audio)
    }

    @Test
    fun getAudioSharingButtonState_hasConnectedBroadcastAssistantDevice_returnsVisible() {
        mAssistantProfile.stub {
            on { allConnectedDevices } doReturn listOf(mock<BluetoothDevice>())
        }
        val inAudioSharingFlow = MutableStateFlow(false)
        whenever(mAudioSharingRepository.inAudioSharing).thenReturn(inAudioSharingFlow)
        mMediaSwitchingController.start(mCb)

        val buttonState = mMediaSwitchingController.getAudioSharingButtonState()

        assertThat(buttonState).isNotNull()
        assertThat(buttonState?.isActive).isFalse()
        assertThat(buttonState?.resId).isEqualTo(R.string.media_output_dialog_button_share_audio)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_remotePlaybackDevice_returnDefaultText() {
        doReturn(mMediaDevice1).whenever(mLocalMediaManager).currentConnectedDevice
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_casting)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_remoteAudioPlaybackDevice_returnDefaultText() {
        doReturn(mMediaDevice1).whenever(mLocalMediaManager).currentConnectedDevice
        whenever(mMediaDevice1.features)
            .thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_casting)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_remoteVideoPlaybackDevice_returnDefaultText() {
        doReturn(mMediaDevice1).whenever(mLocalMediaManager).currentConnectedDevice
        whenever(mMediaDevice1.features)
            .thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_VIDEO_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_casting)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_remoteGroupPlaybackDevice_returnDefaultText() {
        doReturn(mMediaDevice1).whenever(mLocalMediaManager).currentConnectedDevice
        whenever(mMediaDevice1.features)
            .thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_GROUP_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_casting)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_localDevice_returnNull() {
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_LOCAL_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes()).isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_mediaSwitchingTypeIsInput_returnNull() {
        mMediaSwitchingController = createDefaultMediaSwitchingController(MediaSwitchingType.INPUT)

        doReturn(mMediaDevice1).whenever(mLocalMediaManager).currentConnectedDevice
        whenever(mMediaDevice1.features).thenReturn(listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK))

        assertThat(mMediaSwitchingController.getStopButtonStringRes()).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_sessionReleaseSharing_returnsSharingText() {
        doReturn(RoutingSessionInfo.RELEASE_TYPE_SHARING)
            .whenever(mLocalMediaManager)
            .sessionReleaseType

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_sharing)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_sessionReleaseCasting_returnsCastingText() {
        doReturn(RoutingSessionInfo.RELEASE_TYPE_CASTING)
            .whenever(mLocalMediaManager)
            .sessionReleaseType

        assertThat(mMediaSwitchingController.getStopButtonStringRes())
            .isEqualTo(R.string.media_output_dialog_button_stop_casting)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_USE_OF_SESSION_RELEASE_TYPE_FOR_STOP_BUTTON)
    fun getStopButtonText_sessionReleaseUnsupported_returnsNull() {
        doReturn(RoutingSessionInfo.RELEASE_UNSUPPORTED)
            .whenever(mLocalMediaManager)
            .sessionReleaseType

        assertThat(mMediaSwitchingController.getStopButtonStringRes()).isNull()
    }

    @Test
    fun getAudioSharingButtonState_mediaSwitchingTypeIsInput_returnsNull() {
        mMediaSwitchingController = createDefaultMediaSwitchingController(MediaSwitchingType.INPUT)
        mAssistantProfile.stub {
            on { allConnectedDevices } doReturn listOf(mock<BluetoothDevice>())
        }
        val inAudioSharingFlow = MutableStateFlow(false)
        whenever(mAudioSharingRepository.inAudioSharing).thenReturn(inAudioSharingFlow)
        mMediaSwitchingController.start(mCb)

        val buttonState = mMediaSwitchingController.getAudioSharingButtonState()

        assertThat(buttonState).isNull()
    }

    @Test
    fun onMissingPermissionsUpdated_verifyCallback() {
        mMediaSwitchingController.start(mCb)
        reset(mCb)

        mMediaSwitchingController.onMissingPermissionsUpdated(null)

        verify(mCb).onRouteChanged()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun getMissingPermissionsResolveIntent_noMissingPermissionsInfo_returnsNull() {
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(null)

        assertThat(mMediaSwitchingController.getMissingPermissionsResolveIntent()).isNull()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun getMissingPermissionsResolveIntent_emptyPermissions_returnsNull() {
        val componentName = ComponentName(mPackageName, "class")
        val info = MissingPermissionsInfo(componentName, setOf())
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(info)

        assertThat(mMediaSwitchingController.getMissingPermissionsResolveIntent()).isNull()
    }

    @Test
    @RequiresFlagsDisabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun getMissingPermissionsResolveIntent_flagDisabled_returnsNull() {
        val componentName = ComponentName(mPackageName, "class")
        val perms = setOf("perm1", "perm2")
        val info = MissingPermissionsInfo(componentName, perms)
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(info)
        assertThat(mMediaSwitchingController.getMissingPermissionsResolveIntent()).isNull()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
    fun getMissingPermissionsResolveIntent_validInfo_returnsIntent() {
        val componentName = ComponentName(mPackageName, "class")
        val perms = setOf("perm1", "perm2")
        val info = MissingPermissionsInfo(componentName, perms)
        whenever(mLocalMediaManager.missingPermissionsInfo).thenReturn(info)

        val intent = mMediaSwitchingController.getMissingPermissionsResolveIntent()

        assertThat(intent).isNotNull()
        assertThat(intent!!.getAction())
            .isEqualTo(RouteListingPreference.ACTION_RESOLVE_MISSING_PERMISSIONS)
        assertThat(intent.getComponent()).isEqualTo(componentName)
        val extraPermissions =
            intent.getStringArrayListExtra(RouteListingPreference.EXTRA_MISSING_PERMISSIONS)
        assertThat(extraPermissions).containsExactly("perm1", "perm2")
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    private fun getMediaDevices(mediaItemList: List<MediaItem>): List<MediaDevice> {
        return mediaItemList.filterIsInstance<DeviceMediaItem>().map { it.mediaDevice }
    }

    private fun enableInputRoutingConfig() {
        val spyResources = spy(mContext.resources)
        whenever(mSpyContext.resources).thenReturn(spyResources)
        whenever(spyResources.getBoolean(R.bool.config_enableInputRouting)).thenReturn(true)
    }

    private fun createDefaultMediaSwitchingController(
        mediaSwitchingType: MediaSwitchingType? = null
    ): MediaSwitchingController {
        return MediaSwitchingController(
            mSpyContext,
            mPackageName,
            mContext.user,
            mToken = null,
            mediaSwitchingType,
            mMediaSessionManager,
            mLocalBluetoothManager,
            mStarter,
            mNotifCollection,
            mDialogTransitionAnimator,
            mNearbyMediaDevicesManager,
            mAudioManager,
            mPowerExemptionManager,
            mKeyguardManager,
            mClock,
            mFakeBackgroundExecutor,
            mVolumePanelGlobalStateInteractor,
            mUserTracker,
            mJavaAdapter,
            mAudioSharingRepository,
            mExpandedAudioTileDetailsFeatureInteractor,
        )
    }

    private fun setupNotificationMock() {
        val mediaSessionToken =
            mock<MediaSession.Token> { on { binder } doReturn mock<ISessionController>() }
        val mediaSessionBundle =
            mock<Bundle> {
                on { getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION) }
                    .doReturn(mediaSessionToken)
            }
        mNotification.stub { on { isMediaNotification } doReturn false }
        mNotification.extras = mediaSessionBundle
        val statusBarNotification =
            mock<StatusBarNotification> {
                on { notification } doReturn mNotification
                on { packageName } doReturn mPackageName
            }
        val notificationEntry =
            mock<NotificationEntry> { on { this.sbn } doReturn statusBarNotification }
        mNotifCollection.stub { on { allNotifs } doReturn listOf(notificationEntry) }
    }

    companion object {
        private const val TEST_DEVICE_1_ID = "test_device_1_id"
        private const val TEST_DEVICE_2_ID = "test_device_2_id"
        private const val TEST_DEVICE_3_ID = "test_device_3_id"
        private const val TEST_DEVICE_4_ID = "test_device_4_id"
        private const val TEST_DEVICE_5_ID = "test_device_5_id"
        private const val TEST_ARTIST = "test_artist"
        private const val TEST_SONG = "test_song"
        private const val TEST_SESSION_ID = "test_session_id"
        private const val TEST_SESSION_NAME = "test_session_name"
        private const val MAX_VOLUME = 1
        private const val CURRENT_VOLUME = 0
        private const val VOLUME_FIXED_TRUE = true
        private const val PRODUCT_NAME_BUILTIN_MIC = "Built-in Mic"

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_AUDIO_INPUT_DEVICE_ROUTING_AND_VOLUME_CONTROL
            )
        }
    }
}
