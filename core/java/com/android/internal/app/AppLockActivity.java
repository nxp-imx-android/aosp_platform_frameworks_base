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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Activity that allows the user to enable or disable App Lock for an app.
 *
 * <p>Note: This activity runs in a separate process. To accommodate testing with
 * {@link androidx.test.core.app.ActivityScenario}, which does not support testing activities in a
 * separate process, this class is not declared {@code final}. This allows
 * {@link AppLockActivityTest} to use a test-specific subclass.
 */
// TODO(b/436380342): Finish AppLockActivity UI.
public class AppLockActivity extends AlertActivity {

    private static final String TAG = "AppLockActivity";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.DEBUG);

    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final int APP_LOCK_STATE_CHANGE_RESULT_TOAST_DURATION = Toast.LENGTH_SHORT;

    /**
     * Creates an {@link Intent} to launch {@link AppLockActivity} to enable or disable App Lock on
     * the provided package.
     */
    public static Intent createAppLockActivityIntent(@NonNull String packageName,
            boolean newAppLockState) {
        Objects.requireNonNull(packageName);

        return new Intent(PackageManager.ACTION_SET_APP_LOCK)
                .setClassName(SYSTEM_PACKAGE_NAME, AppLockActivity.class.getName())
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, newAppLockState)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Precondition: Check App Lock feature flags.
        if (!android.security.Flags.appLockApis() || !android.security.Flags.appLockCore()) {
            Slog.w(TAG, "App Lock implementation is not enabled, finishing");
            finish();
            return;
        }

        // Check for valid Intent.
        final Intent intent = getIntent();
        final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Slog.w(TAG, "Package name is null, finishing");
            finish();
            return;
        }
        if (!intent.hasExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE)) {
            Slog.w(TAG, "No new App Lock state, finishing");
            finish();
            return;
        }
        final boolean newAppLockEnabled = intent.getBooleanExtra(
                PackageManager.EXTRA_APP_LOCK_NEW_STATE, false);

        // Retrieve the provided package's ApplicationInfo with App Lock information.
        final PackageManager packageManager = getPackageManager();
        final ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName,
                    ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO));
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Package " + packageName + " not found", e);
            finish();
            return;
        }

        // Check that the provided package supports App Lock.
        if (!applicationInfo.isAppLockSupported) {
            Slog.w(TAG, packageName + " does not support App Lock, finishing");
            finish();
            return;
        }

        // Check that the new App Lock state is different from the current state.
        if (applicationInfo.isAppLockEnabled == newAppLockEnabled) {
            Slog.w(TAG, "The App Lock state for " + packageName + " is already "
                    + newAppLockEnabled + ", finishing");
            finish();
            return;
        }

        // Retrieve the package label.
        final CharSequence packageLabel = applicationInfo.loadLabel(packageManager);

        // Set up the dialog.
        final int titleResId = newAppLockEnabled ? R.string.enable_app_lock_dialog_title
                : R.string.disable_app_lock_dialog_title;
        mAlertParams.mTitle = getString(titleResId, packageLabel);
        mAlertParams.mPositiveButtonText = getString(newAppLockEnabled
                ? R.string.enable_app_lock_dialog_enable_button_text
                : R.string.disable_app_lock_dialog_disable_button_text);
        mAlertParams.mNegativeButtonText = getString(android.R.string.cancel);

        final OnDialogClickListener onDialogClickListener = new OnDialogClickListener(this,
                packageName, newAppLockEnabled, packageLabel);
        mAlertParams.mPositiveButtonListener = onDialogClickListener;
        mAlertParams.mNegativeButtonListener = onDialogClickListener;

        setupAlert();
    }

    @VisibleForTesting
    protected void showToast(CharSequence toastText, int duration) {
        Toast.makeText(this, toastText, duration).show();
    }

    private static class OnDialogClickListener implements DialogInterface.OnClickListener {
        private final AppLockActivity mAppLockActivity;
        private final String mPackageName;
        private final boolean mNewAppLockEnabled;
        private final CharSequence mPackageLabel;

        OnDialogClickListener(AppLockActivity appLockActivity, String packageName,
                boolean newAppLockEnabled, CharSequence packageLabel) {
            mAppLockActivity = appLockActivity;
            mPackageName = packageName;
            mNewAppLockEnabled = newAppLockEnabled;
            mPackageLabel = packageLabel;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                final boolean success = mAppLockActivity.getPackageManager()
                        .setPackageAppLockEnabled(mPackageName, mNewAppLockEnabled);
                if (DEBUG) {
                    Slog.d(TAG, "Setting App Lock to state=" + mNewAppLockEnabled
                            + (success ? " succeeded" : " failed"));
                }

                showResultToast(success);
            }

            mAppLockActivity.finish();
        }

        /** Informs the user via a toast whether the App Lock state was changed successfully. */
        private void showResultToast(boolean success) {
            // A failed disablement of App Lock is not reported, as this state is considered
            // impossible and indicates an implementation error.
            if (!mNewAppLockEnabled && !success) {
                Slog.w(TAG, "Failed to disable App Lock for " + mPackageName
                        + ", which should not be possible.");
                return;
            }
            final int toastTextResId;
            if (mNewAppLockEnabled) {
                toastTextResId = success ? R.string.enable_app_lock_success_toast_message
                        : R.string.enable_app_lock_failure_toast_message;
            } else {
                toastTextResId = R.string.disable_app_lock_success_toast_message;
            }
            final CharSequence toastText = mAppLockActivity.getString(toastTextResId,
                    mPackageLabel);
            mAppLockActivity.showToast(toastText, APP_LOCK_STATE_CHANGE_RESULT_TOAST_DURATION);
        }
    }
}
