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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class ContactsRepositoryImpl
@Inject
constructor(@Background private val backgroundDispatcher: CoroutineDispatcher) :
    ContactsRepository {
    override suspend fun fetchContacts(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<ContactModel> {
        return withContext(backgroundDispatcher) {
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$searchQuery%")

            val foundContacts = mutableListOf<ContactModel>()
            // TODO: b/478225883 - Handle work contacts, similar to ValidateNotificationPeople.
            try {
                contentResolver
                    .query(
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACT_LOOKUP_PROJECTION,
                        selection,
                        selectionArgs,
                        null, // sortOrder
                    )
                    .use { cursor ->
                        while (cursor != null && cursor.moveToNext()) {
                            val id: Long? =
                                cursor.getString(cursor.getColumnIndex(ID_FIELD)).toLongOrNull()
                            val lookupKey: String? =
                                cursor.getString(cursor.getColumnIndex(LOOKUP_KEY_FIELD))
                            val name: String? = cursor.getString(cursor.getColumnIndex(NAME_FIELD))
                            val photoUri: String? =
                                cursor.getString(cursor.getColumnIndex(PHOTO_URI_FIELD))

                            if (id == null || lookupKey == null || name == null) {
                                continue
                            }

                            // TODO: b/478225883 - Add a wrapper around #getLookupUri so it's
                            // testable.
                            val lookupUri =
                                ContactsContract.Contacts.getLookupUri(id, lookupKey) ?: continue
                            foundContacts.add(
                                ContactModel(
                                    lookupUri = lookupUri,
                                    name = name,
                                    photoUri = photoUri?.toUri(),
                                )
                            )
                        }
                    }
            } catch (e: Throwable) {
                // TODO: b/478225883 - Error logging.
            }

            foundContacts.toList()
        }
    }

    companion object {
        private const val ID_FIELD = ContactsContract.Contacts._ID
        private const val LOOKUP_KEY_FIELD = ContactsContract.Contacts.LOOKUP_KEY
        private const val NAME_FIELD = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        private const val PHOTO_URI_FIELD = ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        /** The list of fields (columns) to fetch from the contacts table. */
        private val CONTACT_LOOKUP_PROJECTION =
            arrayOf(ID_FIELD, LOOKUP_KEY_FIELD, NAME_FIELD, PHOTO_URI_FIELD)
    }
}
