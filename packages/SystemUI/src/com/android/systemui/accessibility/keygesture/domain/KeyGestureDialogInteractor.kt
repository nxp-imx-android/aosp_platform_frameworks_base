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

package com.android.systemui.accessibility.keygesture.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.internal.accessibility.util.FrameworkObjectProvider
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.keygesture.domain.model.KeyGestureConfirmInfo
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Encapsulates business logic to interact with the key gesture dialog. */
@SysUISingleton
class KeyGestureDialogInteractor
@Inject
constructor(
    @Application private val context: Context,
    private val repository: AccessibilityShortcutsRepository,
    private val broadcastDispatcher: BroadcastDispatcher,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Main private val handler: Handler,
) {
    /** Emits whenever a launch key gesture dialog broadcast is received. */
    val keyGestureConfirmDialogRequest: Flow<KeyGestureConfirmInfo?> =
        broadcastDispatcher
            .broadcastFlow(
                filter = IntentFilter().apply { addAction(ACTION) },
                user = UserHandle.SYSTEM,
                flags = Context.RECEIVER_NOT_EXPORTED,
            ) { intent, _ ->
                intent
            }
            .map { intent -> processDialogRequest(intent) }

    fun onPositiveButtonClick(targetName: String) {
        repository.enableShortcutsForTargets(targetName)
    }

    fun performTtsPromptForText(text: CharSequence): TtsPrompt {
        return TtsPrompt(context, handler, FrameworkObjectProvider(), text)
    }

    private suspend fun processDialogRequest(intent: Intent): KeyGestureConfirmInfo? {
        return withContext(backgroundDispatcher) {
            val keyGestureType = intent.getIntExtra(KeyGestureEventConstants.KEY_GESTURE_TYPE, 0)
            val targetName = intent.getStringExtra(KeyGestureEventConstants.TARGET_NAME)
            val metaState = intent.getIntExtra(KeyGestureEventConstants.META_STATE, 0)
            val keyCode = intent.getIntExtra(KeyGestureEventConstants.KEY_CODE, 0)

            if (isInvalidDialogRequest(keyGestureType, metaState, keyCode, targetName)) {
                null
            } else {
                val titleToContent =
                    repository.getTitleToContentForKeyGestureDialog(
                        keyGestureType,
                        metaState,
                        keyCode,
                        targetName as String,
                    )
                if (titleToContent == null) {
                    null
                } else {
                    KeyGestureConfirmInfo(
                        keyGestureType,
                        titleToContent.first,
                        titleToContent.second,
                        targetName,
                        repository.getActionKeyIconResId(),
                    )
                }
            }
        }
    }

    private fun isInvalidDialogRequest(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String?,
    ): Boolean {
        return targetName.isNullOrEmpty() || keyGestureType == 0 || metaState == 0 || keyCode == 0
    }

    companion object {
        @VisibleForTesting
        const val ACTION = "com.android.systemui.action.LAUNCH_KEY_GESTURE_CONFIRM_DIALOG"
    }
}
