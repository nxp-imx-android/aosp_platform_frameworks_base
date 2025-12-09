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

import android.annotation.IntRange
import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.Slog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.internal.widget.RecyclerView
import com.android.systemui.inputmethod.ImeSwitcherMenuDialogDelegate.Companion.NOT_A_SUBTYPE_INDEX
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.util.Assert
import java.io.PrintWriter

internal class ImeSwitcherMenuDialogDelegate(
    private val context: Context,
    private val sysuiDialogFactory: SystemUIDialogFactory,
    /** The ID of the display where the menu was requested. */
    private val displayId: Int,
    /** The ID of the user that requested the menu. */
    @param:UserIdInt private val userId: Int,
    /** The interface to callback into on specific events. */
    private val listener: ImeSwitcherMenuDialogListener,
) : SystemUIDialog.Delegate {

    /** The currently showing IME Switcher Menu dialog, or `null` if no menu is showing. */
    private var currentDialog: SystemUIDialog? = null

    private var menuItems: List<MenuItem>? = null

    internal interface ImeSwitcherMenuDialogListener {

        /**
         * Called when the IME Switcher Menu visibility changed for the given user on the given
         * display.
         *
         * @param visible the new visibility of the menu.
         * @param displayId the ID of the display where the menu visibility changed.
         * @param userId the ID of the user whose menu visibility changed.
         */
        fun onVisibilityChanged(visible: Boolean, displayId: Int, @UserIdInt userId: Int)

        /**
         * Called when an IME and subtype was selected in the IME Switcher Menu by the given user.
         * This will switch to the IME if it is enabled and installed, and otherwise will do
         * nothing. If the subtype index is also supplied (not [.NOT_A_SUBTYPE_INDEX]) and valid,
         * also switches to it, otherwise the system devices the most sensible default subtype to
         * use.
         *
         * @param imeId the ID of the selected IME.
         * @param subtypeIndex the selected subtype, as an index in the input method's array of
         *   subtypes, or [.NOT_A_SUBTYPE_INDEX] if the system should decide the most sensible
         *   subtype.
         * @param userId the ID of the user that selected the IME and subtype.
         */
        fun onImeAndSubtypeSelected(
            imeId: String,
            @IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) subtypeIndex: Int,
            @UserIdInt userId: Int,
        )
    }

    override fun createDialog(): SystemUIDialog {
        Assert.isMainThread()
        currentDialog?.let {
            Slog.w(TAG, "Dialog is already open, dismissing it and creating a new one")
            it.dismiss()
        }
        val dialog =
            sysuiDialogFactory.create(
                context,
                R.style.Theme_DeviceDefault_InputMethodSwitcherDialog,
                true,
                this,
            )

        dialog.create()

        val context = dialog.context
        LayoutInflater.from(context).inflate(R.layout.input_method_switch_dialog, null).apply {
            accessibilityPaneTitle = context.getText(R.string.select_input_method)
            dialog.setContentView(this)
        }

        dialog.setOnShowListener { listener.onVisibilityChanged(true, displayId, userId) }
        dialog.setOnDismissListener { listener.onVisibilityChanged(false, displayId, userId) }
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let {
            it.attributes.apply {
                // Use an alternate token for the dialog for that window manager can group the
                // token
                // with other IME windows based on type vs. grouping based on whichever token
                // happens to get selected by the system later on.
                token = context.windowContextToken
                gravity =
                    Gravity.getAbsoluteGravity(
                        Gravity.BOTTOM or Gravity.END,
                        context.resources.configuration.layoutDirection,
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
        }
        dialog.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    Assert.isMainThread()
                    currentDialog = null
                    menuItems = null
                }
            }
        )

        currentDialog = dialog
        return dialog
    }

    override fun getWidth(dialog: SystemUIDialog): Int = WindowManager.LayoutParams.WRAP_CONTENT

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
     */
    fun show(
        items: List<IImeSwitcherMenu.Item>,
        selectedImeId: String?,
        selectedSubtypeIndex: Int,
        selectedImeSettingsIntent: Intent?,
        isScreenLocked: Boolean,
    ) {
        val dialog = createDialog()

        val menuItems = getMenuItems(items)
        this.menuItems = menuItems
        val selectedIndex = getSelectedIndex(menuItems, selectedImeId, selectedSubtypeIndex)
        if (selectedIndex == -1) {
            Slog.w(
                TAG,
                "Switching menu shown with no item selected, IME id: $selectedImeId, subtype" +
                    " index: $selectedSubtypeIndex",
            )
        }

        val inflater = LayoutInflater.from(dialog.context)

        val onItemClick = { item: SubtypeItem, isSelected: Boolean ->
            if (!isSelected) {
                listener.onImeAndSubtypeSelected(item.imeId, item.subtypeIndex, userId)
            }
            dialog.dismiss()
        }

        // Create the current IME subtypes list.
        val recyclerView: RecyclerView = dialog.requireViewById(R.id.list)
        recyclerView.setAdapter(Adapter(menuItems, selectedIndex, inflater, onItemClick))
        // Scroll to the currently selected IME. This must run after the recycler view is laid out.
        recyclerView.post { recyclerView.scrollToPosition(selectedIndex) }
        // Request focus to enable rotary scrolling on watches.
        recyclerView.requestFocus()

        updateLanguageSettingsButton(selectedImeSettingsIntent, dialog, isScreenLocked)

        dialog.show()
    }

    fun dismiss() {
        currentDialog?.dismiss()
    }

    /** Whether the Input Method Switcher Menu is showing. */
    val isShowing: Boolean
        get() = currentDialog?.isShowing == true

    fun dump(pw: PrintWriter, prefix: String) {
        pw.println("${prefix}$TAG$ u$userId")
        val showing = isShowing
        val innerPrefix = "$prefix  "
        pw.println("${innerPrefix}isShowing: $showing")

        if (showing) {
            pw.println("${innerPrefix}menuItems: $menuItems")
        }
    }

    /**
     * Updates the visibility of the Language Settings button to visible if the given settings
     * intent is non-`null`, the screen is not locked and the device is provisioned. Otherwise, the
     * button won't be shown.
     *
     * @param settingsIntent the intent for the settings activity of the selected IME, or `null` if
     *   no IME is selected, or the selected IME does not have a settings activity.
     * @param dialog the dialog.
     * @param isScreenLocked whether the screen is currently locked.
     */
    private fun updateLanguageSettingsButton(
        settingsIntent: Intent?,
        dialog: SystemUIDialog,
        isScreenLocked: Boolean,
    ) {
        val isDeviceProvisioned =
            Settings.Global.getInt(
                dialog.context.contentResolver,
                Settings.Global.DEVICE_PROVISIONED,
                0,
            ) != 0
        val hasButton = settingsIntent != null && !isScreenLocked && isDeviceProvisioned
        val buttonBar: View = dialog.requireViewById(R.id.button_bar)
        val button: Button = dialog.requireViewById(R.id.button1)
        val recyclerView: RecyclerView = dialog.requireViewById(R.id.list)
        if (hasButton) {
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            buttonBar.visibility = View.VISIBLE
            button.setOnClickListener {
                dialog.context.startActivityAsUser(settingsIntent, UserHandle.of(userId))
                dialog.dismiss()
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
                    if (!TextUtils.isEmpty(item.layoutName)) View.VISIBLE else View.GONE
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

        private const val TAG: String = "ImeSwitcherMenuDialog"

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
