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

package androidx.window.extensions.embedding;

import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;
import static android.content.res.Configuration.TOUCHSCREEN_FINGER;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.WindowManager.PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.AppGlobals;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.TypedValue;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

import java.util.Set;

public class AppCompatEmbeddingRuleController {
    private static final int DEFAULT_MIN_DP = 600;

    private static boolean sIsVirtualGamepadEnabled;

    private static boolean sIsVirtualGamepadAllowedByApp;

    /** Initializes the controller. Must be called before any other functions. */
    public static void init(@NonNull Context context) {
        sIsVirtualGamepadAllowedByApp = isVirtualGamepadAllowedByApp(context);
        sIsVirtualGamepadEnabled =
                isVirtualGamepadEnabled(context.getPackageName(), context.getUserId());
    }

    /** Loads {@link EmbeddingRule}s if any app compat override rules apply to the app. */
    @NonNull
    public static Set<EmbeddingRule> loadAppCompatRules(@NonNull Context context) {
        final Set<EmbeddingRule> rules = new ArraySet<>();

        if (!isOverrideAllowed(context)) {
            return rules;
        }

        if (sIsVirtualGamepadEnabled) {
            final EmbeddingRule rule = createVirtualGamepadOverrideRule(
                    context.getResources().getString(R.string.config_virtual_gamepad_package_name),
                    context.getResources().getString(R.string.config_virtual_gamepad_activity_name),
                    defaultMinSize(context),
                    context.getPackageName(),
                    context.getUserId());
            if (rule != null) {
                rules.add(rule);
            }
        }

        return rules;
    }

    /** Loads the virtual gamepad override embedding rule. */
    @VisibleForTesting
    @Nullable
    public static EmbeddingRule createVirtualGamepadOverrideRule(
            @NonNull String packageName, @NonNull String activityName, int minSizePx,
            @NonNull String selfPackageName, int userId) {
        if (packageName.isEmpty() || activityName.isEmpty()) {
            return null;
        }

        final Intent placeholderIntent = new Intent();
        placeholderIntent.setClassName(packageName, activityName);
        placeholderIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, selfPackageName);

        // TODO(b/454729069) confirm the desired split ratio and whether to use hinge split
        final SplitAttributes defaultAttributes = new SplitAttributes.Builder()
                .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .build();

        return new SplitPlaceholderRule.Builder(
                placeholderIntent,
                (androidx.window.extensions.core.util.function.Predicate<Activity>)
                        activity -> activity.getResources().getConfiguration().touchscreen
                                == TOUCHSCREEN_FINGER
                                && isVirtualGamepadEnabled(selfPackageName, userId),
                intent -> true,
                parentMetrics -> parentMetrics.getBounds().height() >= minSizePx
                        && parentMetrics.getBounds().width() >= minSizePx)
                .setFinishPrimaryWithPlaceholder(SplitRule.FINISH_NEVER)
                .setDefaultSplitAttributes(defaultAttributes)
                .build();
    }

    /**
     * Updates the placeholder container upon creation.
     *
     * For gamepad overrides, this pins the placeholder container to ensure isolated navigation and
     * always-on-top behavior.
     */
    public static void updatePlaceholderContainer(
            @NonNull SplitPresenter presenter,
            @NonNull TaskFragmentContainer container,
            @NonNull WindowContainerTransaction wct) {
        if (sIsVirtualGamepadEnabled) {
            // Ensure isolated navigation and always-on-top behavior of the gamepad placeholder.
            presenter.setTaskFragmentPinned(wct, container, true /* pinned */);
        }
    }

    private static int defaultMinSize(Context context) {
        // Convert the dps to pixels, based on density scale
        return (int) TypedValue.applyDimension(
                COMPLEX_UNIT_DIP,
                DEFAULT_MIN_DP,
                context.getResources().getDisplayMetrics());
    }

    private static boolean isOverrideAllowed(@NonNull Context context) {
        try {
            // If the app uses activity embedding by itself, we should not apply any override rules
            // to avoid conflicts.
            final PackageManager.Property property = context.getPackageManager().getProperty(
                    PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED,
                    context.getPackageName());
            return !property.getBoolean();
        } catch (PackageManager.NameNotFoundException e) {
            // If not defined, the app is not using embedding.
            return true;
        }
    }

    private static boolean isVirtualGamepadEnabled(@NonNull String packageName, int userId) {
        if (!sIsVirtualGamepadAllowedByApp) {
            return false;
        }
        int userOption = getVirtualGamepadUserOption(packageName, userId);
        if (userOption == PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_OPT_OUT) {
            return false;
        }
        return CompatChanges.isChangeEnabled(OVERRIDE_ENABLE_VIRTUAL_GAMEPAD);
    }

    private static boolean isVirtualGamepadAllowedByApp(@NonNull Context context) {
        try {
            final PackageManager.Property property = context.getPackageManager().getProperty(
                    PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE,
                    context.getPackageName());
            return property.getBoolean();
        } catch (PackageManager.NameNotFoundException e) {
            // Default to true if the property is not specified.
            return true;
        }
    }

    @PackageManager.VirtualGamepadUserOption
    private static int getVirtualGamepadUserOption(@NonNull String packageName, int userId) {
        final IPackageManager pm = AppGlobals.getPackageManager();
        try {
            return pm.getVirtualGamepadUserOption(packageName, userId);
        } catch (RemoteException e) {
            return PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_UNSET;
        }
    }
}
