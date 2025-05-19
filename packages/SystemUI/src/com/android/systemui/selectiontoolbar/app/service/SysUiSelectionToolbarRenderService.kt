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

package com.android.systemui.selectiontoolbar.app.service

import android.service.selectiontoolbar.RemoteSelectionToolbar
import android.service.selectiontoolbar.SelectionToolbarRenderService
import android.util.IndentingPrintWriter
import android.util.Slog
import android.view.selectiontoolbar.ISelectionToolbarCallback
import android.view.selectiontoolbar.SelectionToolbarManager
import android.view.selectiontoolbar.ShowInfo
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.UUID

class SysUiSelectionToolbarRenderService : SelectionToolbarRenderService() {
    // TODO(b/215497659): handle remove if the client process dies.
    // Only show one toolbar, dismiss the old ones and remove from cache
    private val toolbarCache = mutableMapOf<Int, Pair<Long, RemoteSelectionToolbar>>()

    override fun onShow(
        callingUid: Int,
        showInfo: ShowInfo,
        callbackWrapper: RemoteCallbackWrapper,
    ) {
        if (isToolbarShown(callingUid, showInfo)) {
            Slog.e(TAG, "Do not allow multiple toolbar for the app.")
            callbackWrapper.onError(
                ISelectionToolbarCallback.ERROR_DO_NOT_ALLOW_MULTIPLE_TOOL_BAR,
                showInfo.sequenceNumber,
            )
            return
        }
        val widgetToken =
            if (showInfo.widgetToken == SelectionToolbarManager.NO_TOOLBAR_ID) {
                UUID.randomUUID().mostSignificantBits
            } else {
                showInfo.widgetToken
            }
        if (!toolbarCache.containsKey(callingUid)) {
            val toolbar =
                RemoteSelectionToolbar(
                    callingUid,
                    this,
                    widgetToken,
                    showInfo,
                    callbackWrapper,
                    ::transferTouch,
                    ::onPasteAction,
                )
            toolbarCache[callingUid] = widgetToken to toolbar
        }
        Slog.v(TAG, "onShow() for $widgetToken")
        val toolbarPair = toolbarCache[callingUid]!!
        if (toolbarPair.first == widgetToken) {
            toolbarPair.second.show(showInfo)
        } else {
            Slog.w(TAG, "onShow() for unknown $widgetToken")
        }
    }

    override fun onHide(widgetToken: Long) {
        getRemoteSelectionToolbarByToken(widgetToken)?.let {
            Slog.v(TAG, "onHide() for $widgetToken")
            it.hide(widgetToken)
        }
    }

    override fun onDismiss(widgetToken: Long) {
        getRemoteSelectionToolbarByToken(widgetToken)?.let {
            Slog.v(TAG, "onDismiss() for $widgetToken")
            it.dismiss(widgetToken)
            removeRemoteSelectionToolbarByToken(widgetToken)
        }
    }

    override fun onUidDied(callingUid: Int) {
        toolbarCache[callingUid]?.let {
            val remoteToolbar = it.second
            remoteToolbar.dismiss(it.first)
            toolbarCache.remove(callingUid)
        }
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<String>) {
        val ipw = IndentingPrintWriter(pw)
        ipw.print("number selectionToolbar: ")
        ipw.println(toolbarCache.size)
        ipw.increaseIndent()
        toolbarCache.forEach {
            it
            val callingUid = it.key
            val widgetToken = it.value.first
            val selectionToolbar = it.value.second
            ipw.print("callingUid: ")
            ipw.println(callingUid)
            ipw.print("widgetToken: ")
            ipw.println(widgetToken)
            selectionToolbar.dump("", ipw)
            ipw.println()
        }
        ipw.decreaseIndent()
    }

    private fun getRemoteSelectionToolbarByToken(widgetToken: Long): RemoteSelectionToolbar? {
        toolbarCache
            .filterValues { it.first == widgetToken }
            .forEach {
                return it.value.second
            }
        return null
    }

    private fun removeRemoteSelectionToolbarByToken(widgetToken: Long) =
        toolbarCache.entries.removeIf { it.value.first == widgetToken }

    /** Only allow one package to create one toolbar. */
    private fun isToolbarShown(uid: Int, showInfo: ShowInfo): Boolean {
        return showInfo.widgetToken != SelectionToolbarManager.NO_TOOLBAR_ID ||
            toolbarCache.contains(uid)
    }

    companion object {
        private const val TAG = "SysUiRemoteToolbarRenderService"
    }
}
