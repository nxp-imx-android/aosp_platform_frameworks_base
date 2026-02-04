/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os

import android.os.Binder
import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DedupBinderHelperTest {

    @Test
    fun testDedupBinderHelper_Optimized_ReducesSizeAndPreservesData() {
        val binderA = Binder()
        val binderB = Binder()

        val inputList =
            listOf(
                binderA, // New (Index 0)
                binderB, // New (Index 1)
                binderA, // Dedup -> Index 0
                binderA, // Dedup -> Index 0
                binderB, // Dedup -> Index 1
            )

        // Measure size without the helper
        val standardParcel = Parcel.obtain()
        val standardSize =
            try {
                // Write strictly without the helper
                standardParcel.writeBinderList(inputList)
                standardParcel.dataSize()
            } finally {
                standardParcel.recycle()
            }

        // Measure size with the helper
        val optimizedParcel = Parcel.obtain()
        val helper = DedupBinderHelper()
        try {
            optimizedParcel.setReadWriteHelper(helper)
            optimizedParcel.writeBinderList(inputList)
            val optimizedSize = optimizedParcel.dataSize()
            assertThat(optimizedSize).isLessThan(standardSize)

            // Verify data integrity
            val readHelper = DedupBinderHelper()
            optimizedParcel.setDataPosition(0)
            optimizedParcel.setReadWriteHelper(readHelper)

            val outputList = optimizedParcel.createBinderArrayList()
            requireNotNull(outputList) { "Output list should not be null" }

            assertThat(outputList).hasSize(inputList.size)
            assertThat(outputList[0]).isSameInstanceAs(binderA)
            assertThat(outputList[1]).isSameInstanceAs(binderB)
            assertThat(outputList[2]).isSameInstanceAs(binderA)
            assertThat(outputList[3]).isSameInstanceAs(binderA)
            assertThat(outputList[4]).isSameInstanceAs(binderB)
        } finally {
            optimizedParcel.recycle()
        }
    }
}
