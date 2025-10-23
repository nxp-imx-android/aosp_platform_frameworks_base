/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone.fragment;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.SysuiBaseFragmentTest;
import com.android.systemui.animation.AnimatorTestRule;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.log.LogBuffer;
import com.android.systemui.log.LogcatEchoTracker;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.domain.interactor.PanelExpansionInteractor;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.OperatorNameViewController;
import com.android.systemui.statusbar.core.StatusBarRootModernization;
import com.android.systemui.statusbar.disableflags.DisableFlagsLogger;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerStatusBarViewBinder;
import com.android.systemui.statusbar.phone.HeadsUpAppearanceController;
import com.android.systemui.statusbar.phone.StatusBarHideIconsForBouncerManager;
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent;
import com.android.systemui.statusbar.phone.ui.DarkIconManager;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.FakeHomeStatusBarViewBinder;
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.FakeHomeStatusBarViewModel;
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel;
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel.HomeStatusBarViewModelFactory;
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.StatusBarOperatorNameViewModel;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.window.StatusBarWindowController;
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore;
import com.android.systemui.statusbar.window.StatusBarWindowStateController;
import com.android.systemui.statusbar.window.StatusBarWindowStateListener;
import com.android.systemui.util.CarrierConfigTracker;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
public class CollapsedStatusBarFragmentTest extends SysuiBaseFragmentTest {
    private ShadeExpansionStateManager mShadeExpansionStateManager;
    private SystemStatusAnimationScheduler mAnimationScheduler;
    // Set in instantiate()
    private StatusBarIconController mStatusBarIconController;
    private KeyguardStateController mKeyguardStateController;

    private final CommandQueue mCommandQueue = mock(CommandQueue.class);
    private OperatorNameViewController.Factory mOperatorNameViewControllerFactory;
    private OperatorNameViewController mOperatorNameViewController;
    private SecureSettings mSecureSettings;
    private FakeExecutor mExecutor = new FakeExecutor(new FakeSystemClock());
    private final CarrierConfigTracker mCarrierConfigTracker = mock(CarrierConfigTracker.class);

    @Mock
    private HomeStatusBarComponent.Factory mStatusBarFragmentComponentFactory;
    @Mock
    private HomeStatusBarComponent mHomeStatusBarComponent;
    @Mock
    private StatusBarStateController mStatusBarStateController;
    @Mock
    private HeadsUpAppearanceController mHeadsUpAppearanceController;
    @Mock
    private PanelExpansionInteractor mPanelExpansionInteractor;
    @Mock
    private DarkIconManager.Factory mIconManagerFactory;
    @Mock
    private DarkIconManager mIconManager;
    private FakeHomeStatusBarViewModel mCollapsedStatusBarViewModel;
    private FakeHomeStatusBarViewBinder mCollapsedStatusBarViewBinder;
    @Mock
    private StatusBarHideIconsForBouncerManager mStatusBarHideIconsForBouncerManager;
    @Mock
    private DumpManager mDumpManager;
    @Mock
    private StatusBarWindowStateController mStatusBarWindowStateController;
    @Mock
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    @Mock private StatusBarWindowControllerStore mStatusBarWindowControllerStore;
    @Mock private StatusBarWindowController mStatusBarWindowController;
    @Mock private DarkIconDispatcher mDarkIconDispatcher;
    @Rule
    public final AnimatorTestRule mAnimatorTestRule = new AnimatorTestRule(this);

    private List<StatusBarWindowStateListener> mStatusBarWindowStateListeners = new ArrayList<>();

    public CollapsedStatusBarFragmentTest() {
        super(CollapsedStatusBarFragment.class);
    }

    @Before
    public void setup() {
        when(mStatusBarWindowControllerStore.forDisplay(anyInt()))
                .thenReturn(mStatusBarWindowController);
        injectLeakCheckedDependencies(ALL_SUPPORTED_CLASSES);
        mDependency.injectMockDependency(DarkIconDispatcher.class);

        // Keep the window state listeners so we can dispatch to them to test the status bar
        // fragment's response.
        doAnswer(invocation -> {
            mStatusBarWindowStateListeners.add(invocation.getArgument(0));
            return null;
        }).when(mStatusBarWindowStateController).addListener(
                any(StatusBarWindowStateListener.class));
    }

    @Test
    @EnableFlags(StatusBarRootModernization.FLAG_NAME)
    public void testDisableNotifications_doesNothingWhenFlagEnabled() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();

        fragment.disable(DEFAULT_DISPLAY, StatusBarManager.DISABLE_NOTIFICATION_ICONS, 0, false);

        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());

        fragment.disable(DEFAULT_DISPLAY, 0, 0, false);

        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());

        fragment.disable(DEFAULT_DISPLAY, StatusBarManager.DISABLE_NOTIFICATION_ICONS, 0, false);

        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());
    }

    @Test
    @EnableFlags(StatusBarRootModernization.FLAG_NAME)
    public void testDisableClock_doesNothingWhenFlagEnabled() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();

        fragment.disable(DEFAULT_DISPLAY, StatusBarManager.DISABLE_CLOCK, 0, false);

        assertEquals(View.VISIBLE, getClockView().getVisibility());

        fragment.disable(DEFAULT_DISPLAY, 0, 0, false);

        assertEquals(View.VISIBLE, getClockView().getVisibility());

        fragment.disable(DEFAULT_DISPLAY, StatusBarManager.DISABLE_CLOCK, 0, false);

        assertEquals(View.VISIBLE, getClockView().getVisibility());
    }

    @Test
    public void userChip_defaultVisibilityIsGone() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();

        assertEquals(View.GONE, getUserChipView().getVisibility());
    }

    @Test
    @EnableFlags({
            StatusBarRootModernization.FLAG_NAME,
    })
    public void hasPrimaryOngoingActivity_viewsUnchangedWhenRootModernizationFlagOn() {
        resumeAndGetFragment();

        assertEquals(View.VISIBLE, getPrimaryOngoingActivityChipView().getVisibility());
        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());

        mCollapsedStatusBarViewBinder.getListener().onOngoingActivityStatusChanged(
                /* hasPrimaryOngoingActivity= */ true,
                /* hasSecondaryOngoingActivity= */ false,
                /* shouldAnimate= */ false);

        assertEquals(View.VISIBLE, getPrimaryOngoingActivityChipView().getVisibility());
        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());

        mCollapsedStatusBarViewBinder.getListener().onOngoingActivityStatusChanged(
                /* hasPrimaryOngoingActivity= */ false,
                /* hasSecondaryOngoingActivity= */ false,
                /* shouldAnimate= */ false);

        assertEquals(View.VISIBLE, getPrimaryOngoingActivityChipView().getVisibility());
        assertEquals(View.VISIBLE, getNotificationAreaView().getVisibility());
    }

    @Test
    public void setUp_fragmentCreatesDaggerComponent() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();

        assertEquals(mHomeStatusBarComponent, fragment.getHomeStatusBarComponent());
    }

    @Test
    public void testBlockedIcons_obeysSettingForVibrateIcon_settingOff() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();
        String str = mContext.getString(com.android.internal.R.string.status_bar_volume);

        // GIVEN the setting is off
        when(mSecureSettings.getInt(Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON, 0))
                .thenReturn(0);

        // WHEN CollapsedStatusBarFragment builds the blocklist
        fragment.updateBlockedIcons();

        // THEN status_bar_volume SHOULD be present in the list
        boolean contains = fragment.getBlockedIcons().contains(str);
        assertTrue(contains);
    }

    @Test
    public void testBlockedIcons_obeysSettingForVibrateIcon_settingOn() {
        CollapsedStatusBarFragment fragment = resumeAndGetFragment();
        String str = mContext.getString(com.android.internal.R.string.status_bar_volume);

        // GIVEN the setting is ON
        when(mSecureSettings.getIntForUser(Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON, 0,
                UserHandle.USER_CURRENT))
                .thenReturn(1);

        // WHEN CollapsedStatusBarFragment builds the blocklist
        fragment.updateBlockedIcons();

        // THEN status_bar_volume SHOULD NOT be present in the list
        boolean contains = fragment.getBlockedIcons().contains(str);
        assertFalse(contains);
    }

    @Override
    protected Fragment instantiate(Context context, String className, Bundle arguments) {
        MockitoAnnotations.initMocks(this);
        setUpDaggerComponent();
        mAnimationScheduler = mock(SystemStatusAnimationScheduler.class);
        mStatusBarIconController = mock(StatusBarIconController.class);
        mStatusBarStateController = mock(StatusBarStateController.class);
        mKeyguardStateController = mock(KeyguardStateController.class);
        mOperatorNameViewController = mock(OperatorNameViewController.class);
        mOperatorNameViewControllerFactory = mock(OperatorNameViewController.Factory.class);
        when(mOperatorNameViewControllerFactory.create(any(), any()))
                .thenReturn(mOperatorNameViewController);
        when(mIconManagerFactory.create(any(), any(), any())).thenReturn(mIconManager);
        mSecureSettings = mock(SecureSettings.class);

        mShadeExpansionStateManager = new ShadeExpansionStateManager();
        mCollapsedStatusBarViewModel = new FakeHomeStatusBarViewModel(
                mock(StatusBarOperatorNameViewModel.class));
        mCollapsedStatusBarViewBinder = new FakeHomeStatusBarViewBinder();

        HomeStatusBarViewModelFactory homeStatusBarViewModelFactory =
                new HomeStatusBarViewModelFactory() {
            @NonNull
            @Override
            public HomeStatusBarViewModel create() {
                return mCollapsedStatusBarViewModel;
            }
        };

        return new CollapsedStatusBarFragment(
                mStatusBarFragmentComponentFactory,
                mAnimationScheduler,
                mShadeExpansionStateManager,
                mStatusBarIconController,
                mIconManagerFactory,
                homeStatusBarViewModelFactory,
                mCollapsedStatusBarViewBinder,
                mStatusBarHideIconsForBouncerManager,
                mKeyguardStateController,
                mPanelExpansionInteractor,
                mStatusBarStateController,
                mock(NotificationIconContainerStatusBarViewBinder.class),
                mCommandQueue,
                mCarrierConfigTracker,
                new CollapsedStatusBarFragmentLogger(
                        new LogBuffer("TEST", 1, mock(LogcatEchoTracker.class)),
                        new DisableFlagsLogger()
                        ),
                mOperatorNameViewControllerFactory,
                mSecureSettings,
                mExecutor,
                mDumpManager,
                mStatusBarWindowStateController,
                mKeyguardUpdateMonitor,
                mock(DemoModeController.class),
                mStatusBarWindowControllerStore);
    }

    private void setUpDaggerComponent() {
        when(mStatusBarFragmentComponentFactory.create(any(), any()))
                .thenReturn(mHomeStatusBarComponent);
        when(mHomeStatusBarComponent.getHeadsUpAppearanceController())
                .thenReturn(mHeadsUpAppearanceController);
    }

    /**
     * Configure mocks to return values consistent with the secure camera animating itself launched
     * over the keyguard.
     */
    private void mockSecureCameraLaunch(CollapsedStatusBarFragment fragment, boolean launched) {
        when(mKeyguardUpdateMonitor.isSecureCameraLaunchedOverKeyguard()).thenReturn(launched);
        when(mKeyguardStateController.isOccluded()).thenReturn(launched);

        if (launched) {
            fragment.onCameraLaunchGestureDetected(0 /* source */);
        } else {
            for (StatusBarWindowStateListener listener : mStatusBarWindowStateListeners) {
                listener.onStatusBarWindowStateChanged(StatusBarManager.WINDOW_STATE_SHOWING);
            }
        }

        fragment.disable(DEFAULT_DISPLAY, 0, 0, false);
    }

    /**
     * Configure mocks to return values consistent with the secure camera showing over the keyguard
     * with its launch animation finished.
     */
    private void mockSecureCameraLaunchFinished() {
        for (StatusBarWindowStateListener listener : mStatusBarWindowStateListeners) {
            listener.onStatusBarWindowStateChanged(StatusBarManager.WINDOW_STATE_HIDDEN);
        }
    }

    private void mockLockscreenToDreamTransitionFinished() {
        for (StatusBarWindowStateListener listener : mStatusBarWindowStateListeners) {
            listener.onStatusBarWindowStateChanged(StatusBarManager.WINDOW_STATE_HIDDEN);
        }
    }

    private CollapsedStatusBarFragment resumeAndGetFragment() {
        mFragments.dispatchResume();
        processAllMessages();
        CollapsedStatusBarFragment fragment = (CollapsedStatusBarFragment) mFragment;
        fragment.disableAnimationsForTesting();
        return fragment;
    }

    private View getUserChipView() {
        return mFragment.getView().findViewById(R.id.user_switcher_container);
    }

    private View getClockView() {
        return mFragment.getView().findViewById(R.id.clock);
    }

    private View getEndSideContentView() {
        return mFragment.getView().findViewById(R.id.status_bar_end_side_content);
    }

    private View getNotificationAreaView() {
        return mFragment.getView().findViewById(R.id.notificationIcons);
    }

    private View getPrimaryOngoingActivityChipView() {
        return mFragment.getView().findViewById(R.id.ongoing_activity_chip_primary);
    }

    private View getSecondaryOngoingActivityChipView() {
        return mFragment.getView().findViewById(R.id.ongoing_activity_chip_secondary);
    }
}
