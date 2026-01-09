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

package com.android.internal.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.AppLockInternal;
import android.app.AppLockInternal.PackageLockedStateListener;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.View;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.util.Objects;

/**
 * An activity that presents a {@link BiometricPrompt} to the user for unlocking a package locked by
 * App Lock.
 *
 * <p>This activity is launched when the user attempts to open an application that is currently
 * locked by the App Lock feature. It handles the full authentication lifecycle, including showing
 * the {@link BiometricPrompt}, processing the result, and unlocking the package upon success.
 *
 * <p>This activity operates in two modes, depending on the presence of a target
 * {@link IntentSender}:
 * <ul>
 *     <li><b>Intercept Mode (target exists):</b> The activity acts as a transparent overlay that
 *         intercepts the launch of a locked app. It immediately shows a {@link BiometricPrompt},
 *         and upon successful authentication, it launches the original target intent.
 *     <li><b>Locked Task Mode (target is {@code null}):</b> The activity provides a default UI with
 *         a background that continuously shows a {@link BiometricPrompt}, and the back button is
 *         disabled. This mode is intended for scenarios like a locked task overlay, where the user
 *         is returned to a locked app's task.
 * </ul>
 *
 * <p>The activity is designed to be transient and will finish itself under several conditions:
 * <ul>
 *     <li>If the App Lock feature is disabled.</li>
 *     <li>If the initiating intent is missing required data (package name or user ID).</li>
 *     <li>If the target package is already unlocked when the activity starts or becomes unlocked
 *         while the activity is visible.</li>
 *     <li>After a successful authentication.</li>
 * </ul>
 *
 * <p>This activity should only be started by the system.
 *
 * @hide
 */
public final class LockedAppActivity extends Activity {

    private static final String TAG = "LockedAppActivity";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.DEBUG);
    private static final String SYSTEM_PACKAGE_NAME = "android";

    @Nullable
    private static Injector sInjector;

    private final Injector mInjector = sInjector == null ? new Injector() : sInjector;

    private final AppLockInternal mAppLockInternal = LocalServices.getService(
            AppLockInternal.class);
    private final OnBackInvokedCallback mOnBackInvokedCallback = () -> {
        // Do nothing.
    };
    private final BiometricPrompt.AuthenticationCallback mAuthenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if (DEBUG) {
                        Slog.d(TAG, "Authentication succeeded");
                    }
                    mIsBiometricPromptShowing = false;
                    mAppLockInternal.setAppLockEnabledPackageSuccessfullyAuthenticated(
                            mPackageName, mUserId);
                    // In intercept mode, send the original target intent after unlocking.
                    if (isInterceptMode()) {
                        mInjector.sendTargetIntent(LockedAppActivity.this, mTarget);
                    }
                    finish();
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Slog.w(TAG, "Authentication error: " + errorCode + " " + errString);
                    mIsBiometricPromptShowing = false;
                    // In intercept mode, finish the activity to unblock the UI.
                    if (isInterceptMode()) {
                        finish();
                    }
                    // TODO(b/451913532): Handle authentication errors when this activity is a
                    //  locked task overlay.
                }
            };

    private CancellationSignal mCancellationSignal;
    private AppLockLockedStateListener mPackageLockedStateListener;

    private boolean mIsPackageUnlocked;
    private boolean mIsBiometricPromptShowing;

    // Member variables initialized in onCreate for performance.
    private String mPackageName;
    private int mUserId;
    private CharSequence mPackageLabel;
    @Nullable
    private IntentSender mTarget;
    @Nullable
    private Bitmap mPackageLogo;

    /**
     * Creates an {@link Intent} to launch {@link LockedAppActivity}.
     *
     * <p>This activity operates in two modes, determined by the {@code target} parameter:
     * <ul>
     *     <li><b>Intercept Mode:</b> If a non-null {@code target} is provided, the activity acts as
     *         a transparent overlay to intercept the launch of a locked app. Upon successful
     *         authentication, it sends the {@code target} intent.
     *     <li><b>Locked Task Mode:</b> If {@code target} is {@code null}, the activity provides a
     *         default UI and is intended for use as a locked task overlay.
     * </ul>
     *
     * @param packageName the name of the package to unlock.
     * @param userId      the user ID for which the package is locked.
     * @param target      the {@link IntentSender} to be triggered after successful authentication.
     *                    If non-null, the activity runs in intercept mode. If null, it runs in
     *                    locked task mode.
     * @return a configured {@link Intent} to start {@link LockedAppActivity}.
     */
    public static Intent createLockedAppActivityIntent(@NonNull String packageName,
            int userId, @Nullable IntentSender target) {
        Objects.requireNonNull(packageName);

        return new Intent()
                .setClassName(SYSTEM_PACKAGE_NAME, LockedAppActivity.class.getName())
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER_ID, userId)
                .putExtra(Intent.EXTRA_INTENT, target)
                .setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    /**
     * Sets the {@link Injector} for testing purposes.
     *
     * <p>This method allows replacing the default injector with a mock implementation to facilitate
     * testing of this activity. This should only be used in debuggable builds.
     *
     * @param injector the {@link Injector} to use for testing, or {@code null} to reset to the
     *                 default.
     * @throws SecurityException if called in a non-debuggable build.
     */
    @VisibleForTesting
    public static void setInjectorForTesting(@Nullable Injector injector) {
        if (!Build.IS_DEBUGGABLE) {
            throw new SecurityException("Injector should only be set in debuggable builds.");
        }
        sInjector = injector;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Initialize member variables from the intent first.
        initStateFromIntent();

        // The theme must be applied before super.onCreate() to ensure the window is created with
        // the correct style.
        applyTheme();

        super.onCreate(savedInstanceState);

        // Set up the UI based on the activity's mode.
        setupUi();

        // Precondition: Check App Lock feature flags.
        if (!android.security.Flags.appLockApis() || !android.security.Flags.appLockCore()) {
            Slog.w(TAG, "App Lock implementation is not enabled, finishing");
            finish();
            return;
        }

        // Check for valid Intent.
        if (mPackageName == null) {
            Slog.w(TAG, "Package name is null, finishing");
            finish();
            return;
        }
        if (mUserId == UserHandle.USER_NULL) {
            Slog.w(TAG, "No userId, finishing");
            finish();
            return;
        }

        if (finishIfUnlocked(mPackageName, mUserId)) {
            return;
        }

        mPackageLabel = getPackageLabel(mPackageName);
        if (mPackageLabel == null) {
            Slog.e(TAG, "Package label for " + mPackageName + " is null, finishing");
            finish();
            return;
        }
        mPackageLogo = convertDrawableToBitmap(getPackageLogo(mPackageName));

        // Register locked state listener.
        mPackageLockedStateListener = new AppLockLockedStateListener(this, mPackageName, mUserId,
                mTarget);
        mAppLockInternal.registerPackageLockedStateListener(mPackageLockedStateListener);

        if (isInterceptMode()) {
            // In intercept mode, show the BiometricPrompt immediately.
            showBiometricPrompt();
        } else {
            // In locked task mode, disable the back button to prevent bypassing the
            // BiometricPrompt. The BiometricPrompt will appear when the activity gains focus in
            // onWindowFocusChanged(boolean).
            mInjector.getOnBackInvokedDispatcher(this).registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, mOnBackInvokedCallback);
        }
    }

    /**
     * Initializes member variables by parsing the launching intent. This method should be called at
     * the beginning of {@link #onCreate(Bundle)}.
     */
    private void initStateFromIntent() {
        final Intent intent = getIntent();
        mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        mUserId = intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.USER_NULL);
        mTarget = mInjector.getIntentSender(intent);

        if (DEBUG) {
            Slog.d(TAG, "In " + (isInterceptMode() ? "intercept" : "locked task") + " mode");
        }
    }

    /**
     * Determines and applies the appropriate theme for the activity based on its mode. This must be
     * called before {@code super.onCreate()}.
     * <ul>
     *     <li>In intercept mode, a transparent panel theme is used to overlay the locked app.</li>
     *     <li>In locked task mode, a standard theme with no action bar is used.</li>
     * </ul>
     */
    private void applyTheme() {
        mInjector.setTheme(this, isInterceptMode() ? android.R.style.Theme_DeviceDefault_Panel
                : android.R.style.Theme_DeviceDefault_NoActionBar);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The UI must be re-initialized on configuration changes, such as when moving to a
        // different display.
        setupUi();
    }

    /**
     * Configures the user interface based on the current mode (intercept or locked task). This must
     * be called after {@code super.onCreate()} and in {@code super.onConfigurationChanged()}.
     */
    private void setupUi() {
        if (isInterceptMode()) {
            // In intercept mode, ensure the activity is translucent.
            mInjector.setTranslucent(this, true);
        } else {
            // In locked task mode, show a default UI.
            mInjector.setContentView(this, R.layout.locked_app_activity_layout);

            // Setup the external display message.
            final int displayId = mInjector.getDisplayId(this);
            // An external display is any valid display that is not the primary one.
            final boolean isDisplayedOnExternalDisplay = displayId != Display.INVALID_DISPLAY
                    && displayId != Display.DEFAULT_DISPLAY;
            final TextView externalDisplayMessage = findViewById(
                    R.id.locked_app_activity_external_display_message_id);
            if (externalDisplayMessage != null) {
                externalDisplayMessage.setVisibility(isDisplayedOnExternalDisplay
                        ? View.VISIBLE : View.GONE);
            }
        }
    }

    private boolean isInterceptMode() {
        return mTarget != null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // The BiometricPrompt should only be shown if the package is locked and the activity has
        // window focus.
        if (mPackageName == null) {
            Slog.w(TAG, "Package name is null in onWindowFocusChanged, finishing");
            finish();
            return;
        }

        if (finishIfUnlocked(mPackageName, mUserId)) {
            return;
        }

        // In locked task mode, show the prompt immediately upon gaining focus.
        if (hasFocus && !mIsBiometricPromptShowing && !isInterceptMode()) {
            showBiometricPrompt();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up listeners to prevent memory leaks.
        if (mPackageLockedStateListener != null) {
            mAppLockInternal.unregisterPackageLockedStateListener(mPackageLockedStateListener);
        }
        // In locked task mode, unregister the back button listener.
        if (!isInterceptMode()) {
            mInjector.getOnBackInvokedDispatcher(this).unregisterOnBackInvokedCallback(
                    mOnBackInvokedCallback);
        }
    }

    /**
     * Checks if the package is currently locked. If it is not locked or if an error occurs, this
     * method will finish the activity.
     *
     * @return {@code true} if the activity is finishing, {@code false} otherwise.
     */
    private boolean finishIfUnlocked(String packageName, int userId) {
        if (mIsPackageUnlocked || !mAppLockInternal.isPackageLocked(packageName, userId)) {
            if (DEBUG) {
                Slog.d(TAG, "Package is unlocked, finishing");
            }
            mIsPackageUnlocked = true;
            finish();
            return true;
        }

        return false;
    }

    /**
     * Builds and displays the {@link BiometricPrompt} to the user.
     *
     * <p>The {@link BiometricPrompt} is configured with the application's label and icon.
     */
    private void showBiometricPrompt() {
        // TODO(b/459376236): Handle coexistence with Identity Check.
        final BiometricPrompt.Builder biometricPromptBuilder = mInjector.getBiometricPromptBuilder(
                        this)
                .setTitle(getString(R.string.biometric_dialog_default_title))
                .setDescription(getString(R.string.locked_app_biometric_prompt_description,
                        mPackageLabel))
                .setLogoDescription(mPackageLabel.toString())
                .setAllowedAuthenticators(
                        Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL);
        if (mPackageLogo != null) {
            biometricPromptBuilder.setLogoBitmap(mPackageLogo);
        }
        final BiometricPrompt biometricPrompt = biometricPromptBuilder.build();
        mCancellationSignal = new CancellationSignal();

        biometricPrompt.authenticate(mCancellationSignal, getMainExecutor(),
                mAuthenticationCallback);
        mIsBiometricPromptShowing = true;
    }

    /**
     * Retrieves the user-facing label for the given package.
     *
     * @return the application label, or {@code null} if the package is not found.
     */
    private CharSequence getPackageLabel(String packageName) {
        try {
            final PackageManager packageManager = mInjector.getPackageManager(this);
            return packageManager.getApplicationInfo(packageName, /* flags= */ 0)
                    .loadLabel(packageManager);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Package " + packageName + " not found", e);
            return null;
        }
    }

    /**
     * Retrieves the icon for the provided application.
     *
     * @return the application icon, or {@code null} if it cannot be found.
     */
    private Drawable getPackageLogo(String packageName) {
        try {
            final PackageManager packageManager = mInjector.getPackageManager(this);
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Package " + packageName + " not found", e);
            return null;
        }
    }

    /**
     * A {@link PackageLockedStateListener} that finishes the {@link LockedAppActivity} if the
     * target package becomes unlocked while the activity is active.
     */
    private static class AppLockLockedStateListener implements PackageLockedStateListener {
        private final LockedAppActivity mActivity;
        private final String mPackageName;
        private final int mUserId;
        private final IntentSender mTarget;

        AppLockLockedStateListener(LockedAppActivity activity, String packageName, int userId,
                IntentSender target) {
            mActivity = activity;
            mPackageName = packageName;
            mUserId = userId;
            mTarget = target;
        }

        @Override
        public void onPackageLockedStateChanged(@NonNull String packageName, int userId,
                boolean locked) {
            if (!locked && mPackageName.equals(packageName) && mUserId == userId) {
                if (DEBUG) {
                    Slog.d(TAG, "onPackageLockedStateChanged: package was unlocked, so finishing");
                }
                if (mActivity.mCancellationSignal != null) {
                    mActivity.mCancellationSignal.cancel();
                }
                // In intercept mode, send the original target intent before finishing.
                if (mActivity.isInterceptMode()) {
                    mActivity.mInjector.sendTargetIntent(mActivity, mTarget);
                }
                mActivity.finish();
            }
        }
    }

    /**
     * Converts a {@link Drawable} to a {@link Bitmap}.
     *
     * <p>This method handles both {@link BitmapDrawable} and other {@link Drawable} types. If the
     * provided {@link Drawable} is null, this method returns null.
     *
     * <p>Copied from {@link BiometricPrompt#convertDrawableToBitmap(Drawable)}.
     *
     * @param drawable the {@link Drawable} to convert.
     * @return the converted {@link Bitmap}, or {@code null} if the input was null.
     */
    @VisibleForTesting
    public static Bitmap convertDrawableToBitmap(@Nullable Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * An injector class for dependency injection, allowing for easier testing of LockedAppActivity.
     */
    @VisibleForTesting
    public static class Injector {
        /**
         * Default constructor for the injector.
         */
        @VisibleForTesting
        public Injector() {
            // For testing only.
        }

        /**
         * Sets the theme for the given activity.
         *
         * @param activity the activity to set the theme for.
         * @param resId    the resource ID of the theme to set.
         */
        public void setTheme(Activity activity, int resId) {
            activity.setTheme(resId);
        }

        /**
         * Sets the translucent property for the given activity.
         *
         * @param activity    the activity to set the translucent property for.
         * @param translucent whether the activity should be translucent.
         */
        public void setTranslucent(Activity activity, boolean translucent) {
            activity.setTranslucent(translucent);
        }

        /**
         * Sets the content view for the given activity.
         *
         * @param activity    the activity to set the content view for.
         * @param layoutResId the resource ID of the layout to set.
         */
        public void setContentView(Activity activity, int layoutResId) {
            activity.setContentView(layoutResId);
        }

        /**
         * Returns the ID of the {@link Display} for the given activity.
         *
         * @param activity the activity to get the display from.
         * @return the display ID, or {@link Display#INVALID_DISPLAY} if there is no display.
         */
        public int getDisplayId(Activity activity) {
            final Display display = activity.getDisplay();
            return display != null ? display.getDisplayId() : Display.INVALID_DISPLAY;
        }

        /**
         * Returns the {@link PackageManager} for the given activity.
         *
         * @param activity the activity to get the package manager from.
         * @return the package manager.
         */
        public PackageManager getPackageManager(Activity activity) {
            return activity.getPackageManager();
        }

        /**
         * Returns the {@link OnBackInvokedDispatcher} for the given activity.
         *
         * @param activity the activity to get the dispatcher from.
         * @return the on-back-invoked dispatcher.
         */
        public OnBackInvokedDispatcher getOnBackInvokedDispatcher(Activity activity) {
            return activity.getOnBackInvokedDispatcher();
        }

        /**
         * Returns a new {@link BiometricPrompt.Builder} for the given activity.
         *
         * @param activity the activity to create the builder for.
         * @return a new biometric prompt builder.
         */
        public BiometricPrompt.Builder getBiometricPromptBuilder(Activity activity) {
            return new BiometricPrompt.Builder(activity);
        }

        /**
         * Retrieves the {@link IntentSender} from the given intent.
         *
         * @param intent the intent to extract the sender from.
         * @return the intent sender, or {@code null} if not present.
         */
        public IntentSender getIntentSender(Intent intent) {
            return intent.getParcelableExtra(Intent.EXTRA_INTENT, IntentSender.class);
        }

        /**
         * Sends the target intent after successful authentication.
         *
         * @param activity the activity from which to send the intent.
         * @param target   the {@link IntentSender} to trigger.
         */
        public void sendTargetIntent(Activity activity, @NonNull IntentSender target) {
            Objects.requireNonNull(target);
            try {
                target.sendIntent(activity, Activity.RESULT_OK, null, null, null);
            } catch (IntentSender.SendIntentException e) {
                Slog.w(TAG, "Unable to send intent", e);
            }
        }
    }
}
