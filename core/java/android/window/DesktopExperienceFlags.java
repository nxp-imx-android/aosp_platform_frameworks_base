/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.window;

import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DEFAULT_DISPLAY_IN_TOPOLOGY_SWITCH;
import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_MIRROR_IN_LOCK_TASK_MODE;
import static com.android.server.display.feature.flags.Flags.enableDisplayContentModeManagement;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityThread;
import android.content.Context;
import android.os.SystemProperties;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.R;
import com.android.window.flags.Flags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Checks Desktop Experience flag state.
 *
 * <p>This enum provides a centralized way to control the behavior of flags related to desktop
 * experience features which are aiming for developer preview before their release. It allows
 * developer option to override the default behavior of these flags.
 *
 * <p>The flags here will be controlled by the {@code
 * persist.wm.debug.desktop_experience_devopts} system property.
 *
 * <p>NOTE: Flags should only be added to this enum when they have received Product and UX alignment
 * that the feature is ready for developer preview, otherwise just do a flag check.
 *
 * @hide
 */
@RavenwoodKeepWholeClass
public enum DesktopExperienceFlags {
    // go/keep-sorted start
    CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT(
            Flags::closeFullscreenAndSplitscreenKeyboardShortcut, false,
            Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT),
    CONNECTED_DISPLAYS_CURSOR(com.android.input.flags.Flags::connectedDisplaysCursor, true,
            com.android.input.flags.Flags.FLAG_CONNECTED_DISPLAYS_CURSOR),
    DEFER_RESUME_FOCUS_IN_NON_FOCUSED_WINDOW(Flags::deferResumeFocusInNonFocusedWindow, false,
            Flags.FLAG_DEFER_RESUME_FOCUS_IN_NON_FOCUSED_WINDOW),
    DIMMING_WALLPAPER_FOR_MAXIMIZED_AND_TILED(Flags::dimmingWallpaperForMaximizedAndTiled, true,
            Flags.FLAG_DIMMING_WALLPAPER_FOR_MAXIMIZED_AND_TILED),
    DISPLAY_TOPOLOGY(com.android.server.display.feature.flags.Flags::displayTopology, true,
            com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY),
    ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS(
            Flags::enableActivityEmbeddingSupportForConnectedDisplays, true,
            Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS),
    ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS(
            Flags::enableAddWindowDecorationToAllTasks, false,
            Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS),
    ENABLE_APP_HANDLE_POSITION_REPORTING(Flags::enableAppHandlePositionReporting, false,
            Flags.FLAG_ENABLE_APP_HANDLE_POSITION_REPORTING),
    ENABLE_AUTO_RECOVERY_FROM_SELF_KILL(Flags::enableAutoRecoveryFromSelfKill, false,
            Flags.FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL),
    ENABLE_AUTO_ROTATE_ON_SLATE_STATE(Flags::enableAutoRotateOnSlateState, false,
            Flags.FLAG_ENABLE_AUTO_ROTATE_ON_SLATE_STATE),
    ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT(Flags::enableBoundsRestoringOnDragExit, false,
            Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT),
    ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY(Flags::enableBugFixesForSecondaryDisplay, true,
            Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY),
    ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX(
            Flags::enableCameraCompatExternalDisplayRotationBugfix, false,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX),
    ENABLE_CONNECTED_DISPLAYS_DND(Flags::enableConnectedDisplaysDnd, true,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND),
    ENABLE_CONNECTED_DISPLAYS_PIP(Flags::enableConnectedDisplaysPip, true,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP),
    ENABLE_CONNECTED_DISPLAYS_WALLPAPER(android.app.Flags::enableConnectedDisplaysWallpaper, true,
            android.app.Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WALLPAPER),
    ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG(Flags::enableConnectedDisplaysWindowDrag, true,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG),
    ENABLE_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION(Flags::defaultDeskWithoutWarmupMigration, false,
            Flags.FLAG_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION),
    ENABLE_DEFAULT_DISPLAY_IN_TOPOLOGY_SWITCH(
            com.android.server.display.feature.flags.Flags::enableDefaultDisplayInTopologySwitch,
            true, FLAG_ENABLE_DEFAULT_DISPLAY_IN_TOPOLOGY_SWITCH),
    ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX(
            Flags::enableDesktopFirstBasedDefaultToDesktopBugfix, true,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX),
    ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE(Flags::enableDesktopFirstBasedDragToMaximize, true,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE),
    ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX(Flags::enableDesktopFirstFullscreenRefocusBugfix,
            false, Flags.FLAG_ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX),
    ENABLE_DESKTOP_FIRST_LISTENER(Flags::enableDesktopFirstListener, false,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_LISTENER),
    ENABLE_DESKTOP_FIRST_POLICY_IN_LPM(Flags::enableDesktopFirstPolicyInLpm, false,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM),
    ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX(Flags::enableDesktopFirstTopFullscreenBugfix,
            false, Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX),
    ENABLE_DESKTOP_IME_BUGFIX(Flags::enableDesktopImeBugfix, true,
            Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX),
    ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS(Flags::enableDesktopTaskbarOnFreeformDisplays,
            true, Flags.FLAG_ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS),
    ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION(
            Flags::enableDesktopWindowingAppToWebEducationIntegration, true,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION),
    ENABLE_DESKTOP_WINDOWING_MULTI_ACTIVITY_PIP_KEEP_PARENT_OPEN(
            Flags::enableDesktopWindowingMultiActivityPipKeepParentOpen, false,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MULTI_ACTIVITY_PIP_KEEP_PARENT_OPEN),
    ENABLE_DESKTOP_WINDOWING_PIP(Flags::enableDesktopWindowingPip, true,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP),
    ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT(
            com.android.server.display.feature.flags.Flags::enableDisplayContentModeManagement,
            true, FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT),
    ENABLE_DISPLAY_DISCONNECT_INTERACTION(Flags::enableDisplayDisconnectInteraction, true,
            Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION),
    ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS(Flags::enableDisplayFocusInShellTransitions, true,
            Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS),
    ENABLE_DISPLAY_MIRROR_IN_LOCK_TASK_MODE(
            com.android.server.display.feature.flags.Flags::enableDisplayMirrorInLockTaskMode,
            false, FLAG_ENABLE_DISPLAY_MIRROR_IN_LOCK_TASK_MODE),
    ENABLE_DISPLAY_RECONNECT_INTERACTION(Flags::enableDisplayReconnectInteraction, true,
            Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION),
    ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING(Flags::enableDisplayWindowingModeSwitching, true,
            Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING),
    ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS(Flags::enableDraggingPipAcrossDisplays, false,
            Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS),
    ENABLE_DRAG_TO_MAXIMIZE(Flags::enableDragToMaximize, true, Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE),
    ENABLE_DRAWING_APP_HANDLE(Flags::enableDrawingAppHandle, false,
            Flags.FLAG_ENABLE_DRAWING_APP_HANDLE),
    ENABLE_FREEFORM_BOX_SHADOWS_V2(Flags::enableFreeformBoxShadowsV2, false,
            Flags.FLAG_ENABLE_FREEFORM_BOX_SHADOWS_V2),
    ENABLE_FULLSCREEN_WINDOW_CONTROLS(Flags::enableFullscreenWindowControls, false,
            Flags.FLAG_ENABLE_FULLSCREEN_WINDOW_CONTROLS),
    ENABLE_GAME_CONTROLS_ENTRY_IN_HANDLE_MENU(Flags::enableGameControlsEntryInHandleMenu, false,
            Flags.FLAG_ENABLE_GAME_CONTROLS_ENTRY_IN_HANDLE_MENU),
    ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS(
            Flags::enableInteractionDependentTabTearingBounds, false,
            Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS),
    ENABLE_INTERACTIVE_PICTURE_IN_PICTURE(Flags::enableInteractivePictureInPicture, false,
            Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE),
    ENABLE_IS_TASK_MOVE_ALLOWED_ON_DISPLAY_API(Flags::enableIsTaskMoveAllowedOnDisplayApi, false,
            Flags.FLAG_ENABLE_IS_TASK_MOVE_ALLOWED_ON_DISPLAY_API),
    ENABLE_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS(Flags::keyboardShortcutsToSwitchDesks, true,
            Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS),
    ENABLE_MIRROR_DISPLAY_NO_ACTIVITY(Flags::enableMirrorDisplayNoActivity, true,
            Flags.FLAG_ENABLE_MIRROR_DISPLAY_NO_ACTIVITY),
    ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE(Flags::enableMultidisplayTrackpadBackGesture, true,
            Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE),
    ENABLE_MULTIPLE_DESKTOPS_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS(
            Flags::enableMultipleDesktopsDefaultActivationInDesktopFirstDisplays, false,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS),
    ENABLE_MULTIPLE_DESKTOPS_BACKEND(Flags::enableMultipleDesktopsBackend, true,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND),
    ENABLE_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS_BUGFIX(
            Flags::nestedTasksWithIndependentBoundsBugfix, true,
            Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS_BUGFIX),
    ENABLE_NON_DEFAULT_DISPLAY_SPLIT(Flags::enableNonDefaultDisplaySplit, true,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT),
    ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX(Flags::enableNonDefaultDisplaySplitBugfix, false,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX),
    ENABLE_NO_WINDOW_DECORATION_FOR_DESKS(Flags::enableNoWindowDecorationForDesks, true,
        Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS),
    ENABLE_PARALLEL_CD_TRANSITIONS_DURING_RECENTS(Flags::parallelCdTransitionsDuringRecents, true,
            Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS),
    ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY(Flags::enablePerDisplayDesktopWallpaperActivity,
            true, Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY),
    ENABLE_PINNING_APP_WITH_CONTEXT_MENU(Flags::enablePinningAppWithContextMenu, true,
            Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU),
    ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS(Flags::enablePresentationForConnectedDisplays, true,
            Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS),
    ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE(Flags::enableProjectedDisplayDesktopMode, true,
            Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE),
    ENABLE_REENABLE_APP_HANDLE_COLOR_ANIMATIONS(Flags::reenableAppHandleColorAnimations,
            false, Flags.FLAG_REENABLE_APP_HANDLE_COLOR_ANIMATIONS),
    ENABLE_REJECT_HOME_TRANSITION(
            Flags::enableRejectHomeTransition, true,
            Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION),
    ENABLE_REMOVE_DESK_ON_LAST_TASK_REMOVAL(Flags::removeDeskOnLastTaskRemoval, false,
            Flags.FLAG_REMOVE_DESK_ON_LAST_TASK_REMOVAL),
    ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER(Flags::enableRemoveStatusBarInputLayer, false,
            Flags.FLAG_ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER),
    ENABLE_REQUEST_FULLSCREEN_REFACTOR(
            Flags::enableRequestFullscreenRefactor, false,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_REFACTOR),
    ENABLE_REQUIRE_MOVABLE_TASK_API(Flags::enableRequireMovableTaskApi, false,
            Flags.FLAG_ENABLE_REQUIRE_MOVABLE_TASK_API),
    ENABLE_RESTRICT_FREEFORM_HIDDEN_SYSTEM_BARS_TO_FILLING_TASKS(
            Flags::restrictFreeformHiddenSystemBarsToFillingTasks, true,
            Flags.FLAG_RESTRICT_FREEFORM_HIDDEN_SYSTEM_BARS_TO_FILLING_TASKS),
    ENABLE_SEE_THROUGH_TASK_FRAGMENTS(Flags::enableSeeThroughTaskFragments,
            true, Flags.FLAG_ENABLE_SEE_THROUGH_TASK_FRAGMENTS),
    ENABLE_SYS_DECORS_CALLBACKS_VIA_WM(Flags::enableSysDecorsCallbacksViaWm,
            true, Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM),
    ENABLE_TASKBAR_CONNECTED_DISPLAYS(Flags::enableTaskbarConnectedDisplays, true,
            Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS),
    ENABLE_TASK_MOVE_ALLOWED_LISTENER_API(Flags::enableTaskMoveAllowedListenerApi, false,
            Flags.FLAG_ENABLE_TASK_MOVE_ALLOWED_LISTENER_API),
    ENABLE_TILE_RESIZING(Flags::enableTileResizing, true, Flags.FLAG_ENABLE_TILE_RESIZING),
    ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS(
            Flags::enableWindowingTransitionHandlersObservers, true,
            Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS),
    ENABLE_WINDOW_DECORATION_REFACTOR(Flags::enableWindowDecorationRefactor, false,
            Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR),
    ENABLE_WINDOW_REPOSITIONING_API(Flags::enableWindowRepositioningApi, false,
            Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API),
    ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS(Flags::enterDesktopByDefaultOnFreeformDisplays,
            true, Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS),
    EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS(Flags::excludeDeskRootsFromDesktopTasks,
            true, Flags.FLAG_EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS),
    FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH(Flags::formFactorBasedDesktopFirstSwitch, true,
            Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH),
    REPARENT_WINDOW_TOKEN_API(Flags::reparentWindowTokenApi, true,
            Flags.FLAG_REPARENT_WINDOW_TOKEN_API),
    REPOSITORY_BASED_PERSISTENCE(Flags::repositoryBasedPersistence, false,
            Flags.FLAG_REPOSITORY_BASED_PERSISTENCE),
    SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT(
            Flags::skipDeactivationOfDeskWithNothingInFront, true,
            Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT),
    TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY(
            Flags::toggleFullscreenStateViaFullscreenKey, false,
            Flags.FLAG_TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY),
    UPDATE_TDAS_TASK_MOVE_ALLOWED_ON_DESKTOP_FIRST_CHANGE(
            Flags::updateTdasTaskMoveAllowedOnDesktopFirstChange, false,
            Flags.FLAG_UPDATE_TDAS_TASK_MOVE_ALLOWED_ON_DESKTOP_FIRST_CHANGE),
    USE_RESOURCES_FROM_CONTEXT_TO_CREATE_DRAWABLE_ICONS(
            com.android.graphics.flags.Flags::useResourcesFromContextToCreateDrawableIcons,
            true,
            com.android.graphics.flags.Flags
                    .FLAG_USE_RESOURCES_FROM_CONTEXT_TO_CREATE_DRAWABLE_ICONS)
    // go/keep-sorted end
    ;

    /** Whether the desktop experience developer option is supported. */
    static boolean isDesktopExperienceDevOptionSupported() {
        if (!Flags.showDesktopExperienceDevOption()) {
            return false;
        }
        boolean shouldEnforceDeviceRestrictions = SystemProperties.getBoolean(
                "persist.wm.debug.desktop_mode_enforce_device_restrictions", true);
        if (!shouldEnforceDeviceRestrictions) {
            return true;
        }
        final Context context = getApplicationContext();
        if (context == null) {
            return false;
        }
        // Simplified version of DesktopModeHelper.isDeviceEligibleForDesktopMode, as the
        // developer option cannot be considered when we check eligibility.
        return context.getResources().getBoolean(R.bool.config_isDesktopModeSupported);
    }

    /**
     * Flag class, to be used in case the enum cannot be used because the flag is not accessible.
     *
     * <p>This class will still use the process-wide cache.
     */
    public static class DesktopExperienceFlag {
        // Function called to obtain aconfig flag value.
        private final BooleanSupplier mFlagFunction;
        // Name of the flag, used for adb commands.
        private final String mFlagName;
        // Whether the flag state should be affected by developer option.
        private final boolean mShouldOverrideByDevOption;

        public DesktopExperienceFlag(BooleanSupplier flagFunction,
                boolean shouldOverrideByDevOption,
                @Nullable String flagName) {
            this.mFlagFunction = flagFunction;
            this.mFlagName = flagName;
            this.mShouldOverrideByDevOption = shouldOverrideByDevOption;
            if (Flags.showDesktopExperienceDevOption()) {
                registerFlag(flagName, this);
            }
        }

        /**
         * Determines state of flag based on the actual flag and desktop experience developer option
         * overrides.
         *
         * The assumption is that the flag's value doesn't change at runtime, or if it changes the
         * user will reboot very soon so being inconsistent across threads is ok.
         */
        public boolean isTrue() {
            return isFlagTrue(mFlagFunction, mShouldOverrideByDevOption);
        }

        public String getFlagName() {
            return mFlagName;
        }

        public boolean getFlagValue() {
            return mFlagFunction.getAsBoolean();
        }

        public boolean isOverridable() {
            return mShouldOverrideByDevOption;
        }
    }

    private static final String TAG = "DesktopExperienceFlags";
    // Function called to obtain aconfig flag value.
    private final BooleanSupplier mFlagFunction;
    // Name of the flag, used for adb commands.
    private final String mFlagName;
    // Whether the flag state should be affected by developer option.
    private final boolean mShouldOverrideByDevOption;

    // Local cache for toggle override, which is initialized once on its first access. It needs to
    // be refreshed only on reboots as overridden state is expected to take effect on reboots.
    @Nullable
    private static Boolean sCachedToggleOverride;

    // Local cache for the application context.
    @Nullable
    private static Context sApplicationContext;

    /**
     * Local cache of dynamically defined flag, organised by name.
     *
     * <p> Create an array with a capacity of 10, which should be plenty.
     */
    private static Map<String, DesktopExperienceFlag> sDynamicFlags = new ArrayMap<>(10);

    public static final String SYSTEM_PROPERTY_NAME = "persist.wm.debug.desktop_experience_devopts";
    public static final String SYSTEM_PROPERTY_OVERRIDE_PREFIX =
            "persist.wm.debug.desktop_experience.add_dev_option.";

    DesktopExperienceFlags(BooleanSupplier flagFunction, boolean shouldOverrideByDevOption,
            @NonNull String flagName) {
        this.mFlagFunction = flagFunction;
        this.mFlagName = flagName;
        this.mShouldOverrideByDevOption = shouldOverrideByDevOption;
    }

    /**
     * Determines state of flag based on the actual flag and desktop experience developer option
     * overrides.
     *
     * The assumption is that the flag's value doesn't change at runtime, or if it changes the
     * user will reboot very soon so being inconsistent across threads is ok.
     */
    public boolean isTrue() {
        return isFlagTrue(mFlagFunction, mShouldOverrideByDevOption);
    }

    public boolean getFlagValue() {
        return mFlagFunction.getAsBoolean();
    }

    public String getFlagName() {
        return mFlagName;
    }

    /** Returns whether or not the developer option can override that flag. */
    public boolean isOverridable() {
        return mShouldOverrideByDevOption;
    }

    private static boolean isFlagTrue(
            BooleanSupplier flagFunction, boolean shouldOverrideByDevOption) {
        if (shouldOverrideByDevOption && getToggleOverride()) {
            return true;
        }
        return flagFunction.getAsBoolean();
    }

    private static void registerFlag(String name, DesktopExperienceFlag flag) {
        sDynamicFlags.put(name, flag);
    }

    public static List<DesktopExperienceFlag> getRegisteredFlags() {
        return new ArrayList<>(sDynamicFlags.values());
    }

    /** Check whether the flags are overridden to true or not. */
    public static boolean getToggleOverride() {
        // If cached, return it
        if (sCachedToggleOverride != null) {
            return sCachedToggleOverride;
        }

        // Otherwise, fetch and cache it
        boolean override = isToggleOverriddenBySystem();
        sCachedToggleOverride = override;
        Log.d(TAG, "Toggle override initialized to: " + override);
        return override;
    }

    static Context getApplicationContext() {
        if (sApplicationContext == null) {
            final Context application = ActivityThread.currentApplication();
            if (application == null) {
                Log.w(TAG, "Could not get the current application.");
                return null;
            }
            sApplicationContext = application;
        }
        return sApplicationContext;
    }

    /** Returns whether the toggle is overridden by the relevant system property.. */
    private static boolean isToggleOverriddenBySystem() {
        // We never override if display content mode management is enabled (except when desktop
        // mode dev option is enabled on the device, which indicates that the device only supports
        // desktop mode) or if the desktop experience dev option is not enabled in the build.
        if ((enableDisplayContentModeManagement() && (!isDesktopModeDevOptionSupported()))
                || !Flags.showDesktopExperienceDevOption()) {
            return false;
        }
        return SystemProperties.getBoolean(SYSTEM_PROPERTY_NAME, false);
    }

    private static boolean isDesktopModeDevOptionSupported() {
        Context context = getApplicationContext();
        if (context == null) {
            return false;
        }

        return context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported);
    }
}

