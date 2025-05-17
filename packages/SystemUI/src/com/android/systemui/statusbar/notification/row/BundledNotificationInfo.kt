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

import android.app.INotificationManager
import android.app.NotificationChannel.NEWS_ID
import android.app.NotificationChannel.PROMOTIONS_ID
import android.app.NotificationChannel.RECS_ID
import android.app.NotificationChannel.SOCIAL_MEDIA_ID
import android.content.Context
import android.content.pm.PackageManager
import android.os.RemoteException
import android.service.notification.Adjustment
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.AssistantFeedbackController
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.google.android.material.materialswitch.MaterialSwitch


/**
 * The guts of a notification revealed when performing a long press, specifically for notifications
 * that are bundled. Contains controls to allow user to disable the feature for the app that posted
 * the notification.
 */
class BundledNotificationInfo(context: Context?, attrs: AttributeSet?) :
    NotificationInfo(context, attrs) {

    override fun bindInlineControls() {
        val enabled =
            mINotificationManager.isAdjustmentSupportedForPackage(
                mSbn.normalizedUserId,
                Adjustment.KEY_TYPE,
                mSbn.packageName,
            )
        val toggle = findViewById<MaterialSwitch>(R.id.feature_toggle)
        toggle.isChecked = enabled
        toggle.setOnCheckedChangeListener { buttonView, isChecked ->
            val isAChange = isChecked != enabled
            val done = findViewById<TextView>(R.id.done)
            done.setText(
                if (isAChange)
                    R.string.inline_ok_button
                else
                    R.string.inline_done_button
            )
        }

        val done = findViewById<TextView>(R.id.done)
        done.setOnClickListener {
            try {
                if (NotificationBundleUi.isEnabled) {
                    mEntryAdapter.markForUserTriggeredMovement(true)
                    mEntryAdapter.onImportanceChanged()
                } else {
                    mEntry.markForUserTriggeredMovement(true)
                    mOnUserInteractionCallback.onImportanceChanged(mEntry)
                }
                mINotificationManager.setAdjustmentSupportedForPackage(
                    mSbn.normalizedUserId,
                    Adjustment.KEY_TYPE,
                    mSbn.packageName,
                    toggle.isChecked,
                )
                mOnDismissSettings.onClick(done)
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        }
        done.setText(
            if (enabled)
                R.string.inline_done_button
            else
                R.string.inline_ok_button
        )
        done.setAccessibilityDelegate(mGutsContainer.accessibilityDelegate)
        val toggleWrapper = findViewById<ViewGroup>(R.id.classification_toggle)
        toggleWrapper.setOnClickListener {
            toggle.performClick()
        }

        findViewById<TextView>(R.id.feature_summary).setText(
            resources.getString(R.string.notification_guts_bundle_summary, mAppName));

        val dismissButton = findViewById<View>(R.id.inline_dismiss)
        dismissButton.setOnClickListener(mOnCloseClickListener)
        dismissButton.visibility = if (dismissButton.hasOnClickListeners() && mIsDismissable)
            VISIBLE
        else
            GONE
    }

    companion object {
        private const val TAG = "BundledNotificationInfo"
    }
}
