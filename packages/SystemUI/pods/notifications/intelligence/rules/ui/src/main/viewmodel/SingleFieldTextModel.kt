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

import android.content.res.Resources
import android.text.Annotation
import android.text.Spanned
import android.text.TextUtils
import com.android.systemui.res.R

/** Represents a single field as text. */
internal data class SingleFieldTextModel(
    val text: String,
    /** True if this text is ambiguous and requires clarification. See [RuleValue.Ambiguous]. */
    val isAmbiguous: Boolean,
    /**
     * The range of the text that represents the actual field values. For example, for the string
     * "from Phone +3 more", [valueFieldRange] should encompass just the "Phone +3 more" part.
     *
     * [onClick] will be associated with this range.
     */
    val valueFieldRange: IntRange?,
    /** Optional clickable behavior. */
    val onClick: (() -> Unit)?,
)

/** Creates text that shows 1 or more selected items. */
internal fun <T> createMultiItemText(
    items: List<T>,
    label: (T) -> String,
    onClick: (() -> Unit)?,
    resources: Resources,
): SingleFieldTextModel {
    check(items.isNotEmpty()) { "Items must be non-empty" }

    val first = label(items[0])
    val spanned =
        if (items.size > 1) {
            val template = resources.getText(R.string.notification_rules_from_multi_items)
            TextUtils.expandTemplate(template, first, (items.size - 1).toString()) as Spanned
        } else {
            val template = resources.getText(R.string.notification_rules_from_single_item)
            TextUtils.expandTemplate(template, first) as Spanned
        }

    return createFieldText(spanned, onClick, isAmbiguous = false)
}

/**
 * Creates text for a rule value that's underspecified. Clicking on the text will let users clarify
 * what items should be selected.
 */
internal fun createAmbiguousText(
    placeholderText: String,
    onClick: () -> Unit,
    resources: Resources,
): SingleFieldTextModel {
    val template = resources.getText(R.string.notification_rules_from_single_item)
    val spanned = TextUtils.expandTemplate(template, placeholderText) as Spanned
    return createFieldText(spanned, onClick, isAmbiguous = true)
}

private fun createFieldText(
    spanned: Spanned,
    onClick: (() -> Unit)?,
    isAmbiguous: Boolean,
): SingleFieldTextModel {
    val annotations: Array<Annotation> = spanned.getSpans(0, spanned.length, Annotation::class.java)

    val valueFieldRange =
        annotations
            .firstOrNull { annotation ->
                annotation.key == "valueField" && annotation.value.toBoolean()
            }
            ?.let { spanned.getSpanStart(it) until spanned.getSpanEnd(it) }
    // TODO: b/478225883 - Log if value field range wasn't found in string.

    return SingleFieldTextModel(
        text = spanned.toString(),
        valueFieldRange = valueFieldRange,
        onClick = onClick,
        isAmbiguous = isAmbiguous,
    )
}
