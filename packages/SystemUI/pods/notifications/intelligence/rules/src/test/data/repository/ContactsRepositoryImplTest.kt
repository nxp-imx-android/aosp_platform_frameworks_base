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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.sql.SQLException
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ContactsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    // TODO: b/478225883 - Put ContactsRepository in a test fixture.
    private val Kosmos.underTest by Kosmos.Fixture { ContactsRepositoryImpl(kosmos.testDispatcher) }

    @Test
    fun fetchContacts_nullCursor_returnsEmptyList() =
        kosmos.runTest {
            val contentResolver = mock<ContentResolver>()
            whenever(contentResolver.query(any(), any(), any(), any(), eq(null))).thenReturn(null)

            val result = underTest.fetchContacts(searchQuery = "s", contentResolver)

            assertThat(result).isEmpty()
        }

    @Test
    fun fetchContacts_exception_returnsEmptyList() =
        kosmos.runTest {
            val contentResolver = mock<ContentResolver>()

            doAnswer { _: InvocationOnMock -> throw SQLException() }
                .whenever(contentResolver)
                .query(any(), any(), any(), any(), eq(null))

            val result = underTest.fetchContacts(searchQuery = "s", contentResolver)

            assertThat(result).isEmpty()
        }

    @Test
    fun fetchContacts_emptyCursor_returnsEmptyList_andCursorClosed() =
        kosmos.runTest {
            val cursor = mock<Cursor>()
            whenever(cursor.moveToNext()).thenReturn(false)

            val contentResolver = mock<ContentResolver>()
            whenever(contentResolver.query(any(), any(), any(), any(), eq(null))).thenReturn(cursor)

            val result = underTest.fetchContacts(searchQuery = "s", contentResolver)

            assertThat(result).isEmpty()
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_oneContact_returnsContact_andCursorClosed() =
        kosmos.runTest {
            val cursor = createCursorWithEntries(names = listOf("Sys UI"))

            val contentResolver = mock<ContentResolver>()
            whenever(contentResolver.query(any(), any(), any(), any(), eq(null))).thenReturn(cursor)

            val result = underTest.fetchContacts(searchQuery = "s", contentResolver)

            assertThat(result).hasSize(1)
            assertThat(result.first().name).isEqualTo("Sys UI")
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_multipleContacts_returnsAll_andCursorClosed() =
        kosmos.runTest {
            val cursor = createCursorWithEntries(names = listOf("Sys UI", "Frameworks Base"))

            val contentResolver = mock<ContentResolver>()
            whenever(contentResolver.query(any(), any(), any(), any(), eq(null))).thenReturn(cursor)

            val result = underTest.fetchContacts(searchQuery = "s", contentResolver)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactly("Sys UI", "Frameworks Base")
            verify(cursor).close()
        }

    private fun createCursorWithEntries(names: List<String>): Cursor {
        val cursor = mock<Cursor>()
        var numEntriesFetched = 0

        whenever(cursor.moveToNext()).thenReturn(numEntriesFetched < names.size)
        whenever(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
            .thenReturn(0)
        doAnswer { _: InvocationOnMock ->
                val returnValue = names[numEntriesFetched]
                numEntriesFetched++
                returnValue
            }
            .whenever(cursor)
            .getString(0)

        return cursor
    }
}
