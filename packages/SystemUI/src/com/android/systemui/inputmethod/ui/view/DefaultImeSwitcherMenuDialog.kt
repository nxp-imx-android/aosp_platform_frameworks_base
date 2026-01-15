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

package com.android.systemui.inputmethod.ui.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.UserHandle
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.MainThread
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi
import com.android.systemui.inputmethod.ui.composable.ImeSwitcherMenuContent
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.util.Assert
import com.android.systemui.util.printSection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt

/**
 * A universally safe default implementation of the IME Switcher Menu. Unlike the main
 * implementation in [ImeSwitcherMenuDialogDelegate], this does NOT rely on `SystemUIDialogFactory`,
 * ensuring it compiles and runs on form factors that might lack specific SystemUI visual
 * dependencies (like Blur or TransitionAnimators).
 */
class DefaultImeSwitcherMenuDialog
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel,
    private val configurationController: ConfigurationController,
    private val broadcastDispatcher: BroadcastDispatcher,
) : ImeSwitcherMenuUi, ConfigurationController.ConfigurationListener {

    /** The latest value of the night mode. */
    private var lastNightMode: Int =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** The currently showing IME Switcher Menu dialog, or `null` if no menu is showing. */
    private var currentDialog: ComponentDialog? = null

    /** Creates a new instance of the dialog. */
    private fun createDialog(): ComponentDialog {
        currentDialog?.let {
            Log.w(TAG, "Dialog is already open, dismissing it and creating a new one")
            it.dismiss()
        }

        val dialog =
            object : ComponentDialog(context, R.style.Theme_SystemUI_Dialog) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }

                override fun onStart() {
                    super.onStart()
                    configurationController.addCallback(this@DefaultImeSwitcherMenuDialog)
                }

                override fun onStop() {
                    super.onStop()
                    configurationController.removeCallback(this@DefaultImeSwitcherMenuDialog)
                }
            }
        val dismissReceiver = DismissReceiver(broadcastDispatcher) { dialog.dismiss() }
        dismissReceiver.register()

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.let {
            it.attributes.apply {
                // Use an alternate token for the dialog for that window manager can group the token
                // with other IME windows based on type vs. grouping based on whichever token
                // happens to get selected by the system later on.
                token = dialog.context.windowContextToken
                gravity =
                    Gravity.getAbsoluteGravity(
                        Gravity.BOTTOM or Gravity.END,
                        dialog.context.resources.configuration.layoutDirection,
                    )
                x = HORIZONTAL_OFFSET
                privateFlags =
                    privateFlags or WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                // Used for debugging only, not user visible.
                title = WINDOW_TITLE
                fitInsetsTypes =
                    fitInsetsTypes or
                        WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
            }
            it.setHideOverlayWindows(true)
            it.setLayout(getWidth(dialog), WindowManager.LayoutParams.WRAP_CONTENT)
        }

        dialog.setContentView(
            ComposeView(context).apply {
                setContent {
                    PlatformTheme {
                        val defaultContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        CompositionLocalProvider(LocalContentColor provides defaultContentColor) {
                            ImeSwitcherMenuContent(viewModelFactory) { dialog.dismiss() }
                        }
                    }
                }
            }
        )

        dialog.setOnDismissListener {
            dismissReceiver.unregister()
            currentDialog = null
        }
        dialog.setCanceledOnTouchOutside(true)

        currentDialog = dialog
        return dialog
    }

    @MainThread
    override fun show() {
        Assert.isMainThread()

        if (isShowing) {
            return
        }

        val dialog = createDialog()
        dialog.show()
    }

    @MainThread
    override fun dismiss() {
        Assert.isMainThread()

        currentDialog?.dismiss()
    }

    /** Whether the IME Switcher Menu is showing. */
    val isShowing: Boolean
        get() = currentDialog?.isShowing == true

    override fun dump(pw: IndentingPrintWriter, description: String) {
        pw.run { printSection("$TAG $description") { println("isShowing: $isShowing") } }
    }

    override fun onConfigChanged(newConfig: Configuration?) {
        if (newConfig == null) {
            return
        }

        val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (lastNightMode != nightMode) {
            lastNightMode = nightMode

            currentDialog?.window?.let {
                // The window's context is updated after this callback, so we need to manually
                // create a new context and resolve the drawable to update the background.
                val loadContext = it.context.createConfigurationContext(newConfig)
                loadContext.setTheme(it.context.themeResId)
                val outValue = TypedValue()
                loadContext.theme.resolveAttribute(android.R.attr.windowBackground, outValue, true)
                it.decorView.background = loadContext.getDrawable(outValue.resourceId)
            }
        }
    }

    /**
     * Computes the width of the given dialog.
     *
     * @param dialog the dialog to compute the width for.
     */
    private fun getWidth(dialog: ComponentDialog): Int {
        // The base width of the window is set from config_prefDialogWidth. This would be overridden
        // by the first view that has some fixed layout_width set. For Compose the width modifier
        // would not be enforced, and thus limit it to the base width, and the requiredWidthIn
        // modifier would not propagate up the hierarchy, and would lead to clipping the content.
        // TODO(b/469720572): Use WRAP_CONTENT and set width on the outermost Column above once
        //  view-compose measurement is fixed.
        val desiredWith = calculateDialogWidthWithInsets(dialog, DESIRED_WITH_DP)
        val screenWidth = dialog.context.resources.displayMetrics.widthPixels
        return desiredWith.coerceAtMost(screenWidth)
    }

    /**
     * Return the pixel width `dialog` should be so that it is {@code widthInDp} wide, taking its
     * background insets into consideration.
     */
    private fun calculateDialogWidthWithInsets(dialog: ComponentDialog, widthInDp: Float): Int {
        val widthInPx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                widthInDp,
                dialog.context.resources.displayMetrics,
            )
        return (widthInPx + getHorizontalInsets(dialog)).roundToInt()
    }

    /**
     * Gets the horizontal insets from the background of the given dialog.
     *
     * @param dialog the dialog to get the horizontal insets for.
     */
    private fun getHorizontalInsets(dialog: ComponentDialog): Int {
        val decorView = dialog.window?.decorView ?: return 0
        val insets = decorView.background?.opticalInsets ?: return 0
        return insets.left + insets.right
    }

    /**
     * Broadcast receiver for dismissing the dialog when [Intent.ACTION_SCREEN_OFF] or
     * [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] is received.
     */
    private class DismissReceiver(
        val broadcastDispatcher: BroadcastDispatcher,
        val dismissAction: () -> Unit,
    ) : BroadcastReceiver() {

        var registered = false

        fun register() {
            val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            broadcastDispatcher.registerReceiver(this, intentFilter, null, UserHandle.CURRENT)
            registered = true
        }

        fun unregister() {
            if (registered) {
                broadcastDispatcher.unregisterReceiver(this)
                registered = false
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            dismissAction.invoke()
        }
    }

    @AssistedFactory
    interface Factory : ImeSwitcherMenuUi.Factory {

        override fun create(
            context: Context,
            viewModelFactory: (Context) -> ImeSwitcherMenuViewModel,
        ): DefaultImeSwitcherMenuDialog
    }

    companion object {

        private const val TAG = "DefImeSwitcherMenuDial"

        /**
         * The horizontal offset from the menu to the edge of the screen corresponding to
         * [Gravity.END].
         */
        private const val HORIZONTAL_OFFSET = 16

        /** The desired width of the dialog, in dp units. */
        private const val DESIRED_WITH_DP = 320F

        /** The title of the window, used for debugging. */
        private const val WINDOW_TITLE = "Default IME Switcher Menu"
    }
}
