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
import android.app.Notification
import android.app.Notification.BridgedNotificationMetadata
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.widget.TextView
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider

class BridgedNotificationInfo(context: Context?, attrs: AttributeSet?) :
    NotificationInfo(context, attrs) {

    override fun bindInlineControls() {
        val done = findViewById<TextView>(R.id.done)
        done.setOnClickListener { mOnDismissSettings.onClick(done) }

        val dismissButton = findViewById<TextView>(R.id.inline_dismiss)
        dismissButton.setOnClickListener(mOnCloseClickListener)

        val openAssociatedDeviceSettingsButton =
            findViewById<TextView>(R.id.bridged_open_associated_device_settings)
        openAssociatedDeviceSettingsButton.setOnClickListener {
            var intent =
                Intent(Notification.ACTION_BRIDGED_NOTIFICATION_PREFERENCES)
            intent.setPackage(mSbn.getPackageName())
            context.sendBroadcastAsUser(intent, UserHandle.CURRENT)
        }
    }

    override fun bindNotification(
        pm: PackageManager,
        iNotificationManager: INotificationManager,
        appIconProvider: AppIconProvider,
        iconStyleProvider: NotificationIconStyleProvider,
        onUserInteractionCallback: OnUserInteractionCallback,
        channelEditorDialogController: ChannelEditorDialogController,
        packageDemotionInteractor: PackageDemotionInteractor,
        pkg: String,
        ranking: NotificationListenerService.Ranking,
        sbn: StatusBarNotification,
        entry: NotificationEntry?,
        entryAdapter: EntryAdapter?,
        onSettingsClick: OnSettingsClickListener?,
        onAppSettingsClick: OnAppSettingsClickListener?,
        feedbackClickListener: OnFeedbackClickListener?,
        uiEventLogger: UiEventLogger,
        isDeviceProvisioned: Boolean,
        isNonblockable: Boolean,
        isDismissable: Boolean,
        wasShownHighPriority: Boolean,
        metricsLogger: MetricsLogger,
        onCloseClick: OnClickListener?,
    ) {
        super.bindNotification(
            pm,
            iNotificationManager,
            appIconProvider,
            iconStyleProvider,
            onUserInteractionCallback,
            channelEditorDialogController,
            packageDemotionInteractor,
            pkg,
            ranking,
            sbn,
            entry,
            entryAdapter,
            onSettingsClick,
            onAppSettingsClick,
            feedbackClickListener,
            uiEventLogger,
            isDeviceProvisioned,
            isNonblockable,
            isDismissable,
            wasShownHighPriority,
            metricsLogger,
            onCloseClick,
        )
        val bridgedOpenSettingsButton =
            findViewById<TextView>(R.id.bridged_open_associated_device_settings)
        var deviceTypeString = ""
        when (sbn.getNotification()?.getBridgedNotificationMetadata()?.getOriginDeviceType()) {
            Notification.BridgedNotificationMetadata.BRIDGED_METADATA_TYPE_PHONE ->
                deviceTypeString = mContext.getString(R.string.bridged_device_type_phone)
            Notification.BridgedNotificationMetadata.BRIDGED_METADATA_TYPE_TABLET ->
                deviceTypeString = mContext.getString(R.string.bridged_device_type_tablet)
            Notification.BridgedNotificationMetadata.BRIDGED_METADATA_TYPE_LAPTOP ->
                deviceTypeString = mContext.getString(R.string.bridged_device_type_laptop)
            Notification.BridgedNotificationMetadata.BRIDGED_METADATA_TYPE_WATCH ->
                deviceTypeString = mContext.getString(R.string.bridged_device_type_watch)
            Notification.BridgedNotificationMetadata.BRIDGED_METADATA_TYPE_TV ->
                deviceTypeString = mContext.getString(R.string.bridged_device_type_tv)
        }
        bridgedOpenSettingsButton.setText(
            mContext.getString(
                R.string.inline_bridged_open_associated_device_settings,
                deviceTypeString,
            )
        )
        val bridgedLabelTextView = findViewById<TextView>(R.id.bridged_label)
        bridgedLabelTextView.setText(
            mContext.getString(R.string.notification_bridged_title, deviceTypeString)
        )

        val bridgedSummaryTextView = findViewById<TextView>(R.id.bridged_summary)
        bridgedSummaryTextView.setText(
            // TODO(b/438827600): Use the remote device's name once it's available in the metadata
            mContext.getString(R.string.notification_channel_summary_bridged, deviceTypeString)
        )
        // TODO(b/438827600): Set click listener for opening settings on the remote device
    }

    companion object {
        private const val TAG = "BridgedNotificationInfo"
    }
}
