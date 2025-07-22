/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.view.inputmethod.ImeTracker.DEBUG_IME_VISIBILITY;

import static com.android.server.EventLogTags.IMF_HIDE_IME;
import static com.android.server.EventLogTags.IMF_SHOW_IME;
import static com.android.server.inputmethod.ImeProtoLogGroup.IME_VISIBILITY_APPLIER_DEBUG;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.IBinder;
import android.util.EventLog;
import android.view.inputmethod.ImeTracker;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.InputMethodDebug;
import com.android.internal.inputmethod.SoftInputShowHideReason;
import com.android.internal.protolog.ProtoLog;
import com.android.server.LocalServices;
import com.android.server.wm.ImeTargetVisibilityPolicy;

import java.util.Objects;

/**
 * A stateless helper class for IME visibility operations like show/hide and update Z-ordering
 * relative to the IME targeted window.
 */
final class DefaultImeVisibilityApplier {

    static final String TAG = "DefaultImeVisibilityApplier";

    private InputMethodManagerService mService;

    @NonNull
    private final ImeTargetVisibilityPolicy mImeTargetVisibilityPolicy;

    DefaultImeVisibilityApplier(InputMethodManagerService service) {
        mService = service;
        mImeTargetVisibilityPolicy = LocalServices.getService(ImeTargetVisibilityPolicy.class);
    }

    /**
     * Performs showing IME on top of the given window.
     *
     * @param showInputToken a token that represents the requester to show IME
     * @param statsToken     the token tracking the current IME request
     * @param reason         the reason for requesting to show IME
     * @param userId         the target user when performing show IME
     */
    @GuardedBy("ImfLock.class")
    void performShowIme(IBinder showInputToken, @NonNull ImeTracker.Token statsToken,
            @SoftInputShowHideReason int reason, @UserIdInt int userId) {
        final var userData = mService.getUserData(userId);
        final var bindingController = userData.mBindingController;
        final IInputMethodInvoker curMethod = bindingController.getCurMethod();
        if (curMethod != null) {
            ProtoLog.v(IME_VISIBILITY_APPLIER_DEBUG,
                    "Calling %s.showSoftInput(%s) for reason: %s", curMethod,
                    showInputToken, InputMethodDebug.softInputDisplayReasonToString(reason));
            // TODO(b/192412909): Check if we can always call onShowHideSoftInputRequested() or not.
            if (curMethod.showSoftInput(statsToken)) {
                if (DEBUG_IME_VISIBILITY) {
                    EventLog.writeEvent(IMF_SHOW_IME,
                            statsToken != null ? statsToken.getTag() : ImeTracker.TOKEN_NONE,
                            Objects.toString(userData.mImeBindingState.mFocusedWindow),
                            InputMethodDebug.softInputDisplayReasonToString(reason),
                            InputMethodDebug.softInputModeToString(
                                    userData.mImeBindingState.mFocusedWindowSoftInputMode));
                }
                // TODO(b/419459695): Check if we still need to pass the input token
                mService.onShowHideSoftInputRequested(true /* show */, showInputToken, reason,
                        statsToken, userId);
            }
        }
    }

    /**
     * Performs hiding IME to the given window
     *
     * @param hideInputToken a token that represents the requester to hide IME
     * @param statsToken     the token tracking the current IME request
     * @param reason         the reason for requesting to hide IME
     * @param userId         the target user when performing hide IME
     */
    @GuardedBy("ImfLock.class")
    void performHideIme(IBinder hideInputToken, @NonNull ImeTracker.Token statsToken,
            @SoftInputShowHideReason int reason, @UserIdInt int userId) {
        final var userData = mService.getUserData(userId);
        final var bindingController = userData.mBindingController;
        final IInputMethodInvoker curMethod = bindingController.getCurMethod();
        if (curMethod != null) {
            // The IME will report its visible state again after the following message finally
            // delivered to the IME process as an IPC.  Hence the inconsistency between
            // IMMS#mInputShown and IMMS#mImeWindowVis should be resolved spontaneously in
            // the final state.
            ProtoLog.v(IME_VISIBILITY_APPLIER_DEBUG,
                    "Calling %s.hideSoftInput(%s) for reason: %s", curMethod, hideInputToken,
                    InputMethodDebug.softInputDisplayReasonToString(reason));
            // TODO(b/192412909): Check if we can always call onShowHideSoftInputRequested() or not.
            if (curMethod.hideSoftInput(statsToken)) {
                if (DEBUG_IME_VISIBILITY) {
                    EventLog.writeEvent(IMF_HIDE_IME,
                            statsToken != null ? statsToken.getTag() : ImeTracker.TOKEN_NONE,
                            Objects.toString(userData.mImeBindingState.mFocusedWindow),
                            InputMethodDebug.softInputDisplayReasonToString(reason),
                            InputMethodDebug.softInputModeToString(
                                    userData.mImeBindingState.mFocusedWindowSoftInputMode));
                }
                // TODO(b/419459695): Check if we still need to pass the input token
                mService.onShowHideSoftInputRequested(false /* show */, hideInputToken, reason,
                        statsToken, userId);
            }
        }
    }

    /**
     * Applies the IME screenshot visibility on the given IME target window.
     *
     * @param imeTarget the token of the IME target window.
     * @param show      whether to show or remove the screenshot.
     * @param userId    the ID of the user to apply the screenshot visibility for.
     */
    @GuardedBy("ImfLock.class")
    void applyImeScreenshotVisibility(IBinder imeTarget, boolean show, @UserIdInt int userId) {
        final var userData = mService.getUserData(userId);
        final var bindingController = userData.mBindingController;
        final int displayId = bindingController.getDisplayIdToShowIme();
        if (show) {
            showImeScreenshot(imeTarget, displayId, userId);
        } else {
            removeImeScreenshot(imeTarget, displayId, userId);
        }
    }

    /**
     * Shows the IME screenshot and attaches it to the given IME target window.
     *
     * @param imeTarget the token of the IME target window.
     * @param displayId the ID of the display to show the screenshot on.
     * @param userId    the ID of the user to show the screenshot for.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    @VisibleForTesting
    @GuardedBy("ImfLock.class")
    boolean showImeScreenshot(IBinder imeTarget, int displayId, @UserIdInt int userId) {
        if (mImeTargetVisibilityPolicy.showImeScreenshot(imeTarget, displayId)) {
            mService.onShowHideSoftInputRequested(false /* show */, imeTarget,
                    SoftInputShowHideReason.SHOW_IME_SCREENSHOT_FROM_IMMS, null /* statsToken */,
                    userId);
            return true;
        }
        return false;
    }

    /**
     * Removes the IME screenshot from the given display.
     *
     * @param imeTarget the token of the IME target window.
     * @param displayId the ID of the display to remove the screenshot from.
     * @param userId    the ID of the user to remove the screenshot for.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    @VisibleForTesting
    @GuardedBy("ImfLock.class")
    boolean removeImeScreenshot(IBinder imeTarget, int displayId, @UserIdInt int userId) {
        if (mImeTargetVisibilityPolicy.removeImeScreenshot(displayId)) {
            mService.onShowHideSoftInputRequested(false /* show */, imeTarget,
                    SoftInputShowHideReason.REMOVE_IME_SCREENSHOT_FROM_IMMS, null /* statsToken */,
                    userId);
            return true;
        }
        return false;
    }
}
