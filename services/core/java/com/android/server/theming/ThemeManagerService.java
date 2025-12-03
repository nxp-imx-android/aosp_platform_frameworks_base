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

package com.android.server.theming;

import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import androidx.annotation.VisibleForTesting;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;
import com.android.systemui.monet.ColorScheme;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * ThemeManagerService handles the application of theme overlays across the Android system.
 * <p>
 * It listens for various events like wallpaper changes, user settings modifications, and system
 * events to ensure the appropriate theme overlays are applied.
 * <p>
 * In essence, ThemeManagerService acts as an intermediary that gathers, filters, and transforms
 * various theming-related signals into a cohesive lifecycle for each user, relayed to the
 * ThemeStateManager for managing the actual application of theme overlays.
 * <p>
 * The ThemeService class orchestrates the theming lifecycle by:
 * <p>
 * <ol>
 * Monitoring theme-related events: It listens for changes in wallpaper colors, theming
 * settings, display contrast, user setup completion, profile additions, device lock state,
 * and user lifecycle events.
 * </ol><ol>
 * Loading and applying theme settings, and for notifying other components of changes to the
 * theme. It also handles the registration of content observers for theme-related settings.
 * </ol><ol>
 * Consolidating and filtering inputs: It gathers relevant data from these sources,
 * such as user IDs, seed colors, contrast values, and theming styles, filtering out
 * irrelevant or redundant information.
 * </ol><ol>
 * Transforming data for each user: It processes the collected information on a per-user basis,
 * handling cases where color information comes from presets or when specific styles need to
 * be applied.
 * </ol><ol>
 * Driving the ThemeStateManager lifecycle: It provides a clean, user-specific lishoufecycle
 * to the ThemeStateManager by invoking appropriate methods based on the processed events.
 * This includes informing the state manager about new users, user setup completion,
 * theme style changes, and other relevant events, ensuring the correct application of
 * theme overlays.
 * </ol>
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeManagerService";

    private static final String KEY_COLOR_PALETTE_VERSION = "global_color_palette_version";

    private final ThemeManagerInternal mInternal;
    private final ThemeBinderService mPublic;
    private final Context mContext;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final ThemeStateManager mStateManager;
    private final ThemeOverlayHelper mOverlayHelper;

    private final ThemeWallpaperManager mThemeWallpaperManager;
    private UiModeManagerInternal mUiModeManagerInternal;
    private UserManagerInternal mUserManagerInternal;
    private final SystemPropertiesReader mSystemPropertiesReader;


    public ThemeManagerService(@NonNull Context context) {
        this(context, SystemProperties::get, new ThemeStateManager(context),
                LocalServices.getService(WallpaperManagerInternal.class),
                LocalServices.getService(OverlayManagerInternal.class));
    }

    @VisibleForTesting
    ThemeManagerService(@NonNull Context context,
            @NonNull SystemPropertiesReader systemPropertiesReader,
            ThemeStateManager themeStateManager,
            @Nullable WallpaperManagerInternal wallpaperManagerInternal,
            OverlayManagerInternal overlayManagerInternal) {
        super(context);
        mContext = context;
        mStateManager = themeStateManager;
        mThemeWallpaperManager = new ThemeWallpaperManager(wallpaperManagerInternal);
        mThemeSettingsManager = new ThemeSettingsManager(mThemeWallpaperManager);
        mSystemPropertiesReader = systemPropertiesReader;
        mOverlayHelper = new ThemeOverlayHelper(overlayManagerInternal);

        mInternal = new ThemeManagerInternal(mContext, mThemeSettingsManager,
                mSystemPropertiesReader, mStateManager, mOverlayHelper);
        mPublic = new ThemeBinderService(mContext, mInternal);
    }

    @Override
    public void onStart() {
        publishLocalService(ThemeManagerInternal.class, mInternal);
        publishBinderService(Context.THEME_SERVICE, mPublic.asBinder());
    }

    @Override
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void onBootPhase(@BootPhase int phase) {
        Slog.d(TAG, "onBootPhase: " + phase);
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
            mStateManager.onServicesReady();
            setupListeners();

            // Pre-load users to avoid race conditions with ContentObservers
            int[] userIds = mUserManagerInternal.getUserIds();
            for (int userId : userIds) {
                loadUserThemeState(userId);
            }
        }

        if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY) {
            mStateManager.onBootComplete(/*isPaletteOutdated*/ shouldForceReloadForVersion());
        }

    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        if (shouldIgnoreForHsum(user.getUserHandle(), "onUserStarting")) {
            return;
        }
        int userId = user.getUserHandle().getIdentifier();

        // check if seed color comes from wallpaper or preset
        ThemeSettings userSettings = mInternal.getThemeSettingsOrDefault(userId);

        int seedColor = getEffectiveSeedColor(userSettings, userId);

        Slog.d(TAG, "User: " + user.getUserIdentifier() + " starting");

        mStateManager.onUserStart(user.getUserHandle(),
                /*isSetup*/Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 0, userId) == 1,
                /*seedColor*/ seedColor,
                /*contrast*/ mUiModeManagerInternal.getContrast(userId),
                /*style*/userSettings.themeStyle());
    }

    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        if (shouldIgnoreForHsum(to.getUserHandle(), "onUserSwitching")) {
            return;
        }
        Slog.d(TAG, "User switch from:" + (from != null ? from.getUserIdentifier() : "-") + " to:"
                + to.getUserIdentifier());
        mStateManager.onUserSwitching(from.getUserIdentifier(), to.getUserIdentifier());
    }

    @Override
    public void onUserCompletedEvent(@NonNull TargetUser user, UserCompletedEventType eventType) {
        if (shouldIgnoreForHsum(user.getUserHandle(), "onUserCompletedEvent")) {
            return;
        }
        Slog.d(TAG, "User: " + user.getUserIdentifier() + " completed eventType: "
                + eventType.toString());
    }

    // HELPER METHODS

    private void loadUserThemeState(int userId) {
        if (shouldIgnoreForHsum(UserHandle.of(userId), "loadUserThemeState")) {
            return;
        }

        ThemeSettings userSettings = mInternal.getThemeSettingsOrDefault(userId);
        int seedColor = getEffectiveSeedColor(userSettings, userId);

        boolean isSetup = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, userId) == 1;

        mStateManager.onUserLoad(userId, isSetup, seedColor,
                mUiModeManagerInternal.getContrast(userId),
                userSettings.themeStyle());
    }

    private int getEffectiveSeedColor(ThemeSettings userSettings, int userId) {
        int seedColor;
        if (userSettings.colorSource().equals(VALUE_PRESET)) {
            seedColor = userSettings.systemPalette().toArgb();
        } else {
            Integer wallpaperSeed = mThemeWallpaperManager.getSeedColor(userId);
            seedColor = wallpaperSeed != null ? wallpaperSeed
                    : userSettings.systemPalette().toArgb();
        }
        return seedColor;
    }

    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    private void setupListeners() {
        Executor mainExecutor = mContext.getMainExecutor();
        Handler bgHandler = BackgroundThread.getHandler();

        KeyguardManager keyguardManager = mContext.getSystemService(KeyguardManager.class);
        mUiModeManagerInternal = LocalServices.getService(
                UiModeManagerInternal.class);

        ActivityManagerInternal activityManagerInternal = LocalServices.getService(
                ActivityManagerInternal.class);

        // Profile and overlay changes
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                // Added User
                if (Intent.ACTION_PROFILE_ADDED.equals(action)) {
                    UserHandle newUserHandle = intent.getParcelableExtra(Intent.EXTRA_USER,
                            UserHandle.class);

                    int newUserOrProfileId = newUserHandle.getIdentifier();

                    // Ensure user state is loaded immediately when a new user/profile is added.
                    loadUserThemeState(newUserOrProfileId);

                    int parentId = mStateManager.parentOf(newUserOrProfileId);
                    if (shouldIgnoreForHsum(UserHandle.of(parentId), "onProfileAdd")) {
                        return;
                    }

                    Slog.d(TAG, "User: " + newUserOrProfileId + " added to parent: " + parentId);
                    mStateManager.onProfileAdd(parentId, newUserOrProfileId);

                    return;
                }

                // Overlay Applied
                if (Intent.ACTION_OVERLAY_CHANGED.equals(action)) {
                    final Uri data = intent.getData();
                    if (data == null) {
                        return;
                    }
                    final String changedPackage = data.getSchemeSpecificPart();
                    if ("android".equals(changedPackage)) {
                        final int userId = intent.getIntExtra(Intent.EXTRA_USER_ID,
                                UserHandle.USER_NULL);
                        Slog.i(TAG, "Theme overlays successfully applied for user " + userId);
                        mInternal.notifyThemeChanged(userId);
                    }
                }
            }
        }, filter, null, bgHandler);

        // Wallpaper Color Change
        mThemeWallpaperManager.addOnColorsChangedListener(
                (wallpaperColors, which, displayId, userId, fromForegroundApp) -> {
                    if (shouldIgnoreForHsum(UserHandle.of(userId), "onColorsChanged")) {
                        return;
                    }
                    ThemeSettings userSettings = mInternal.getThemeSettingsOrDefault(userId);
                    if (userSettings.colorSource().equals(VALUE_PRESET)) {
                        Slog.d(TAG, "Wallpaper color change ignored due to preset color source");
                        return;
                    }

                    if (wallpaperColors == null) {
                        Slog.d(TAG,
                                "Wallpaper color change ignored due to WallpaperManager providing"
                                        + " null WallpaperColors");
                        return;
                    }

                    Slog.d(TAG, "User: " + userId + " changed wallpaper");
                    mStateManager.onSeedColorChange(activityManagerInternal.getCurrentUserId(),
                            ColorScheme.getSeedColor(wallpaperColors),
                            fromForegroundApp);
                }, bgHandler);


        mUiModeManagerInternal.addContrastListener((userId, contrast) -> {
            if (shouldIgnoreForHsum(UserHandle.of(userId), "onContrastChange")) {
                return;
            }
            mStateManager.onContrastChange(userId, contrast);
        }, mainExecutor);

        // Sleep
        keyguardManager.addKeyguardLockedStateListener(mainExecutor, isKeyguardLocked -> {
            if (isKeyguardLocked) {
                Slog.d(TAG, "Keyguard locked");
                mStateManager.onLockStateChange(true);
            }
        });

        // Setup Change
        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), false,
                new ContentObserver(bgHandler) {
                    @Override
                    public void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                            int flags, int userId) {
                        if (shouldIgnoreForHsum(UserHandle.of(userId), "onFinishSetup")) {
                            return;
                        }
                        Slog.d(TAG, "User: " + userId + " setup complete");
                        mStateManager.onFinishSetup(userId);
                    }
                }, UserHandle.USER_ALL);

        // Style Change
        resolver.registerContentObserver(
                // This listener is also called when choosing a style with fixed color.
                // Case in which we should fork the call to onSeedColorChange and onStyleChange
                // in this case
                Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                false, new ContentObserver(bgHandler) {
                    @Override
                    public void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                            int flags, int userId) {
                        if (shouldIgnoreForHsum(UserHandle.of(userId), "onStyleChange")) {
                            return;
                        }
                        ThemeSettings userSettings = mInternal.getThemeSettingsOrDefault(userId);

                        // we now check the source of the color is "preset", case in which we fork
                        // the event

                        if (userSettings.colorSource().equals(VALUE_PRESET)) {
                            int newSeed = userSettings.systemPalette().toArgb();

                            Slog.d(TAG, "User: " + userId + " changed seed to "
                                    + Integer.toHexString(newSeed));
                            mStateManager.onSeedColorChange(userId, newSeed, true);
                        }

                        Slog.d(TAG, "User: " + userId + " changed style to "
                                + ThemeStyle.name(userSettings.themeStyle()));
                        mStateManager.onStyleChange(userId, userSettings.themeStyle());
                    }
                }, UserHandle.USER_ALL);
    }

    private boolean shouldForceReloadForVersion() {
        String storedVersion = Settings.Global.getString(mContext.getContentResolver(),
                KEY_COLOR_PALETTE_VERSION);
        String currentVersion = mSystemPropertiesReader.get("ro.build.date.utc", null);

        if (TextUtils.isEmpty(currentVersion)) {
            Slog.i(TAG, "Palette version missing. Refreshing overlays");
            return true;
        }

        if (storedVersion != null && Objects.equals(storedVersion, currentVersion)) return false;

        Slog.i(TAG, "Palette version bumped from " + storedVersion + " to " + currentVersion);
        Settings.Global.putString(mContext.getContentResolver(), KEY_COLOR_PALETTE_VERSION,
                currentVersion);
        return true;
    }

    /**
     * Gates calls to prevent processing for the system user when in Headless System User Mode
     * (HSUM). In these ThemeOverlayHelper ensures the System user adopts current user's overlays.
     *
     * @param userHandle The {@link UserHandle} to check.
     * @param methodName The name of the method being gated, for logging purposes.
     * @return {@code true} if the call should be ignored (i.e., it's the system user in HSUM),
     * {@code false} otherwise.
     */
    private boolean shouldIgnoreForHsum(UserHandle userHandle, String methodName) {
        if (mUserManagerInternal != null && mUserManagerInternal.isHeadlessSystemUserMode()
                && userHandle.isSystem()) {
            Slog.d(TAG, "Ignoring " + methodName + " for system user in HSUM");
            return true;
        }
        return false;
    }
}

