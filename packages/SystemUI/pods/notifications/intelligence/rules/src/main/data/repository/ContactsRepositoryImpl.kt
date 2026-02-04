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
import android.database.Cursor
import android.provider.ContactsContract
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
            var cursor: Cursor? = null
            // TODO: b/478225883 - Handle work contacts, similar to ValidateNotificationPeople.
            try {
                cursor =
                    contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACT_LOOKUP_PROJECTION,
                        selection,
                        selectionArgs,
                        null, // sortOrder
                    )
                while (cursor != null && cursor.moveToNext()) {
                    val name =
                        cursor.getString(
                            cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                        )
                    foundContacts.add(ContactModel(name = name))
                }
            } catch (e: Throwable) {
                // TODO: b/478225883 - Error logging.
            } finally {
                cursor?.close()
            }
            foundContacts.toList()
        }
    }

    companion object {
        /** The list of fields (columns) to fetch from the contacts table. */
        private val CONTACT_LOOKUP_PROJECTION =
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
    }
}
