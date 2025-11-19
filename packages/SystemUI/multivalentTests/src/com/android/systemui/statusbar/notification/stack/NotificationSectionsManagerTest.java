/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.stack;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.app.NotificationChannel;
import android.testing.TestableLooper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.kosmos.KosmosJavaAdapter;
import com.android.systemui.media.controls.ui.controller.KeyguardMediaController;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.collection.BundleSpec;
import com.android.systemui.statusbar.notification.collection.render.MediaContainerController;
import com.android.systemui.statusbar.notification.collection.render.SectionHeaderController;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class NotificationSectionsManagerTest extends SysuiTestCase {

    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock private NotificationStackScrollLayout mNssl;
    @Mock private StatusBarStateController mStatusBarStateController;
    @Mock private ConfigurationController mConfigurationController;
    @Mock private KeyguardMediaController mKeyguardMediaController;
    @Mock private MediaContainerController mMediaContainerController;
    @Mock private NotificationRoundnessManager mNotificationRoundnessManager;
    @Mock private SectionHeaderController mIncomingHeaderController;
    @Mock private SectionHeaderController mPeopleHeaderController;
    @Mock private SectionHeaderController mAlertingHeaderController;
    @Mock private SectionHeaderController mSilentHeaderController;
    @Mock private SectionHeaderController mHighlightsHeaderController;

    private NotificationSectionsManager mSectionsManager;

    private final KosmosJavaAdapter mKosmos = new KosmosJavaAdapter(this);

    @Before
    public void setUp() {
        mSectionsManager =
                new NotificationSectionsManager(
                        mConfigurationController,
                        mKeyguardMediaController,
                        mMediaContainerController,
                        mNotificationRoundnessManager,
                        mIncomingHeaderController,
                        mPeopleHeaderController,
                        mAlertingHeaderController,
                        mSilentHeaderController,
                        mHighlightsHeaderController
                );
        // Required in order for the header inflation to work properly
        when(mNssl.generateLayoutParams(any(AttributeSet.class)))
                .thenReturn(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        mSectionsManager.initialize(mNssl);
        when(mNssl.indexOfChild(any(View.class))).thenReturn(-1);
        when(mStatusBarStateController.getState()).thenReturn(StatusBarState.SHADE);
    }

    @Test(expected =  IllegalStateException.class)
    public void testDuplicateInitializeThrows() {
        mSectionsManager.initialize(mNssl);
    }

    @Test
    public void testRoundAllBundles() {
        ExpandableNotificationRow be1 = mKosmos.createRowBundle(BundleSpec.Companion.getNEWS());
        ExpandableNotificationRow be2 = mKosmos.createRowBundle(
                BundleSpec.Companion.getRECOMMENDED());
        ExpandableNotificationRow silentSolo = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setChannel(new NotificationChannel("1", "1", 2));
                    return builder.done();
                }));
        ExpandableNotificationRow silentSolo2 = mKosmos.createRow(
                mKosmos.buildNotificationEntry(builder -> {
                    builder.setChannel(new NotificationChannel("1", "1", 2));
                    return builder.done();
                }));

        List<ExpandableView> views = List.of(be1, be2, silentSolo, silentSolo2);
        mSectionsManager.updateFirstAndLastViewsForAllSections(views);
        assertThat(be1.getBottomRoundness()).isWithin(.001f).of(1f);
        assertThat(be1.getTopRoundness()).isWithin(.001f).of(1f);
        assertThat(be2.getBottomRoundness()).isWithin(.001f).of(1f);
        assertThat(be2.getTopRoundness()).isWithin(.001f).of(1f);
        assertThat(silentSolo.getBottomRoundness()).isWithin(.001f).of(0f);
        assertThat(silentSolo.getTopRoundness()).isWithin(.001f).of(1f);
        assertThat(silentSolo2.getBottomRoundness()).isWithin(.001f).of(1f);
        assertThat(silentSolo2.getTopRoundness()).isWithin(.001f).of(0f);
    }

}
