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

package com.android.systemui.media.controls.ui.util

import androidx.recyclerview.widget.DiffUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.media.controls.ui.viewmodel.MediaCommonViewModel
import com.android.systemui.media.controls.ui.viewmodel.mediaControlViewModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaDiffUtilTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @Test
    fun newMediaControlAdded() {
        val mediaControl = createMediaControl(InstanceId.fakeInstanceId(123), true)
        val oldList = listOf<MediaCommonViewModel>()
        val newList = listOf(mediaControl)
        val mediaLoadedCallback = MediaViewModelCallback(oldList, newList)
        val mediaLoadedListUpdateCallback =
            MediaViewModelListUpdateCallback(
                oldList,
                newList,
                { commonViewModel, _ -> assertThat(commonViewModel).isEqualTo(mediaControl) },
                { commonViewModel, _ -> fail("Unexpected to update $commonViewModel") },
                { fail("Unexpected to remove $it") },
                { commonViewModel, _, _ -> fail("Unexpected to move $commonViewModel ") },
            )

        DiffUtil.calculateDiff(mediaLoadedCallback).dispatchUpdatesTo(mediaLoadedListUpdateCallback)
    }

    @Test
    fun updateMediaControl_contentChanged() {
        val mediaControl = createMediaControl(InstanceId.fakeInstanceId(123), true)
        val oldList = listOf(mediaControl)
        val newList = listOf(mediaControl.copy(immediatelyUpdateUi = false))
        val mediaLoadedCallback = MediaViewModelCallback(oldList, newList)
        val mediaLoadedListUpdateCallback =
            MediaViewModelListUpdateCallback(
                oldList,
                newList,
                { commonViewModel, _ -> fail("Unexpected to add $commonViewModel") },
                { commonViewModel, _ -> assertThat(commonViewModel).isNotEqualTo(mediaControl) },
                { fail("Unexpected to remove $it") },
                { commonViewModel, _, _ -> fail("Unexpected to move $commonViewModel ") },
            )

        DiffUtil.calculateDiff(mediaLoadedCallback).dispatchUpdatesTo(mediaLoadedListUpdateCallback)
    }

    @Test
    fun mediaControlMoved() {
        val mediaControl1 = createMediaControl(InstanceId.fakeInstanceId(123), true)
        val mediaControl2 = createMediaControl(InstanceId.fakeInstanceId(456), false)
        val oldList = listOf(mediaControl1, mediaControl2)
        val newList = listOf(mediaControl2, mediaControl1)
        val mediaLoadedCallback = MediaViewModelCallback(oldList, newList)
        val mediaLoadedListUpdateCallback =
            MediaViewModelListUpdateCallback(
                oldList,
                newList,
                { commonViewModel, _ -> fail("Unexpected to add $commonViewModel") },
                { commonViewModel, _ -> fail("Unexpected to update $commonViewModel") },
                { fail("Unexpected to remove $it") },
                { commonViewModel, _, _ -> assertThat(commonViewModel).isEqualTo(mediaControl1) },
            )

        DiffUtil.calculateDiff(mediaLoadedCallback).dispatchUpdatesTo(mediaLoadedListUpdateCallback)
    }

    @Test
    fun mediaControlRemoved() {
        val mediaControl = createMediaControl(InstanceId.fakeInstanceId(123), true)
        val oldList = listOf(mediaControl)
        val newList = listOf<MediaCommonViewModel>()
        val mediaLoadedCallback = MediaViewModelCallback(oldList, newList)
        val mediaLoadedListUpdateCallback =
            MediaViewModelListUpdateCallback(
                oldList,
                newList,
                { commonViewModel, _ -> fail("Unexpected to add $commonViewModel") },
                { commonViewModel, _ -> fail("Unexpected to update $commonViewModel") },
                { commonViewModel -> assertThat(commonViewModel).isEqualTo(mediaControl) },
                { commonViewModel, _, _ -> fail("Unexpected to move $commonViewModel ") },
            )

        DiffUtil.calculateDiff(mediaLoadedCallback).dispatchUpdatesTo(mediaLoadedListUpdateCallback)
    }

    private fun createMediaControl(
        instanceId: InstanceId,
        immediatelyUpdateUi: Boolean,
    ): MediaCommonViewModel.MediaControl {
        return MediaCommonViewModel.MediaControl(
            instanceId = instanceId,
            immediatelyUpdateUi = immediatelyUpdateUi,
            controlViewModel = kosmos.mediaControlViewModel,
            onAdded = {},
            onRemoved = {},
            onUpdated = {},
        )
    }
}
