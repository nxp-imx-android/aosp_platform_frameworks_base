/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.Context
import android.content.DialogInterface
import android.content.packageManager
import android.content.pm.PackageManager
import android.view.View
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModel
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.NORMAL_PACKAGE
import com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel.ScreenRecordChipViewModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.mockSystemUIDialogFactory
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * utilities used by other tests
 * TODO(b/452147661): move these helpers to [OngoingActivityChipsWithNotifsViewModelTest]
 */
class OngoingActivityChipsViewModelTest {
    companion object {
        /**
         * Assuming that the click listener in [latest] opens a dialog, this fetches the action
         * associated with the positive button, which we assume is the "Stop sharing" action.
         */
        fun getStopActionFromDialog(
            latest: OngoingActivityChipModel?,
            chipView: View,
            expandable: Expandable,
            dialog: SystemUIDialog,
            kosmos: Kosmos,
        ): DialogInterface.OnClickListener {
            // Capture the action that would get invoked when the user clicks "Stop" on the dialog
            lateinit var dialogStopAction: DialogInterface.OnClickListener
            Mockito.doAnswer {
                    val delegate = it.arguments[0] as SystemUIDialog.Delegate
                    delegate.beforeCreate(dialog, /* savedInstanceState= */ null)

                    val stopActionCaptor = argumentCaptor<DialogInterface.OnClickListener>()
                    verify(dialog).setPositiveButton(any(), stopActionCaptor.capture())
                    dialogStopAction = stopActionCaptor.firstValue

                    return@doAnswer dialog
                }
                .whenever(kosmos.mockSystemUIDialogFactory)
                .create(any<SystemUIDialog.Delegate>(), any<Context>())
            whenever(kosmos.packageManager.getApplicationInfo(eq(NORMAL_PACKAGE), any<Int>()))
                .thenThrow(PackageManager.NameNotFoundException())

            if (StatusBarChipsModernization.isEnabled) {
                val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
                (clickBehavior as OngoingActivityChipModel.ClickBehavior.ExpandAction).onClick(
                    expandable
                )
            } else {
                val clickListener =
                    ((latest as OngoingActivityChipModel.Active).onClickListenerLegacy)
                clickListener!!.onClick(chipView)
            }

            return dialogStopAction
        }

        fun assertIsScreenRecordChip(latest: OngoingActivityChipModel?) {
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key)
                .isEqualTo(ScreenRecordChipViewModel.KEY)
            val icon =
                ((latest.icon) as OngoingActivityChipModel.ChipIcon.SingleColorIcon).impl
                    as Icon.Resource
            assertThat(icon.resId).isEqualTo(R.drawable.ic_screenrecord)
        }

        fun assertIsShareToAppChip(latest: OngoingActivityChipModel?) {
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key)
                .isEqualTo(ShareToAppChipViewModel.KEY)
            val icon =
                ((latest.icon) as OngoingActivityChipModel.ChipIcon.SingleColorIcon).impl
                    as Icon.Resource
            assertThat(icon.resId).isEqualTo(R.drawable.ic_present_to_all)
        }

        fun assertIsCallChip(
            latest: OngoingActivityChipModel?,
            notificationKey: String,
            context: Context,
        ) {
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key)
                .isEqualTo("${CallChipViewModel.KEY_PREFIX}$notificationKey")

            if (StatusBarConnectedDisplays.isEnabled) {
                assertNotificationIcon(latest, notificationKey)
            } else {
                val contentDescription =
                    if (latest.icon is OngoingActivityChipModel.ChipIcon.SingleColorIcon) {
                        ((latest.icon) as OngoingActivityChipModel.ChipIcon.SingleColorIcon)
                            .impl
                            .contentDescription
                    } else {
                        (latest.icon as OngoingActivityChipModel.ChipIcon.StatusBarView)
                            .contentDescription
                    }
                assertThat(contentDescription.loadContentDescription(context))
                    .contains(context.getString(R.string.ongoing_call_content_description))
            }
        }

        private fun assertNotificationIcon(
            latest: OngoingActivityChipModel?,
            notificationKey: String,
        ) {
            val active = latest as OngoingActivityChipModel.Active
            val notificationIcon =
                active.icon as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon
            assertThat(notificationIcon.notificationKey).isEqualTo(notificationKey)
        }
    }
}
