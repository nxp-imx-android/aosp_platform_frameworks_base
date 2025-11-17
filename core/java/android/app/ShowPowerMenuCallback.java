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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Callback for {@link StatusBarManager#showPowerMenu}.
 */
@FlaggedApi(Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
public interface ShowPowerMenuCallback {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_UNKNOWN,
            ERROR_TIMEOUT,
    })
    @interface ShowPowerMenuError {}

    /**
     * An unknown error occurred when trying to show the Power Menu. This usually indicates
     * an error with the {@code statusbar} service. Retrying in the future will probably not
     * succeed.
     */
    int ERROR_UNKNOWN = 0;

    /**
     * The request to show the Power Menu timed out, before the Power Menu showed. The Power Menu
     * may show as a result of this call, but the callback will not be notified.
     */
    int ERROR_TIMEOUT = 1;

    /**
     * This method will be called if there are no errors with the request.
     * <p>
     * If {@code showing} is {@code false}, it indicates that the Power Menu is currently disabled
     * and will not be shown. Retrying in the future may succeed.
     * <p>
     * If the Power Menu shows before a time out, this method will be called
     * with {@code true}.
     * @param showing {@code true} if and when the Power Menu is showing, {@code false} if it cannot
     *                be shown
     */
    void onPowerMenuShown(boolean showing);

    /**
     * Called if there is an error when showing the Power Menu.
     */
    void onError(@ShowPowerMenuError int error);
}
