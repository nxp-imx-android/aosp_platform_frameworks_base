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

package com.android.systemui.statusbar.notification.row

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.notifications.ui.composable.row.createBundleHeaderGutsComposeView
import com.android.systemui.statusbar.notification.collection.BundleEntryAdapter
import com.android.systemui.statusbar.notification.row.NotificationGuts.GutsContent
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderGutsViewModel

/**
 * This View is a container for a ComposeView and implements GutsContent. Technically, this should
 * not be a View as GutsContent could just return the ComposeView directly for getContentView().
 * Unfortunately, the legacy design of `NotificationMenuRowPlugin.MenuItem.getGutsView()` forces the
 * GutsContent to be a View itself. Therefore this class is a view that just holds the ComposeView.
 *
 * A redesign of `NotificationMenuRowPlugin.MenuItem.getGutsView()` to return GutsContent instead is
 * desired but it lacks proper module dependencies. As soon as this class does not need to inherit
 * from View it can just return the ComposeView directly instead.
 */
class BundleHeaderGutsContent
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), GutsContent {

    private var composeView: ComposeView? = null
    private var gutsParent: NotificationGuts? = null

    fun bindNotification(
        row: ExpandableNotificationRow,
        onSettingsClicked: () -> Unit = {},
        onDoneClicked: () -> Unit = {},
        onDismissClicked: () -> Unit = {},
    ) {
        if (composeView != null) return

        val repository = (row.entryAdapter as BundleEntryAdapter).entry.bundleRepository
        val viewModel =
            BundleHeaderGutsViewModel(
                titleTextResId = repository.titleTextResId,
                bundleIcon = repository.bundleIcon,
                onSettingsClicked = onSettingsClicked,
                onDoneClicked = onDoneClicked,
                onDismissClicked = onDismissClicked,
            )
        composeView = createBundleHeaderGutsComposeView(context, viewModel)
        addView(composeView)
    }

    override fun setGutsParent(listener: NotificationGuts?) {
        this.gutsParent = listener
    }

    override fun getContentView(): View {
        return this
    }

    override fun getActualHeight(): Int {
        return composeView?.measuredHeight ?: 0
    }

    override fun handleCloseControls(save: Boolean, force: Boolean): Boolean {
        return false
    }

    override fun willBeRemoved(): Boolean {
        return false
    }

    override fun shouldBeSavedOnClose(): Boolean {
        return false
    }

    override fun needsFalsingProtection(): Boolean {
        return true
    }
}
