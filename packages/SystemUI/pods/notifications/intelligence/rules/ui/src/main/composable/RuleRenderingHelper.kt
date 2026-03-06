/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextChunk
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextStyles

/** Transforms a series of text chunks into a single annotated string that can be rendered. */
internal fun buildAnnotatedString(
    textChunks: List<TextChunk>,
    textStyles: TextStyles,
): AnnotatedString {
    return buildAnnotatedString { textChunks.forEach { appendTextChunk(it, textStyles) } }
}

private fun AnnotatedString.Builder.appendTextChunk(chunk: TextChunk, textStyles: TextStyles) {
    when (chunk) {
        is TextChunk.BasicText -> {
            append(chunk.text)
        }
        is TextChunk.FieldValueText -> {
            withStyle(style = textStyles.specifiedValueSpanStyle) { append(chunk.text) }
        }
        is TextChunk.ClickableText -> {
            appendClickableRegion(
                text = chunk.text,
                onClick = chunk.onClick,
                isAmbiguous = chunk.isAmbiguous,
                textStyles = textStyles,
            )
        }
    }
}

/** Appends a piece of clickable text. */
private fun AnnotatedString.Builder.appendClickableRegion(
    text: String,
    onClick: () -> Unit,
    isAmbiguous: Boolean,
    textStyles: TextStyles,
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = text,
            styles =
                TextLinkStyles(
                    style =
                        if (isAmbiguous) textStyles.ambiguousValueSpanStyle
                        else textStyles.specifiedValueSpanStyle
                ),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}
