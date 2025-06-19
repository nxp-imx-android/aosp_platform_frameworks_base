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

import android.app.ActivityManager.RunningTaskInfo
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.IndentingPrintWriter
import androidx.core.net.toUri
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import java.io.PrintWriter
import kotlin.coroutines.suspendCoroutine

/**
 * App-to-Web has the following features: transferring an app session to the web and transferring
 * a web session to the relevant app. To transfer an app session to the web, we utilize
 * three different [Uri]s:
 * 1. webUri: The web URI provided by the app using [AssistContent]
 * 2. capturedLink: The link used to open the app if app was opened by clicking on a link
 * 3. genericLink: The system provided link for the app
 * In order to create the [Intent] to transfer the user from app to the web, the [Uri]s listed above
 * are checked in the given order and the first non-null link is used. When transferring from the
 * web to an app, the [Uri] must be provided by the browser application through [AssistContent].
 *
 * This Repository encapsulates the data stored for the App-to-Web feature for a single task and
 * creates the intents used to open switch between an app or browser session.
 */
class AppToWebRepository(
    private val userContext: Context,
    private val taskId: Int,
    private val assistContentRequester: AssistContentRequester,
    private val genericLinksParser: AppToWebGenericLinksParser,
) {
    private var capturedLink: CapturedLink? = null

    /** Sets the captured link if a new link is provided. */
    fun setCapturedLink(link: Uri, timeStamp: Long) {
        if (capturedLink?.timeStamp == timeStamp) return
        capturedLink = CapturedLink(link, timeStamp)
    }

    /**
     * Checks if [capturedLink] is available (non-null and has not been used) to use for switching
     * to browser session.
     */
    fun isCapturedLinkAvailable(): Boolean {
        val link = capturedLink ?: return false
        return !link.used
    }

    /** Sets the captured link as used. */
    fun onCapturedLinkUsed() {
        capturedLink?.setUsed()
    }

    /**
     * Retrieves the latest webUri and genericLink. If the task requesting the intent
     * [isBrowserApp], intent is created to switch to application if link was provided by browser
     * app and a relevant application exists to host the app. Otherwise, returns intent to switch
     * to browser if webUri, capturedLink, or genericLink is available.
     *
     * Note that the capturedLink should be updated separately using [setCapturedLink]
     *
     */
    suspend fun getAppToWebIntent(taskInfo: RunningTaskInfo, isBrowserApp: Boolean): Intent? {
        ProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "AppToWebRepository: Updating browser links for task $taskId"
        )
        val assistContent = assistContentRequester.requestAssistContent(taskInfo.taskId)
        val webUri = assistContent?.getSessionWebUri()
        return if (isBrowserApp) {
            getAppIntent(webUri)
        } else {
            getBrowserIntent(webUri, getGenericLink(taskInfo))
        }
    }

    private suspend fun AssistContentRequester.requestAssistContent(taskId: Int): AssistContent? =
        suspendCoroutine { continuation ->
            requestAssistContent(taskId) { continuation.resumeWith(Result.success(it)) }
        }

    /** Returns the browser link associated with the given application if available. */
    private fun getBrowserIntent(webUri: Uri?, genericLink: Uri?): Intent? {
        val browserLink = webUri ?: if (isCapturedLinkAvailable()) {
            capturedLink?.uri
        } else {
            genericLink
        } ?: return null
        return getBrowserIntent(browserLink, userContext.packageManager, userContext.userId)
    }

    private fun getAppIntent(webUri: Uri?): Intent? {
        webUri ?: return null
        return getAppIntent(
            uri = webUri,
            packageManager = userContext.packageManager,
            userId = userContext.userId
        )
    }


    private fun getGenericLink(taskInfo: RunningTaskInfo): Uri? {
        ProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "AppToWebRepository: Updating generic link for task %d",
            taskId
        )
        val baseActivity = taskInfo.baseActivity ?: return null
        return genericLinksParser.getGenericLink(baseActivity.packageName)?.toUri()
    }

    /** Dumps the repository's current state. */
    fun dump(originalWriter: PrintWriter, prefix: String) {
        val pw = IndentingPrintWriter(originalWriter, " ", prefix)
        pw.println("AppToWebRepository for task#$taskId")
        pw.increaseIndent()
        pw.println("CapturedLink=$capturedLink")
    }

    /** Encapsulates data associated with a captured link. */
    private data class CapturedLink(val uri: Uri, val timeStamp: Long) {

        /** Signifies if captured link has already been used, making it invalid. */
        var used = false

        /** Sets the captured link as used. */
        fun setUsed() {
            used = true
        }
    }
}
