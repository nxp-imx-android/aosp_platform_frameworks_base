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

import android.annotation.Px
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.android.systemui.notifications.intelligence.rules.domain.interactor.ContactsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.InstalledAppsInteractor
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class NotificationRuleEditViewModelImpl
@AssistedInject
constructor(
    private val contactsInteractor: ContactsInteractor,
    private val installedAppsInteractor: InstalledAppsInteractor,
) : NotificationRuleEditViewModel {

    override suspend fun fetchContacts(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<ContactModel> {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return emptyList()
        }
        return contactsInteractor.fetchContacts(searchQuery, contentResolver)
    }

    override suspend fun loadContactBitmapFromUri(
        uri: Uri,
        userContext: Context,
        @Px sizePx: Int,
    ): Bitmap? {
        return contactsInteractor.loadBitmapFromUri(uri, userContext, sizePx)
    }

    override suspend fun fetchInstalledApps(): List<AppModel> {
        return installedAppsInteractor.fetchInstalledApps()
    }

    @AssistedFactory
    interface Factory : NotificationRuleEditViewModel.Factory {
        override fun create(): NotificationRuleEditViewModelImpl
    }
}
