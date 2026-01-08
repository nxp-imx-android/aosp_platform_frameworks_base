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

package com.android.systemui.accessibility.floatingmenu;

import static android.provider.Settings.Secure.ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.systemui.util.kotlin.JavaAdapterKt.collectFlow;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IUserInitializationCompleteCallback;

import androidx.annotation.MainThread;

import com.android.compose.animation.scene.SceneKey;
import com.android.internal.annotations.VisibleForTesting;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.bluetooth.HearingAidDeviceManager;
import com.android.systemui.Flags;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver.AccessibilityButtonMode;
import com.android.systemui.accessibility.AccessibilityButtonTargetsObserver;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.res.R;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.scene.shared.model.KeyguardScenesKt;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.settings.SecureSettings;

import kotlinx.coroutines.CoroutineScope;

import java.util.function.Consumer;

import javax.inject.Inject;

/** A controller to handle the lifecycle of accessibility floating menu. */
@MainThread
@SysUISingleton
public class AccessibilityFloatingMenuController implements
        AccessibilityButtonModeObserver.ModeChangedListener,
        AccessibilityButtonTargetsObserver.TargetsChangedListener {

    private final AccessibilityButtonModeObserver mAccessibilityButtonModeObserver;
    private final AccessibilityButtonTargetsObserver mAccessibilityButtonTargetsObserver;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final KeyguardTransitionInteractor mKeyguardInteractor;
    private final SceneInteractor mSceneInteractor;
    private final UserTracker mUserTracker;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final DisplayManager mDisplayManager;
    private final AccessibilityManager mAccessibilityManager;
    private final HearingAidDeviceManager mHearingAidDeviceManager;
    private final CoroutineScope mCoroutineScope;

    private final SecureSettings mSecureSettings;
    private final DisplayTracker mDisplayTracker;
    private final NavigationModeController mNavigationModeController;
    @VisibleForTesting
    IAccessibilityFloatingMenu mFloatingMenu;
    private int mBtnMode;
    private String mBtnTargets;
    private boolean mIsKeyguardVisible;
    private boolean mIsUserInInitialization;
    @VisibleForTesting
    Handler mHandler;

    @VisibleForTesting
    final KeyguardUpdateMonitorCallback mKeyguardCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onUserUnlocked() {
            handleFloatingMenuVisibility();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean visible) {
            mIsKeyguardVisible = visible;
            handleFloatingMenuVisibility();
        }

        @Override
        public void onUserSwitching(int userId) {
            destroyFloatingMenu();
            mIsUserInInitialization = true;
        }
    };

    @VisibleForTesting
    final UserInitializationCompleteCallback mUserInitializationCompleteCallback =
            new UserInitializationCompleteCallback();

    @VisibleForTesting
    final Consumer<KeyguardState> mKeyguardStateConsumer = (state) -> {
        mIsKeyguardVisible = switch(state) {
            case KeyguardState.LOCKSCREEN, KeyguardState.DOZING, KeyguardState.AOD -> true;
            default -> false;
        };
        handleFloatingMenuVisibility();
    };

    final Consumer<SceneKey> mSceneConsumer = (scene) -> {
        mIsKeyguardVisible = KeyguardScenesKt.isKeyguardScene(scene);
        handleFloatingMenuVisibility();
    };

    final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            destroyFloatingMenu();
            mIsUserInInitialization = true;
        }
    };

    @Inject
    public AccessibilityFloatingMenuController(Context context,
            WindowManager windowManager,
            DisplayManager displayManager,
            AccessibilityManager accessibilityManager,
            AccessibilityButtonTargetsObserver accessibilityButtonTargetsObserver,
            AccessibilityButtonModeObserver accessibilityButtonModeObserver,
            @Nullable HearingAidDeviceManager hearingAidDeviceManager,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            SecureSettings secureSettings,
            DisplayTracker displayTracker,
            NavigationModeController navigationModeController,
            @Main Handler handler,
            @Application CoroutineScope coroutineScope,
            KeyguardTransitionInteractor keyguardInteractor,
            SceneInteractor sceneInteractor,
            UserTracker userTracker) {
        mContext = context;
        mWindowManager = windowManager;
        mDisplayManager = displayManager;
        mAccessibilityManager = accessibilityManager;
        mAccessibilityButtonTargetsObserver = accessibilityButtonTargetsObserver;
        mAccessibilityButtonModeObserver = accessibilityButtonModeObserver;
        mHearingAidDeviceManager = hearingAidDeviceManager;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mSecureSettings = secureSettings;
        mDisplayTracker = displayTracker;
        mNavigationModeController = navigationModeController;
        mHandler = handler;
        mKeyguardInteractor = keyguardInteractor;
        mSceneInteractor = sceneInteractor;
        mCoroutineScope = coroutineScope;
        mUserTracker = userTracker;

        mIsKeyguardVisible = false;
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button mode changes.
     *
     * @param mode Current accessibility button mode.
     */
    @Override
    public void onAccessibilityButtonModeChanged(@AccessibilityButtonMode int mode) {
        mBtnMode = mode;
        handleFloatingMenuVisibility();
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button targets
     * changes.
     * List should come from {@link Settings.Secure#ACCESSIBILITY_BUTTON_TARGETS}.
     * @param targets Current accessibility button list.
     */
    @Override
    public void onAccessibilityButtonTargetsChanged(String targets) {
        mBtnTargets = targets;
        handleFloatingMenuVisibility();
    }

    /** Initializes the AccessibilityFloatingMenuController configurations. */
    public void init() {
        mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
        mBtnTargets = mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
        registerContentObservers();
    }

    private void registerContentObservers() {
        mAccessibilityButtonModeObserver.addListener(this);
        mAccessibilityButtonTargetsObserver.addListener(this);
        if (Flags.keyguardInteractorForFloatingButton()) {
            if (mCoroutineScope != null) {
                if (Flags.sceneContainer()) {
                    collectFlow(mCoroutineScope,
                            mSceneInteractor.getCurrentScene(), mSceneConsumer);
                } else {
                    collectFlow(mCoroutineScope,
                            mKeyguardInteractor.getCurrentKeyguardState(), mKeyguardStateConsumer);
                }
            }
            mUserTracker.addCallback(mUserTrackerCallback, new HandlerExecutor(mHandler));
        } else {
            mKeyguardUpdateMonitor.registerCallback(mKeyguardCallback);
        }
        mAccessibilityManager.registerUserInitializationCompleteCallback(
                mUserInitializationCompleteCallback);
    }

    /**
     * Handles the accessibility floating menu visibility with the given values.
     */
    private void handleFloatingMenuVisibility() {
        if (hasValidScene() && hasValidSettings()) {
            showFloatingMenu();
        } else {
            destroyFloatingMenu();
        }
    }

    private boolean hasValidScene() {
        if (mIsUserInInitialization) {
            return false; // Not allowed during user initialization.
        }
        return !mIsKeyguardVisible;
    }

    private boolean hasValidSettings() {
        return mBtnMode == ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU
                && !TextUtils.isEmpty(mBtnTargets);
    }

    private void showFloatingMenu() {
        if (mFloatingMenu == null) {
            final Display defaultDisplay = mDisplayManager.getDisplay(
                    mDisplayTracker.getDefaultDisplayId());
            final Context windowContext = mContext.createWindowContext(defaultDisplay,
                    TYPE_NAVIGATION_BAR_PANEL, /* options= */ null);
            windowContext.setTheme(R.style.Theme_SystemUI);
            mFloatingMenu = new MenuViewLayerController(windowContext, mWindowManager,
                    mAccessibilityManager, mSecureSettings, mNavigationModeController,
                    mHearingAidDeviceManager);
        }

        mFloatingMenu.show();
    }

    private void destroyFloatingMenu() {
        if (mFloatingMenu == null) {
            return;
        }

        mFloatingMenu.hide();
        mFloatingMenu = null;
    }

    class UserInitializationCompleteCallback
            extends IUserInitializationCompleteCallback.Stub {
        @Override
        public void onUserInitializationComplete(int userId) {
            mIsUserInInitialization = false;
            mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
            mBtnTargets =
                    mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
            mHandler.post(
                    () -> {
                        // Force a refresh by destroying the menu if it exists.
                        destroyFloatingMenu();
                        handleFloatingMenuVisibility();
                    });
        }
    }
}
