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

package android.companion.virtual.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionParamsTest {

    private static final String SESSION_NAME = "ComputerControlSessionName";
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        ComputerControlSessionParams originalParams = new ComputerControlSessionParams.Builder()
                .setName(SESSION_NAME)
                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
    }

    @Test
    public void build_unsetName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .build());
    }

    @Test
    public void build_unsetTargetPackageNames_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .build());
    }
}
