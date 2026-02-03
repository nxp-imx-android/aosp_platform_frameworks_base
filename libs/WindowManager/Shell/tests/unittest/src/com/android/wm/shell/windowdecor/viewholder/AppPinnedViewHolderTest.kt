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

package com.android.wm.shell.windowdecor.viewholder

import android.graphics.Color
import android.testing.AndroidTestingRunner
import android.view.View
import android.widget.ImageButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [AppPinnedViewHolder]
 *
 * atest WMShellUnitTests:AppPinnedViewHolderTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class AppPinnedViewHolderTest : ShellTestCase() {

    private val mockView = mock<View>()
    private val mockTaskResourceLoader = mock<WindowDecorTaskResourceLoader>()
    private val mockImageButton = mock<ImageButton>()
    private val mockCaptionView = mock<View>()
    private val mockTouchListener = mock<View.OnTouchListener>()
    private val mockMotionListener = mock<View.OnGenericMotionListener>()
    private val mockOpenSettings = mock<View.OnClickListener>()
    private val mockCloseWindow = mock<View.OnClickListener>()

    private fun createViewHolder() =
        AppPinnedViewHolder(
            mockView,
            mockTaskResourceLoader,
            mockTouchListener,
            mockMotionListener,
            mockOpenSettings,
            mockCloseWindow,
        )

    @Before
    fun setup() {
        whenever(mockView.context).thenReturn(mContext)
        whenever(mockView.requireViewById<View>(R.id.pinned_caption)).thenReturn(mockCaptionView)
        whenever(mockView.requireViewById<View>(R.id.caption_handle)).thenReturn(mockView)
        whenever(mockView.requireViewById<ImageButton>(R.id.settings_button))
            .thenReturn(mockImageButton)
        whenever(mockView.requireViewById<ImageButton>(R.id.close_window))
            .thenReturn(mockImageButton)
    }

    @Test
    fun darkBackground() {
        val viewHolder = spy(createViewHolder())
        val taskInfo = WindowDecorationTestHelper.createOpaqueAppHeaderTask()
        viewHolder.bindData(AppPinnedViewHolder.AppPinnedData(taskInfo))

        val darkColor = dynamicDarkColorScheme(mContext).surfaceContainerHigh.toArgb()

        verify(mockCaptionView).setBackgroundColor(darkColor)
    }

    @Test
    fun transparentBackground() {
        val viewHolder = spy(createViewHolder())
        val taskInfo = WindowDecorationTestHelper.createCustomAppHeaderTask()
        viewHolder.bindData(AppPinnedViewHolder.AppPinnedData(taskInfo))

        verify(mockCaptionView).setBackgroundColor(Color.TRANSPARENT)
    }

    @Test
    fun bindsAppName() {
        val appName = "Test App"
        whenever(mockTaskResourceLoader.getNameAndHeaderIcon(any(), any())).thenAnswer { it ->
            val callback = it.getArgument<(CharSequence, android.graphics.Bitmap) -> Unit>(1)
            callback.invoke(appName, mock())
        }
        val viewHolder = createViewHolder()
        val taskInfo = WindowDecorationTestHelper.createCustomAppHeaderTask()
        viewHolder.bindData(AppPinnedViewHolder.AppPinnedData(taskInfo))

        verify(mockImageButton).contentDescription =
            mContext.getString(R.string.close_button_text, appName)
    }
}
