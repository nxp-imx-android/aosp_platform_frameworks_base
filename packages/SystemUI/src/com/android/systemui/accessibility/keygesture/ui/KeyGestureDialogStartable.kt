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

package com.android.systemui.accessibility.keygesture.ui

import android.hardware.input.KeyGestureEvent
import android.text.Annotation
import android.text.Spanned
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.hardware.input.Flags
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.CoreStartable
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.domain.model.KeyGestureConfirmInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dialog.ui.composable.AlertDialogContent
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class KeyGestureDialogStartable
@Inject
constructor(
    private val interactor: KeyGestureDialogInteractor,
    private val dialogFactory: SystemUIDialogFactory,
    @Application private val mainScope: CoroutineScope,
) : CoreStartable {
    @VisibleForTesting var currentDialog: ComponentSystemUIDialog? = null

    override fun start() {
        if (!Flags.enableTalkbackAndMagnifierKeyGestures()) {
            return
        }

        mainScope.launch {
            interactor.keyGestureConfirmDialogRequest.collectLatest { keyGestureConfirmInfo ->
                createDialog(keyGestureConfirmInfo)
            }
        }
    }

    private fun createDialog(keyGestureConfirmInfo: KeyGestureConfirmInfo?) {
        if (keyGestureConfirmInfo == null) {
            dismissDialog()
            return
        }
        dismissDialog()

        currentDialog =
            dialogFactory.create { dialog ->
                PlatformTheme {
                    AlertDialogContent(
                        title = { Text(text = keyGestureConfirmInfo.title) },
                        content = {
                            TextWithIcon(
                                keyGestureConfirmInfo.contentText,
                                keyGestureConfirmInfo.actionKeyIconResId,
                            )
                        },
                        negativeButton = {
                            PlatformOutlinedButton(onClick = { dialog.dismiss() }) {
                                Text(stringResource(id = android.R.string.cancel))
                            }
                        },
                        positiveButton = {
                            PlatformButton(
                                onClick = {
                                    interactor.onPositiveButtonClick(
                                        keyGestureConfirmInfo.targetName
                                    )
                                    dialog.dismiss()
                                }
                            ) {
                                Text(
                                    stringResource(
                                        id =
                                            R.string
                                                .accessibility_key_gesture_dialog_positive_button_text
                                    )
                                )
                            }
                        },
                    )
                }
            }

        currentDialog?.let { dialog ->
            dialog.show()

            // We need to announce the text for the TalkBack dialog.
            if (
                keyGestureConfirmInfo.keyGestureType ==
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER
            ) {
                val tts = interactor.performTtsPromptForText(keyGestureConfirmInfo.contentText)
                dialog.setOnDismissListener { tts.dismiss() }
            }
        }
    }

    private fun dismissDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun buildAnnotatedStringFromResource(resourceText: CharSequence): AnnotatedString {
        // `resourceText` is an instance of SpannableStringBuilder, so we can cast it to a Spanned.
        val spanned = resourceText as? Spanned ?: return AnnotatedString(resourceText.toString())

        // get all the annotation spans from the text
        val annotations = spanned.getSpans(0, spanned.length, Annotation::class.java)

        return buildAnnotatedString {
            var startIndex = 0
            for (annotationSpan in annotations) {
                if (annotationSpan.key == "id" && annotationSpan.value == "action_key_icon") {
                    val annotationStart = spanned.getSpanStart(annotationSpan)
                    val annotationEnd = spanned.getSpanEnd(annotationSpan)
                    append(spanned.substring(startIndex, annotationStart))
                    appendInlineContent(ICON_INLINE_CONTENT_ID)
                    startIndex = annotationEnd
                }
            }

            if (startIndex < spanned.length) {
                append(spanned.substring(startIndex))
            }
        }
    }

    @Composable
    private fun TextWithIcon(text: CharSequence, modifierKeyIconResId: Int) {
        // TODO: b/419026315 - Update the icon drawable based on keyboard device.
        val inlineContentMap =
            mapOf(
                ICON_INLINE_CONTENT_ID to
                    InlineTextContent(
                        Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        Icon(
                            painter = painterResource(modifierKeyIconResId),
                            contentDescription = null, // decorative icon
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            )

        Text(buildAnnotatedStringFromResource(text), inlineContent = inlineContentMap)
    }

    companion object {
        const val ICON_INLINE_CONTENT_ID = "iconInlineContentId"
    }
}
