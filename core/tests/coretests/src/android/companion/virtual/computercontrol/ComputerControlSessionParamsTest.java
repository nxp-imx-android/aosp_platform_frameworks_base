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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.AppInteractionAttribution;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionParamsTest {

    private static final String SESSION_NAME = "ComputerControlSessionName";
    private static final int TARGET_COMPUTER_CONTROL_VERSION = 1;
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        PendingIntent previewIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent("PREVIEW"),
                        PendingIntent.FLAG_IMMUTABLE);
        AppInteractionAttribution appInteractionAttribution =
                new AppInteractionAttribution.Builder(
                                AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                        .build();
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(TARGET_COMPUTER_CONTROL_VERSION)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(previewIntent)
                        .setAppInteractionAttribution(appInteractionAttribution)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetComputerControlVersion())
                .isEqualTo(TARGET_COMPUTER_CONTROL_VERSION);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isEqualTo(previewIntent);
        assertThat(params.getAppInteractionAttribution()).isEqualTo(appInteractionAttribution);
    }

    @Test
    public void parcelable_unsetPreviewIntent_shouldRecreateSuccessfully() {
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
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
        assertThat(params.getPreviewIntent()).isNull();
    }

    @Test
    public void parcelable_unsetAppInteractionAttribution_shouldRecreateSuccessfully() {
        PendingIntent previewIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent("PREVIEW"),
                        PendingIntent.FLAG_IMMUTABLE);
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(previewIntent)
                        .setAppInteractionAttribution(null)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isEqualTo(previewIntent);
        assertThat(params.getAppInteractionAttribution()).isNull();
    }

    @Test
    public void build_unsetName_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .build());
    }

    @Test
    public void build_unsetTargetPackageNames_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder().setName(SESSION_NAME).build());
    }

    @Test
    public void build_withNullPreviewIntent_setsPreviewIntentToNull() {
        ComputerControlSessionParams params =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(TARGET_COMPUTER_CONTROL_VERSION)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(null)
                        .build();

        assertThat(params.getPreviewIntent()).isNull();
    }

    @Test
    public void build_withNullAppInteractionAttribution_setsAppInteractionAttributionToNull() {
        ComputerControlSessionParams params =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(TARGET_COMPUTER_CONTROL_VERSION)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setAppInteractionAttribution(null)
                        .build();

        // Ensure this doesn't throw for backwards compatibility.
        assertThat(params.getAppInteractionAttribution()).isNull();
    }

    @Test
    public void build_withTargetComputerControlVersion5_unsetAppInteractionAttribution_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setName(SESSION_NAME)
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .setTargetComputerControlVersion(5)
                                .build());
    }
}
