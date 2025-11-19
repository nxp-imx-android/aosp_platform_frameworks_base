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
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.RemoteException
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.util.Slog
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.Flags
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.internal.inputmethod.IImeSwitcherMenuListener
import com.android.internal.widget.RecyclerView
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.inputmethod.ImeSwitcherMenuController.Companion.NOT_A_SUBTYPE_INDEX
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

    /** The interface to receive callbacks from this controller. */
    private var listener: IImeSwitcherMenuListener? = null

    private var dialogWindowContext: Context? = null

    private var dialog: AlertDialog? = null

    private var menuItems: List<MenuItem>? = null

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
        // Hide the menu in case it was already showing.
        hide(displayId, userId)

        val menuItems = getMenuItems(items)
        val selectedIndex = getSelectedIndex(menuItems, selectedImeId, selectedSubtypeIndex)
        if (selectedIndex == -1) {
            Slog.w(
                TAG,
                "Switching menu shown with no item selected, IME id: $selectedImeId, subtype" +
                    " index: $selectedSubtypeIndex",
            )
        }

        val dialogWindowContext = getContext(displayId)
        val builder =
            AlertDialog.Builder(
                dialogWindowContext,
                R.style.Theme_DeviceDefault_InputMethodSwitcherDialog,
            )
        val inflater = LayoutInflater.from(builder.context)

        // Create the content view.
        val contentView = inflater.inflate(R.layout.input_method_switch_dialog, null)
        contentView.accessibilityPaneTitle =
            dialogWindowContext.getText(R.string.select_input_method)
        builder.setView(contentView)

        val onItemClick = { item: SubtypeItem, isSelected: Boolean ->
            if (!isSelected) {
                onImeAndSubtypeSelected(item.imeId, item.subtypeIndex, userId)
            }
            hide(displayId, userId)
        }

        // Create the current IME subtypes list.
        val recyclerView: RecyclerView = contentView.requireViewById(R.id.list)
        recyclerView.setAdapter(Adapter(menuItems, selectedIndex, inflater, onItemClick))
        // Scroll to the currently selected IME. This must run after the recycler view is laid out.
        recyclerView.post { recyclerView.scrollToPosition(selectedIndex) }
        // Request focus to enable rotary scrolling on watches.
        recyclerView.requestFocus()

        updateLanguageSettingsButton(
            selectedImeSettingsIntent,
            contentView,
            isScreenLocked,
            displayId,
            userId,
        )

        builder.setOnCancelListener { hide(displayId, userId) }
        this.menuItems = menuItems
        dialog =
            builder.create().apply {
                setCanceledOnTouchOutside(true)
                window?.let {
                    it.setHideOverlayWindows(true)
                    it.attributes =
                        it.attributes.apply {
                            // Use an alternate token for the dialog for that window manager can
                            // group the
                            // token  with other IME windows based on type vs. grouping based on
                            // whichever
                            // token happens  to get selected by the system later on.
                            token = dialogWindowContext.windowContextToken
                            gravity =
                                Gravity.getAbsoluteGravity(
                                    Gravity.BOTTOM or Gravity.END,
                                    dialogWindowContext.resources.configuration.layoutDirection,
                                )
                            x = HORIZONTAL_OFFSET
                            privateFlags =
                                privateFlags or
                                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                            type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                            // Used for debugging only, not user visible.
                            title = WINDOW_TITLE
                        }
                }
                show()
            }
        onVisibilityChanged(true /* visible */, displayId, userId)
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

        menuItems = null
        // Cannot use dialog.isShowing() here, as the cancel listener flow already resets mShowing.
        dialog?.let {
            it.dismiss()
            dialog = null
            onVisibilityChanged(false /* visible */, displayId, userId)
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

    /** Whether the Input Method Switcher Menu is showing. */
    val isShowing: Boolean
        get() = dialog?.isShowing ?: false

    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println()
        pw.println(TAG)
        val showing = isShowing
        pw.println("isShowing: $showing")

        if (showing) {
            pw.println("menuItems: $menuItems")
        }
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

    /**
     * Updates the visibility of the Language Settings button to visible if the given settings
     * intent is non-`null`, the screen is not locked and the device is provisioned. Otherwise, the
     * button won't be shown.
     *
     * @param settingsIntent the intent for the settings activity of the selected IME, or `null` if
     *   no IME is selected, or the selected IME does not have a settings activity.
     * @param view the menu dialog view.
     * @param isScreenLocked whether the screen is currently locked.
     * @param displayId the ID of the display where the menu was requested.
     * @param userId the ID of the user that requested the menu.
     */
    private fun updateLanguageSettingsButton(
        settingsIntent: Intent?,
        view: View,
        isScreenLocked: Boolean,
        displayId: Int,
        @UserIdInt userId: Int,
    ) {
        val isDeviceProvisioned =
            Settings.Global.getInt(
                view.context.contentResolver,
                Settings.Global.DEVICE_PROVISIONED,
                0,
            ) != 0
        val hasButton = settingsIntent != null && !isScreenLocked && isDeviceProvisioned
        val buttonBar: View = view.requireViewById(R.id.button_bar)
        val button: Button = view.requireViewById(R.id.button1)
        val recyclerView: RecyclerView = view.requireViewById(R.id.list)
        if (hasButton) {
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            buttonBar.visibility = View.VISIBLE
            button.setOnClickListener {
                it.context.startActivityAsUser(settingsIntent, UserHandle.of(userId))
                hide(displayId, userId)
            }
            // Indicate that the list can be scrolled.
            recyclerView.scrollIndicators = View.SCROLL_INDICATOR_BOTTOM
        } else {
            buttonBar.visibility = View.GONE
            button.setOnClickListener(null)
            // Remove scroll indicator as there is nothing drawn below the list.
            recyclerView.scrollIndicators = 0
        }
    }

    fun onVisibilityChanged(visible: Boolean, displayId: Int, @UserIdInt userId: Int) {
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

    fun onImeAndSubtypeSelected(
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

    /** Item to be displayed in the menu. */
    private sealed interface MenuItem

    /** Subtype item containing an input method and optionally an input method subtype. */
    private data class SubtypeItem(

        /** The name of the input method. */
        val imeName: CharSequence,

        /** The name of the input method subtype, or `null` if this item doesn't have a subtype. */
        val subtypeName: CharSequence?,

        /**
         * The name of the subtype's layout, or `null` if this item doesn't have a subtype, or
         * doesn't specify a layout.
         */
        val layoutName: CharSequence?,

        /** The ID of the input method. */
        val imeId: String,

        /**
         * The index of the subtype in the input method's array of subtypes, or
         * [NOT_A_SUBTYPE_INDEX] if this item doesn't have a subtype.
         */
        @param:IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) val subtypeIndex: Int,
    ) : MenuItem

    /** Header item displayed before a group of [SubtypeItem] of the same input method. */
    private data class HeaderItem(

        /** The header title. */
        val title: CharSequence
    ) : MenuItem

    /** Divider item displayed before a [HeaderItem]. */
    private data object DividerItem : MenuItem

    private class Adapter(

        /** The list of items to show. */
        private val items: List<MenuItem>,

        /** The index of the selected item. */
        @param:IntRange(from = -1) private val selectedIndex: Int,
        private val inflater: LayoutInflater,

        /** The listener used to handle clicks on [Adapter.SubtypeViewHolder] items. */
        private val onItemClick: (SubtypeItem, Boolean) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup?,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_SUBTYPE -> {
                    val view = inflater.inflate(R.layout.input_method_switch_item, parent, false)
                    SubtypeViewHolder(view, onItemClick)
                }

                TYPE_HEADER -> {
                    val view =
                        inflater.inflate(R.layout.input_method_switch_item_header, parent, false)
                    HeaderViewHolder(view)
                }

                TYPE_DIVIDER -> {
                    val view =
                        inflater.inflate(R.layout.input_method_switch_item_divider, parent, false)
                    DividerViewHolder(view)
                }

                else -> throw IllegalArgumentException("Unknown viewType: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
            val item = items[position]
            when {
                holder is SubtypeViewHolder && item is SubtypeItem -> {
                    holder.bind(item, position == selectedIndex /* isSelected */)
                }
                holder is HeaderViewHolder && item is HeaderItem -> {
                    holder.bind(item)
                }
                holder is DividerViewHolder && item is DividerItem -> {
                    // Nothing to bind for dividers.
                    return
                }
                else -> Slog.w(TAG, "Holder type: $holder doesn't match item type: $item")
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SubtypeItem -> TYPE_SUBTYPE
                is HeaderItem -> TYPE_HEADER
                is DividerItem -> TYPE_DIVIDER
            }
        }

        private class SubtypeViewHolder(
            /** The container of the item. */
            private val container: View,
            private val onItemClick: (SubtypeItem, Boolean) -> Unit,
        ) : RecyclerView.ViewHolder(container) {
            /** The name of the item. */
            private val name: TextView = container.requireViewById(R.id.text)

            /** The layout name. */
            private val layout: TextView = container.requireViewById(R.id.text2)

            /** Indicator for the selected status of the item. */
            private val checkmark: ImageView = container.requireViewById(R.id.image)

            /** The bound item data, or `null` if no item was bound yet. */
            private var item: SubtypeItem? = null

            /** Whether this item is the currently selected one. */
            private var isSelected = false

            init {
                container.setOnClickListener { item?.let { onItemClick(it, isSelected) } }
            }

            /**
             * Binds the given item to the current view.
             *
             * @param item the item to bind.
             * @param isSelected whether the item is selected.
             */
            fun bind(item: SubtypeItem, isSelected: Boolean) {
                this.item = item
                this.isSelected = isSelected
                container.isActivated = isSelected
                // Activated is the correct state, but we also set selected for accessibility info.
                container.isSelected = isSelected
                // Trigger the ellipsize marquee behaviour by selecting the name.
                name.setSelected(isSelected)
                // Use the IME name for subtypes with an empty subtype name.
                name.text = if (item.subtypeName.isNullOrEmpty()) item.imeName else item.subtypeName
                layout.text = item.layoutName
                layout.visibility =
                    if (!item.layoutName.isNullOrEmpty()) View.VISIBLE else View.GONE
                checkmark.setVisibility(if (isSelected) View.VISIBLE else View.GONE)
            }
        }

        private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            /** The title view, only visible if the bound item has a title. */
            private val title: TextView = itemView.requireViewById(R.id.header_text)

            /**
             * Binds the given item to the current view.
             *
             * @param item the item to bind.
             */
            fun bind(item: HeaderItem) {
                title.text = item.title
            }
        }

        private class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        companion object {

            /** View type for [SubtypeItem]. */
            private const val TYPE_SUBTYPE = 0

            /** View type for [HeaderItem]. */
            private const val TYPE_HEADER = 1

            /** View type for [DividerItem]. */
            private const val TYPE_DIVIDER = 2
        }
    }

    companion object {
        private const val TAG: String = "ImeSwitcherMenuCtrl"

        private const val DEBUG = false

        /**
         * The horizontal offset from the menu to the edge of the screen corresponding to
         * [Gravity.END].
         */
        private const val HORIZONTAL_OFFSET = 16

        /** The title of the window, used for debugging. */
        private const val WINDOW_TITLE = "IME Switcher Menu"

        private const val NOT_A_SUBTYPE_INDEX = -1

        /**
         * Creates the list of menu items from the given list of input methods and subtypes. This
         * handles adding headers and dividers between groups of items from different input methods
         * as follows:
         * * If there is only one group, no divider or header will be added.
         * * A divider is added before each group, except the first one.
         * * A header is added before each group (after the divider, if it exists) if the group has
         *   at least two items, or a single item with a subtype name.
         *
         * @param items the list of input method and subtype items.
         */
        @VisibleForTesting
        private fun getMenuItems(items: List<IImeSwitcherMenu.Item>): List<MenuItem> {
            val menuItems = mutableListOf<MenuItem>()
            if (items.isEmpty()) {
                return menuItems
            }

            val numItems = items.size
            // Initialize to the last IME id to avoid headers if there is only a single IME.
            var prevImeId = items.last().imeId
            var firstGroup = true
            for (i in 0..<numItems) {
                val item = items[i]

                val imeId = item.imeId
                val groupChange = imeId != prevImeId
                if (groupChange) {
                    if (!firstGroup) {
                        menuItems.add(DividerItem)
                    }
                    // Add a header if we have at least two items, or a single item with a subtype
                    // name.
                    val nextItemId: String? = items.getOrNull(i + 1)?.imeId
                    val addHeader = item.subtypeName != null || imeId == nextItemId
                    if (addHeader) {
                        menuItems.add(HeaderItem(item.imeName))
                    }
                    firstGroup = false
                    prevImeId = imeId
                }

                menuItems.add(
                    SubtypeItem(
                        item.imeName,
                        item.subtypeName,
                        item.layoutName,
                        item.imeId,
                        item.subtypeIndex,
                    )
                )
            }

            return menuItems
        }

        /**
         * Gets the index of the selected item.
         *
         * @param items the list of menu items.
         * @param selectedImeId the ID of the selected input method.
         * @param selectedSubtypeIndex the index of the selected subtype in the input method's array
         *   of subtypes, or [NOT_A_SUBTYPE_INDEX] if no subtype is selected.
         * @return the index of the selected item, or `-1` if no item is selected.
         */
        @VisibleForTesting
        @IntRange(from = -1)
        private fun getSelectedIndex(
            items: List<MenuItem>,
            selectedImeId: String?,
            selectedSubtypeIndex: Int,
        ): Int {
            // Returns -1 if there is no selected IME, or the selected subtype is enabled but not in
            // the list. This can happen if an implicit subtype is selected, but we got a list of
            // explicit subtypes. In this case, the implicit subtype will no longer be included in
            // the list.
            return items.indexOfFirst { item ->
                item is SubtypeItem &&
                    item.imeId == selectedImeId &&
                    ((item.subtypeIndex == 0 && selectedSubtypeIndex == NOT_A_SUBTYPE_INDEX) ||
                        item.subtypeIndex == NOT_A_SUBTYPE_INDEX ||
                        item.subtypeIndex == selectedSubtypeIndex)
            }
        }
    }
}
