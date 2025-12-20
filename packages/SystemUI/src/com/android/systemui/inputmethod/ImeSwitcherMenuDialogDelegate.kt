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
import android.util.Slog
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.systemui.inputmethod.ImeSwitcherMenuDialogDelegate.Companion.NOT_A_SUBTYPE_INDEX
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
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

    private val onSettingsClick = mutableStateOf<(() -> Unit)?>(null)

    private val menuItems = mutableStateListOf<MenuItem>()

    private val selectedIndex = mutableIntStateOf(-1)

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
            sysuiDialogFactory.create(context = context, dialogDelegate = this) {
                ImeSwitcherMenuContent(it)
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
        }
        dialog.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    Assert.isMainThread()
                    currentDialog = null
                    menuItems.clear()
                }
            }
        )

        currentDialog = dialog
        return dialog
    }

    @Composable
    private fun ImeSwitcherMenuContent(dialog: SystemUIDialog) {
        // TODO(b/369376884): The composable does correctly update when the theme changes
        //  while the dialog is open, but the background (which we don't control here)
        //  doesn't, which causes us to show things like white text on a white background.
        //  as a workaround, we remember the original theme and keep it on recomposition.
        val isCurrentlyInDarkTheme = isSystemInDarkTheme()
        val cachedDarkTheme = remember { isCurrentlyInDarkTheme }
        val paneTitleDescription = stringResource(R.string.select_input_method)
        val buttonDescription = stringResource(R.string.input_method_language_settings)

        PlatformTheme(isDarkTheme = cachedDarkTheme) {
            Column(
                modifier = Modifier.fillMaxWidth().semantics { paneTitle = paneTitleDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ImeSwitcherMenuList(menuItems.toList(), dialog)
                onSettingsClick.value?.let {
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier =
                            Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        PlatformOutlinedButton(
                            onClick = {
                                it.invoke()
                                dialog.dismiss()
                            }
                        ) {
                            Text(
                                text =
                                    stringResource(R.string.input_method_switcher_settings_button),
                                modifier =
                                    Modifier.padding(vertical = 3.dp).semantics {
                                        contentDescription = buttonDescription
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ImeSwitcherMenuList(items: List<MenuItem>, dialog: SystemUIDialog) {
        val listState = rememberLazyListState()
        LaunchedEffect(selectedIndex.intValue, items.size) {
            val index = selectedIndex.intValue
            if (index != -1 && index < items.size) {
                listState.scrollToItem(index)
            }
        }

        // TODO(b/308488505): Add scroll indicators to the LazyColumn once implemented.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp),
            modifier = Modifier.heightIn(max = 373.dp),
        ) {
            itemsIndexed(items, key = { _, item -> "${item.imeId}:${item.subtypeIndex}" }) {
                index,
                item ->
                MenuItemView(item, index == selectedIndex.intValue, dialog)
            }
        }
    }

    @Composable
    private fun MenuItemView(item: MenuItem, isSelected: Boolean, dialog: SystemUIDialog) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (item.hasDivider) {
                HorizontalDivider(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 20.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            if (item.hasHeader) {
                Text(
                    text = item.imeName.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(top = 4.dp, bottom = 16.dp),
                )
            }

            val backgroundColor =
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            val selectedColor =
                if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .heightIn(min = 72.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(backgroundColor)
                        .semantics { this.selected = isSelected }
                        .clickable(
                            onClick = {
                                if (!isSelected) {
                                    listener.onImeAndSubtypeSelected(
                                        item.imeId,
                                        item.subtypeIndex,
                                        userId,
                                    )
                                }
                                dialog.dismiss()
                            }
                        )
                        .padding(start = 20.dp, end = 24.dp)
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val text =
                        if (item.subtypeName.isNullOrEmpty()) item.imeName else item.subtypeName
                    Text(
                        text = text.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = selectedColor,
                        maxLines = 1,
                        modifier =
                            if (isSelected) Modifier.basicMarquee(iterations = 1) else Modifier,
                    )
                    if (!item.layoutName.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.layoutName.toString().uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier =
                                if (isSelected) Modifier.basicMarquee(iterations = 1) else Modifier,
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        painter = painterResource(com.android.systemui.res.R.drawable.ic_check),
                        contentDescription = null, // decorative
                        tint = selectedColor,
                        modifier = Modifier.padding(start = 12.dp).size(24.dp),
                    )
                }
            }
        }
    }

    override fun getWidth(dialog: SystemUIDialog): Int {
        // The base width of the window is set from config_prefDialogWidth. This would be overridden
        // by the first view that has some fixed layout_width set. For Compose the width modifier
        // would not be enforced, and thus limit it to the base width, and the requiredWidthIn
        // modifier would not propagate up the hierarchy, and would lead to clipping the content.
        // TODO(b/469720572): Use WRAP_CONTENT and set width on the outermost Column above once
        //  view-compose measurement is fixed.
        val desiredWidth = SystemUIDialog.calculateDialogWidthWithInsets(dialog, 320)
        val screenWidth = dialog.context.resources.displayMetrics.widthPixels
        return desiredWidth.coerceAtMost(screenWidth)
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
        this.menuItems.addAll(menuItems)
        val selectedIndex = getSelectedIndex(menuItems, selectedImeId, selectedSubtypeIndex)
        this.selectedIndex.intValue = selectedIndex
        if (selectedIndex == -1) {
            Slog.w(
                TAG,
                "Switching menu shown with no item selected, IME id: $selectedImeId, subtype" +
                    " index: $selectedSubtypeIndex",
            )
        }

        updateLanguageSettingsButton(selectedImeSettingsIntent, isScreenLocked)

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
     * @param isScreenLocked whether the screen is currently locked.
     */
    private fun updateLanguageSettingsButton(settingsIntent: Intent?, isScreenLocked: Boolean) {
        val isDeviceProvisioned =
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVICE_PROVISIONED,
                0,
            ) != 0
        val hasButton = settingsIntent != null && !isScreenLocked && isDeviceProvisioned
        if (hasButton) {
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            onSettingsClick.value = {
                context.startActivityAsUser(settingsIntent, UserHandle.of(userId))
            }
        } else {
            onSettingsClick.value = null
        }
    }

    /**
     * Item to be displayed in the menu, containing an input method and optionally an input method
     * subtype.
     */
    private data class MenuItem(

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

        /** Whether this item has a divider, displayed before the header. */
        val hasDivider: Boolean,

        /** Whether this item has a header. */
        val hasHeader: Boolean,
    )

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

            // Initialize to the last IME id to avoid headers if there is only a single IME.
            var prevImeId = items.last().imeId
            var firstGroup = true
            for (i in items.indices) {
                val item = items[i]

                var hasDivider = false
                var hasHeader = false

                val groupChange = item.imeId != prevImeId
                if (groupChange) {
                    hasDivider = !firstGroup
                    // Has a header if we have at least two items, or a single item with a subtype
                    // name.
                    val nextItemId: String? = items.getOrNull(i + 1)?.imeId
                    hasHeader = item.subtypeName != null || item.imeId == nextItemId
                    firstGroup = false
                    prevImeId = item.imeId
                }

                menuItems.add(
                    MenuItem(
                        item.imeName,
                        item.subtypeName,
                        item.layoutName,
                        item.imeId,
                        item.subtypeIndex,
                        hasDivider,
                        hasHeader,
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
                item.imeId == selectedImeId &&
                    ((item.subtypeIndex == 0 && selectedSubtypeIndex == NOT_A_SUBTYPE_INDEX) ||
                        item.subtypeIndex == NOT_A_SUBTYPE_INDEX ||
                        item.subtypeIndex == selectedSubtypeIndex)
            }
        }
    }
}
