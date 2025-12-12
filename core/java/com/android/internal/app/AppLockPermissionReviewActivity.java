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

package com.android.internal.app;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An activity that reviews app permissions for photo access during the App Lock setup.
 *
 * <p>This activity is launched by {@link AppLockActivity} using
 * {@link android.app.Activity#startActivityForResult(android.content.Intent, int)} with the
 * request code {@code REQUEST_CODE_PHOTOS_APP_PERMISSION_REVIEW_DIALOG}. It is shown when a user
 * is locking an app that can access photos, to make them aware of other applications that already
 * have permission to access media on their device.
 *
 * <p>Upon completion, this activity returns {@link android.app.Activity#RESULT_OK} if the user
 * chooses to continue with the process, or {@link android.app.Activity#RESULT_CANCELED} if they
 * cancel. This result is then handled by
 * {@link AppLockActivity#onActivityResult(int, int, Intent)}.
 */
public class AppLockPermissionReviewActivity extends Activity {

    private static final String TAG = "AppLockPermissionReviewActivity";
    private static final String[] PERMISSIONS_TO_CHECK = new String[] {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    };

    private final ExecutorService mBackgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates an {@link Intent} to launch {@link AppLockPermissionReviewActivity} to show a
     * permission review sheet that lists applications with existing photo access before the user
     * confirms enabling App Lock for the provided package.
     */
    static Intent createIntent(Context context, String photosPackageName) {
        final Intent photoAccessActivityIntent = new Intent(context,
                AppLockPermissionReviewActivity.class);
        photoAccessActivityIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, photosPackageName);
        return photoAccessActivityIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        if (savedInstanceState != null) {
            return;
        }
        setContentView(R.layout.app_lock_permission_review_sheet);

        // Set up the recycler view and its adapter.
        final AppLockMaxHeightRecyclerView recyclerView = findViewById(
                R.id.app_lock_permission_review_app_list_recycler_view);
        final AppLockPermissionReviewAdapter appLockPermissionReviewAdapter =
                new AppLockPermissionReviewAdapter();
        recyclerView.setAdapter(appLockPermissionReviewAdapter);

        findViewById(R.id.app_lock_permission_review_btn_cancel).setOnClickListener(v -> {
            onBackPressed();
        });

        findViewById(R.id.app_lock_permission_review_btn_continue).setOnClickListener(v -> {
            setResult(Activity.RESULT_OK);
            finish();
        });
        populateAppsList(appLockPermissionReviewAdapter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Finish the activity if it is being stopped for reasons other than a config change (e.g.,
        // Home button).
        if (!isChangingConfigurations()) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);

        // Use finishAffinity() to dismiss the entire AppLockActivity stack as a single unit.
        // This ensures a clean task-level exit transition and prevents the parent
        // activity from momentarily appearing (flickering) during dismissal.
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBackgroundExecutor.shutdownNow();
    }

    /** Populates the list of apps with photo/video permission. */
    protected void populateAppsList(AppLockPermissionReviewAdapter appLockPermissionReviewAdapter) {
        mBackgroundExecutor.execute(() -> {
            final List<AppWithPermissionInfo> appsWithPermissions = getAppsWithPermissions();
            runOnUiThread(() -> updateUiWithAppList(appsWithPermissions,
                    appLockPermissionReviewAdapter));
        });
    }

    /** Returns a list of user-facing apps that have photo/video access permissions. */
    protected List<AppWithPermissionInfo> getAppsWithPermissions() {
        final PackageManager packageManager = getPackageManager();
        final List<AppWithPermissionInfo> appsWithPermissions = new ArrayList<>();

        final List<PackageInfo> packages = packageManager
                .getPackagesHoldingPermissions(PERMISSIONS_TO_CHECK, /* flags= */ 0);
        for (PackageInfo packageInfo : packages) {
            if (!isUserFacingApp(packageManager, packageInfo.packageName)) {
                continue;
            }

            final ApplicationInfo appInfo = packageInfo.applicationInfo;
            if (appInfo != null) {
                final CharSequence packageLabel = packageManager.getApplicationLabel(appInfo);
                final Drawable appIcon = appInfo.loadIcon(packageManager);
                appsWithPermissions.add(new AppWithPermissionInfo(packageLabel, appIcon,
                        packageInfo.packageName));
            }
        }
        return appsWithPermissions;
    }

    /** Updates the RecyclerView with the list of apps and configures the UI. */
    protected void updateUiWithAppList(List<AppWithPermissionInfo> appsWithPermissions,
            AppLockPermissionReviewAdapter appLockPermissionReviewAdapter) {

        // If there are no apps with the photos permissions, we can just finish and return RESULT_OK
        // to continue the app lock setup.
        if (appsWithPermissions.isEmpty()) {
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }
        final AppLockMaxHeightRecyclerView recyclerView = findViewById(
                R.id.app_lock_permission_review_app_list_recycler_view);
        final View viewAllButton = findViewById(R.id.app_lock_permission_review_btn_view_all);

        // Show a collapsed list with a "View all" button if there are more than 3 apps.
        if (appsWithPermissions.size() <= 3) {
            appLockPermissionReviewAdapter.updateData(appsWithPermissions);
            viewAllButton.setVisibility(View.GONE);
            return;
        }
        appLockPermissionReviewAdapter.updateData(appsWithPermissions.subList(0, 3));
        viewAllButton.setVisibility(View.VISIBLE);
        viewAllButton.setOnClickListener(v -> {

            if (recyclerView.getParent() instanceof ViewGroup) {
                TransitionManager.beginDelayedTransition((ViewGroup) recyclerView.getParent());
            }
            appLockPermissionReviewAdapter.updateData(appsWithPermissions);
            findViewById(R.id.app_lock_permission_review_bottom_sheet_divider)
                    .setVisibility(View.VISIBLE);
            viewAllButton.setVisibility(View.GONE);
        });
    }

    /** Checks if an app is user-facing and should be shown in the list. */
    private boolean isUserFacingApp(PackageManager packageManager, String packageName) {
        final String photosPackageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (photosPackageName != null && photosPackageName.equals(packageName)) {
            return false;
        }

        // Filter out background services, widget-only apps, etc.
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);
        final List<ResolveInfo> activities = packageManager.queryIntentActivities(intent,
                /* flags= */ 0);
        return activities != null && !activities.isEmpty();
    }

    /**
     * Data class that holds information for an application that currently has permission to access
     * photos or media on the device. This information (label, icon, and package name) is used to
     * populate the list in the permission review sheet.
     */
    static class AppWithPermissionInfo {
        final CharSequence mAppName;
        final Drawable mAppIcon;
        final String mPackageName;

        AppWithPermissionInfo(CharSequence name, Drawable icon, String pkgName) {
            this.mAppName = name;
            this.mAppIcon = icon;
            this.mPackageName = pkgName;
        }
    }
}
