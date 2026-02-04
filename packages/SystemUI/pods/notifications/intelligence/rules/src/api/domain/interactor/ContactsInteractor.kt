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

package com.android.systemui.notifications.intelligence.rules.domain.interactor

import android.content.ContentResolver
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel

/** Interactor for everything related to contacts. */
public interface ContactsInteractor {
    /**
     * Fetches all contacts whose name matches [searchQuery].
     *
     * @param contentResolver the content resolver for the current user.
     */
    public suspend fun fetchContacts(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<ContactModel>
}
