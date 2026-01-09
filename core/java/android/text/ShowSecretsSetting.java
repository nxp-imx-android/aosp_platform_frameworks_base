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
package android.text;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings.Secure;

import com.android.text.flags.Flags;

import java.util.Objects;

/**
 * This is the API surface for interacting with the settings that determine whether characters in
 * password inputs or other secret fields are echoed briefly or hidden immediately.
 */
@FlaggedApi(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
@SuppressLint("PackageLayering")
public class ShowSecretsSetting {
    private static final int SHOW = 1;
    private static final int HIDE = 0;

    private ShowSecretsSetting() {}

    /**
     * If enabled system services and SDKs will use the new split settings instead of {@link
     * android.provider.Settings.System.TEXT_SHOW_PASSWORD}.
     *
     * @hide
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
    @TestApi
    public static final long SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL = 417951523;

    /**
     * Returns {@code true} when characters entered into a password, pin or other secret field from
     * touch/virtual input sources should either be shown or echoed briefly.
     */
    public static boolean shouldShowTouchInputForUser(
            @NonNull ContentResolver resolver, @NonNull UserHandle user) {
        return Secure.getIntForUser(
                        Objects.requireNonNull(resolver),
                        Secure.TEXT_SHOW_PASSWORD_TOUCH,
                        SHOW,
                        user.getIdentifier())
                == SHOW;
    }

    /**
     * Returns {@code true} when characters entered into a password, pin or other secret field from
     * hardware/physical input sources should either be shown or echoed briefly.
     */
    public static boolean shouldShowPhysicalInputForUser(
            @NonNull ContentResolver resolver, @NonNull UserHandle user) {
        return Secure.getIntForUser(
                        Objects.requireNonNull(resolver),
                        Secure.TEXT_SHOW_PASSWORD_PHYSICAL,
                        HIDE,
                        user.getIdentifier())
                == SHOW;
    }

    /**
     * Set the underlying setting to either show/echo or hide characters from touch/virtual input in
     * password-like input fields immediately.
     *
     * @param resolver The ContentResolver to access.
     * @param shouldShow Set to {@code true} if characters should be shown/echoed briefly.
     * @param user The id of the user you want to set the setting for.
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    @SystemApi
    public static void setShouldShowTouchInputForUser(
            @NonNull ContentResolver resolver, boolean shouldShow, @NonNull UserHandle user) {
        setSettingValue(
                Objects.requireNonNull(resolver),
                Secure.TEXT_SHOW_PASSWORD_TOUCH,
                shouldShow,
                user);
    }

    /**
     * Set the underlying setting to either show/echo or hide characters from hardware/physical
     * inputs in password-like input fields immediately.
     *
     * @param resolver The ContentResolver to access.
     * @param shouldShow Set to {@code true} if characters should be shown/echoed briefly.
     * @param user The id of the user you want to set the setting for.
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    @SystemApi
    public static void setShouldShowPhysicalInputForUser(
            @NonNull ContentResolver resolver, boolean shouldShow, @NonNull UserHandle user) {
        setSettingValue(
                Objects.requireNonNull(resolver),
                Secure.TEXT_SHOW_PASSWORD_PHYSICAL,
                shouldShow,
                user);
    }

    /**
     * Get the {@link android.net.Uri} of the setting for touch/virtual inputs. This is useful if
     * you want to listen to setting changes with a {@link android.database.ContentObserver}.
     */
    public static @NonNull Uri getTouchUri() {
        return Secure.getUriFor(Secure.TEXT_SHOW_PASSWORD_TOUCH);
    }

    /**
     * Get the {@link android.net.Uri} of the setting for hardware/physical inputs. This is useful
     * if you want to listen to setting changes with a {@link android.database.ContentObserver}.
     */
    public static @NonNull Uri getPhysicalUri() {
        return Secure.getUriFor(Secure.TEXT_SHOW_PASSWORD_PHYSICAL);
    }

    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    private static void setSettingValue(
            ContentResolver resolver, String key, boolean newValue, @NonNull UserHandle user) {
        Secure.putIntForUser(resolver, key, newValue ? SHOW : HIDE, user.getIdentifier());
    }
}
