/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.inputmethod.data.repository

import android.os.UserHandle
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class InputMethodRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val inputMethodManager = mock<InputMethodManager>()
    private val underTest =
        InputMethodRepositoryImpl(
            backgroundDispatcher = kosmos.testDispatcher,
            inputMethodManager = inputMethodManager,
        )

    @Test
    fun enabledInputMethods_noImes_emptyFlow() =
        testScope.runTest {
            inputMethodManager.stub {
                on { getEnabledInputMethodListAsUser(eq(USER_HANDLE)) }.thenReturn(listOf())
                on { getEnabledInputMethodSubtypeListAsUser(any(), any(), eq(USER_HANDLE)) }
                    .thenReturn(listOf())
            }

            assertThat(underTest.enabledInputMethods(USER_HANDLE, fetchSubtypes = true).count())
                .isEqualTo(0)
        }

    @Test
    fun selectedInputMethodSubtypes_returnsSubtypeList() =
        testScope.runTest {
            val subtypeId = 123
            val isAuxiliary = true
            val selectedImiId = "imiId"
            val selectedImi = mock<InputMethodInfo> { on { id } doReturn selectedImiId }
            inputMethodManager.stub {
                on { getCurrentInputMethodInfoAsUser(eq(USER_HANDLE)) }.thenReturn(selectedImi)
                on { getEnabledInputMethodListAsUser(eq(USER_HANDLE)) }
                    .thenReturn(listOf(selectedImi))
                on {
                        getEnabledInputMethodSubtypeListAsUser(
                            eq(selectedImiId),
                            any(),
                            eq(USER_HANDLE),
                        )
                    }
                    .thenReturn(
                        listOf(
                            InputMethodSubtype.InputMethodSubtypeBuilder()
                                .setSubtypeId(subtypeId)
                                .setIsAuxiliary(isAuxiliary)
                                .build()
                        )
                    )
            }

            val result = underTest.selectedInputMethodSubtypes(USER_HANDLE)
            assertThat(result).hasSize(1)
            assertThat(result.first().subtypeId).isEqualTo(subtypeId)
            assertThat(result.first().isAuxiliary).isEqualTo(isAuxiliary)
        }

    @Test
    fun showImePicker_forwardsDisplayId() =
        testScope.runTest {
            val displayId = 7

            underTest.showInputMethodPicker(displayId, /* showAuxiliarySubtypes= */ true)

            verify(inputMethodManager)
                .showInputMethodPickerFromSystem(
                    /* showAuxiliarySubtypes = */ eq(true),
                    /* displayId = */ eq(displayId),
                )
        }

    companion object {
        private val USER_HANDLE = UserHandle.of(100)
    }
}
