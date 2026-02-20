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

package com.android.systemui.notifications.intelligence.rules.shared.model

import android.net.Uri

/** A model for a contact entry. */
public data class ContactModel(
    /** A URI associated with this contact. Can be used as a unique identifier. */
    val lookupUri: Uri,
    /** The display name for the contact. */
    val name: String,
    /** The URI for fetching the photo of the contact. */
    val photoUri: Uri?,
)

/**
 * Represents the list of contacts inside a rule filter.
 *
 * @param contacts must be non-empty.
 */
public data class ContactsModel(val contacts: List<ContactModel>) {
    init {
        require(contacts.isNotEmpty()) { "Contacts list cannot be empty" }
    }
}
