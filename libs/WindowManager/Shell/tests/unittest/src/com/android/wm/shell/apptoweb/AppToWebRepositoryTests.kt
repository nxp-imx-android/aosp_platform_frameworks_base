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

package com.android.wm.shell.apptoweb

import android.app.assist.AssistContent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.test.mock.MockContext
import android.testing.AndroidTestingRunner
import androidx.core.net.toUri
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.createTaskInfo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for [AppToWebRepository].
 *
 * Build/Install/Run: atest WMShellUnitTests:AppToWebRepositoryTests
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class AppToWebRepositoryTests : ShellTestCase() {

    @Mock private lateinit var mockAssistContentRequester: AssistContentRequester
    @Mock private lateinit var mockGenericLinksParser: AppToWebGenericLinksParser
    @Mock private lateinit var mockAssistContent: AssistContent
    @Mock private lateinit var mockContext: MockContext
    @Mock private lateinit var mockPackageManager: PackageManager

    private lateinit var mockInit: AutoCloseable
    private lateinit var appToWebRepository: AppToWebRepository
    private val taskInfo = createTaskInfo().apply {
        taskId = TEST_TASK_ID
        baseActivity = ComponentName("appToWeb", "testActivity")
    }
    private val resolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo()
        activityInfo.packageName = "appToWeb"
        activityInfo.name = "testActivity"
    }

    @Before
    fun setUp() {
        mockInit = MockitoAnnotations.openMocks(this)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.resolveActivityAsUser(any(), anyInt(), anyInt()))
            .thenReturn(resolveInfo)
        appToWebRepository = AppToWebRepository(
            mockContext,
            TEST_TASK_ID,
            mockAssistContentRequester,
            mockGenericLinksParser
        )
    }

    @After
    fun tearDown() {
        mockInit.close()
    }

    @Test
    fun capturedLink_unavailableAfterUse() {
        appToWebRepository.setCapturedLink(
            link = TEST_CAPTURED_URI,
            timeStamp = 100
        )
        appToWebRepository.onCapturedLinkUsed()
        assertFalse(appToWebRepository.isCapturedLinkAvailable())
    }

    @Test
    fun capturedLink_sameCapturedLinkNotSavedAgain() {
        val timeStamp = 100L
        appToWebRepository.setCapturedLink(TEST_CAPTURED_URI, timeStamp)
        appToWebRepository.onCapturedLinkUsed()
        // Set the same captured link (same timeStamp)
        appToWebRepository.setCapturedLink(TEST_CAPTURED_URI, timeStamp)
        assertFalse(appToWebRepository.isCapturedLinkAvailable())
    }

    @Test
    fun webUri_prioritizedOverCapturedAndGenericLinks() = runTest {
        // Set captured link
        appToWebRepository.setCapturedLink(
            link = TEST_CAPTURED_URI,
            timeStamp = 100
        )
        // Set web Uri
        whenever(mockAssistContent.getSessionWebUri()).thenReturn(TEST_WEB_URI)
        mockAssistContent.webUri = TEST_WEB_URI
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_WEB_URI)
    }

    @Test
    fun capturedLink_prioritizedOverGenericLink() = runTest {
        // Set captured link
        appToWebRepository.setCapturedLink(
            link = TEST_CAPTURED_URI,
            timeStamp = 100
        )
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_CAPTURED_URI)
    }

    @Test
    fun genericLink_utilizedWhenAllOtherLinksAreUnavailable() = runTest {
        // Set generic link
        whenever(mockGenericLinksParser.getGenericLink(any())).thenReturn(TEST_GENERIC_LINK)

        assertAppToWebIntent(TEST_GENERIC_LINK.toUri())
    }

    @Test
    fun webUri_usedWhenIsBrowserApp() = runTest {
        // Set web Uri
        whenever(mockAssistContent.getSessionWebUri()).thenReturn(TEST_WEB_URI)

        assertAppToWebIntent(
            expectedUri = TEST_WEB_URI,
            isBrowserApp = true
        )
    }

    private suspend fun assertAppToWebIntent(expectedUri: Uri, isBrowserApp: Boolean = false) {
        whenever(
            mockAssistContentRequester.requestAssistContent(
                anyInt(),
                any()
            )
        ).thenAnswer { invocation ->
            val callback = invocation.arguments[1] as AssistContentRequester.Callback
            callback.onAssistContentAvailable(mockAssistContent)
        }

        assertEquals(
            expectedUri,
            appToWebRepository.getAppToWebIntent(
                taskInfo = taskInfo,
                isBrowserApp = isBrowserApp,
            )?.data
        )
    }

    private companion object {
        private const val TEST_TASK_ID = 1
        private val TEST_WEB_URI = Uri.parse("http://www.youtube.com")
        private val TEST_CAPTURED_URI = Uri.parse("www.google.com")
        private const val TEST_GENERIC_LINK = "http://www.gmail.com"
    }
}
