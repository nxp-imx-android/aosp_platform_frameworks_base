/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *st
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.notifications.intelligence.rules.ui.composable

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders a fullscreen page to select 1 or more people matching a search string. */
@Composable
fun PeopleChoiceScreen(
    viewState: RulesScreenViewState.EditField.People,
    onDismissRequest: () -> Unit,
) {
    val viewModel = viewState.viewModel
    val contentResolver = LocalContext.current.contentResolver

    val initialSelection =
        when (val people = viewModel.rule.filter.people) {
            is RuleValue.Specified -> people.value.people
            is RuleValue.Ambiguous -> emptyList()
            null -> emptyList()
        }

    EditScreenWithSearch(
        title = stringResource(R.string.notification_rules_field_people),
        initialSelection = initialSelection,
        onSelectionSaved = { viewState.onPeopleSaved(it) },
        onDismissRequest = onDismissRequest,
        fetchSearchResults = { query -> viewModel.fetchPeople(query, contentResolver) },
        sortKey = { it.displayLabel },
        uniqueId = { it.id },
        icon = { PersonIcon(it, EditScreenDimens.iconSize, viewModel::loadContactBitmapFromUri) },
        text = { it.displayLabel },
    )
}

@Composable
fun PersonIcon(
    model: PersonModel,
    size: Dp,
    loadContactBitmap: suspend (Uri, Context, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    when (model) {
        is PersonModel.Contact -> ContactIcon(model, size, loadContactBitmap, modifier.size(size))
    }
}

@Composable
private fun ContactIcon(
    model: PersonModel.Contact,
    size: Dp,
    loadContactBitmap: suspend (Uri, Context, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    AsyncUriImage(
        uri = model.photoUri,
        loadBitmap = loadContactBitmap,
        contentDescription = model.displayLabel,
        size = size,
        modifier = modifier.clip(CircleShape),
        // TODO: b/478225883 - Use a better default placeholder, like the first letter of the
        // contact.
        placeholderContent = {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary))
        },
    )
}
