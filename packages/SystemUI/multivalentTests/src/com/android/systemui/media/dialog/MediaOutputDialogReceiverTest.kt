/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog

import android.content.Intent
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.flags.Flags
import com.android.settingslib.media.MediaOutputConstants
import com.android.systemui.SysuiTestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaOutputDialogReceiverTest : SysuiTestCase() {

    private lateinit var mediaOutputDialogReceiver: MediaOutputDialogReceiver
    private val mockMediaOutputDialogManager: MediaOutputDialogManager = mock()

    @Before
    fun setup() {
        mediaOutputDialogReceiver = MediaOutputDialogReceiver(mockMediaOutputDialogManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun launchMediaOutputDialog_extraPackageName_dialogFactoryCalled() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
            putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, context.packageName)
        }
        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, times(1))
            .createAndShow(
                packageName = eq(context.packageName),
                aboveStatusBar = eq(false),
                view = anyOrNull(),
                userHandle = anyOrNull(),
                token = anyOrNull(),
                useSystemColors = eq(true),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun launchMediaOutputDialog_wrongExtraKey_dialogFactoryNotCalled() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
            putExtra("Wrong Package Name Key", context.packageName)
        }
        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, never())
            .createAndShow(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun launchMediaOutputDialog_noExtra_dialogFactoryNotCalled() {
        val intent = Intent(MediaOutputConstants.ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG)
        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, never())
            .createAndShow(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun unknownAction_extraPackageName_factoriesNotCalled() {
        val intent = Intent("Unknown Action").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(MediaOutputConstants.EXTRA_PACKAGE_NAME, context.packageName)
        }
        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, never())
            .createAndShow(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MEDIA_OUTPUT_SWITCHER_ENTRY_POINT_THEMING)
    fun unknownActionAnd_noExtra_factoriesNotCalled() {
        val intent = Intent("Unknown Action")
        mediaOutputDialogReceiver.onReceive(context, intent)

        verify(mockMediaOutputDialogManager, never())
            .createAndShow(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
    }
}
