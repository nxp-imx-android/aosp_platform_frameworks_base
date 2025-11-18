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
package com.android.systemui.inputmethod

import android.Manifest
import android.annotation.BinderThread
import android.annotation.IntRange
import android.annotation.RequiresPermission
import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.RemoteException
import android.util.Log
import android.util.Slog
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.view.inputmethod.Flags
import android.view.inputmethod.InputMethodManager
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.internal.inputmethod.IImeSwitcherMenuListener
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

/** Controller for showing and hiding the Input Method Switcher Menu. */
@SysUISingleton
class ImeSwitcherMenuController
@Inject
constructor(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val inputMethodManager: InputMethodManager,
    @param:Main private val mainExecutor: Executor,
) : CoreStartable {

    private val impl = IImeSwitcherMenuImpl()

    private val mDialogListener = DialogListener()

    /** The interface to receive callbacks from this controller. */
    private var listener: IImeSwitcherMenuListener? = null

    private var dialogWindowContext: Context? = null

    private var dialog: ImeSwitcherMenuDialog? = null

    @RequiresPermission(
        allOf =
            [
                Manifest.permission.WRITE_SECURE_SETTINGS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.STATUS_BAR_SERVICE,
            ]
    )
    override fun start() {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        inputMethodManager.registerImeSwitcherMenu(impl)
    }

    /**
     * Shows the Input Method Switcher Menu, with a list of IMEs and their subtypes.
     *
     * @param items the list of input method and subtype items.
     * @param selectedImeId the ID of the selected input method.
     * @param selectedSubtypeIndex the index of the selected subtype in the input method's array of
     *   subtypes, or [NOT_A_SUBTYPE_INDEX] if no subtype is selected.
     * @param selectedImeSettingsIntent the intent for the settings activity of the selected IME, or
     *   `null` if no IME is selected, or the selected IME does not have a settings activity.
     * @param isScreenLocked whether the screen is current locked.
     * @param displayId the ID of the display where the menu was requested.
     * @param userId the ID of the user that requested the menu.
     */
    private fun show(
        items: List<IImeSwitcherMenu.Item>,
        selectedImeId: String?,
        selectedSubtypeIndex: Int,
        selectedImeSettingsIntent: Intent?,
        isScreenLocked: Boolean,
        displayId: Int,
        @UserIdInt userId: Int,
    ) {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        if (dialog == null) {
            dialog = ImeSwitcherMenuDialog(getContext(displayId), mDialogListener)
        }
        dialog!!.show(
            items,
            selectedImeId,
            selectedSubtypeIndex,
            selectedImeSettingsIntent,
            isScreenLocked,
            displayId,
            userId,
        )
    }

    /**
     * Hides the Input Method Switcher Menu.
     *
     * @param displayId the ID of the display from where the menu should be hidden.
     * @param userId the ID of the user for which the menu should be hidden.
     */
    private fun hide(displayId: Int, @UserIdInt userId: Int) {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        if (DEBUG) Slog.v(TAG, "Hide IME switcher menu.")

        dialog?.let {
            it.hide(displayId, userId)
            dialog = null
        }
    }

    /**
     * Registers an interface to receive callbacks from this controller.
     *
     * @param listener the listener to receive callbacks on.
     */
    private fun registerListener(listener: IImeSwitcherMenuListener) {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        this.listener = listener
    }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println()
        pw.println(TAG)
        dialog?.dump(pw, "  ")
    }

    /**
     * Returns the window context for IME switch dialogs to receive configuration changes.
     *
     * This method initializes the window context if it was not initialized, or moves the context to
     * the targeted display if the current display of context is different from the display
     * specified by `displayId`.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    private fun getContext(displayId: Int): Context {
        val curContext = dialogWindowContext
        if (curContext?.displayId == displayId) {
            return curContext
        }
        val display = displayManager.getDisplay(displayId)
        val windowContext =
            context.createWindowContext(
                display,
                WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG,
                null, /* options */
            )
        val newContext = ContextThemeWrapper(windowContext, R.style.Theme_DeviceDefault_Settings)
        dialogWindowContext = newContext
        return newContext
    }

    private inner class DialogListener : ImeSwitcherMenuDialog.ImeSwitcherMenuDialogListener {

        override fun onVisibilityChanged(visible: Boolean, displayId: Int, @UserIdInt userId: Int) {
            try {
                listener?.onVisibilityChanged(visible, displayId, userId)
            } catch (e: RemoteException) {
                Log.e(
                    TAG,
                    "Failed to notify listener of new visibility: $visible for user: $userId" +
                        " and display: $displayId",
                    e,
                )
            }
        }

        override fun onImeAndSubtypeSelected(
            imeId: String,
            @IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) subtypeIndex: Int,
            @UserIdInt userId: Int,
        ) {
            try {
                listener?.onImeAndSubtypeSelected(imeId, subtypeIndex, userId)
            } catch (e: RemoteException) {
                Log.e(
                    TAG,
                    "Failed to notify listener of new selected IME: $imeId and subtype" +
                        " index: $subtypeIndex for user: $userId",
                    e,
                )
            }
        }
    }

    /** The interface for IPC calls outside of SystemUI. */
    @BinderThread
    private inner class IImeSwitcherMenuImpl : IImeSwitcherMenu.Stub() {

        override fun show(
            items: List<IImeSwitcherMenu.Item>,
            selectedImeId: String?,
            selectedSubtypeIndex: Int,
            selectedImeSettingsIntent: Intent?,
            isScreenLocked: Boolean,
            displayId: Int,
            @UserIdInt userId: Int,
        ) {
            mainExecutor.execute {
                this@ImeSwitcherMenuController.show(
                    items,
                    selectedImeId,
                    selectedSubtypeIndex,
                    selectedImeSettingsIntent,
                    isScreenLocked,
                    displayId,
                    userId,
                )
            }
        }

        override fun hide(displayId: Int, @UserIdInt userId: Int) {
            mainExecutor.execute { this@ImeSwitcherMenuController.hide(displayId, userId) }
        }

        override fun registerListener(listener: IImeSwitcherMenuListener) {
            mainExecutor.execute { this@ImeSwitcherMenuController.registerListener(listener) }
        }
    }

    companion object {
        private const val TAG: String = "ImeSwitcherMenuCtrl"

        private const val DEBUG = false

        private const val NOT_A_SUBTYPE_INDEX = -1
    }
}
