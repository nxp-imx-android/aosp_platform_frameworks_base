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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

/** Creates a model of a full rules text string using the given [appsText] and [contactsText]. */
internal fun buildRuleText(
    appsText: SingleFieldTextModel?,
    contactsText: SingleFieldTextModel?,
): RuleDisplayModel {
    val textChunks: List<TextChunk> = buildList {
        // TODO: b/478225883 - Create a string resource for the full rule text.
        add(TextChunk.BasicText("Notifications [TK]"))
        appsText?.let { addAll(it.toTextChunks()) }
        contactsText?.let { addAll(it.toTextChunks()) }
    }
    return RuleDisplayModel(textChunks)
}

/** Transforms a single field (like "from Photos +3 more") into a list of individual text chunks. */
internal fun SingleFieldTextModel.toTextChunks(): List<TextChunk> {
    if (valueFieldRange == null) {
        return listOf(TextChunk.BasicText(text))
    }

    return buildList {
        add(TextChunk.BasicText(text.substring(0 until valueFieldRange.first)))
        add(
            TextChunk.ClickableText(
                text = text.substring(valueFieldRange),
                onClick = onClick,
                isAmbiguous = isAmbiguous,
            )
        )
        add(TextChunk.BasicText(text.substring(valueFieldRange.last + 1)))
    }
        .filterNot {
            // Get rid of any empty strings, which will happen if the onClick range is at the
            // beginning or end of the string
            it == TextChunk.BasicText("")
        }
}
