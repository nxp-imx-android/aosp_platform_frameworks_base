/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.personalcontext.notifications;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.service.notification.Adjustment;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.notification.NotificationManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotificationActionRendererTest {
    private Context mContext;
    private NotificationActionRenderer mRenderer;
    private StatusBarNotification mNotification;

    @Mock private NotificationManagerInternal mNotificationManagerInternal;
    @Mock private PackageManager mPackageManager;

    private static final NotificationChannel NOTIFICATION_CHANNEL =
            new NotificationChannel("id", "name", IMPORTANCE_DEFAULT);
    private static final NotificationListenerService.RankingMap RANKING_MAP =
            new NotificationListenerService.RankingMap(new NotificationListenerService.Ranking[0]);

    private static final String TEST_APP_NAME = "Test App";
    private static final int TEST_APP_RESOURCE_ID = 1234;

    private static final Icon FAKE_ICON = Icon.createWithResource("pkg", 123);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mRenderer =
                new NotificationActionRenderer(
                        mContext, mNotificationManagerInternal, mPackageManager);
        mNotification =
                new StatusBarNotification(
                        /* pkg= */ "pkg",
                        /* opPkg= */ "opPkg",
                        /* id= */ 0,
                        /* tag= */ "tag",
                        /* uid= */ 0,
                        /* initialPid= */ 0,
                        new Notification(),
                        mContext.getUser(),
                        /* overrideGroupKey= */ null,
                        /* postTime= */ 0);
    }

    @Test
    public void testRender_notActionableInsight_noAction() {
        mockPackageManagerResolvesIntent();
        ContextInsight insight = new BundleInsight.Builder().build();

        mRenderer.render(insight, false);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_actionableInsightWithNoNotificationHint_noAction() {
        mockPackageManagerResolvesIntent();

        InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title", FAKE_ICON).build();
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setIntent(new Intent()).build();
        ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();

        mRenderer.render(insight, false);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_actionableInsightWithNotificationHint_requestsAdjustment() {
        mockPackageManagerResolvesIntent();
        ActionableInsight insight = createActionableInsight("Test Title", FAKE_ICON);
        Notification.Action action = renderAndGetAction(insight);

        assertThat(action.title.toString()).isEqualTo("Test Title");
        assertThat(action.isContextual()).isTrue();
        assertThat(action.actionIntent).isNotNull();
        assertThat(action.getIcon()).isEqualTo(FAKE_ICON);
    }

    @Test
    public void testRender_noTitleInsight_usesDefaultTitle() {
        mockPackageManagerResolvesIntent();
        ActionableInsight insight = createActionableInsight(null, FAKE_ICON);
        Notification.Action action = renderAndGetAction(insight);

        CharSequence expectedTitle =
                mContext.getString(com.android.internal.R.string.open_app_name, TEST_APP_NAME);
        assertThat(action.title.toString()).isEqualTo(expectedTitle.toString());
    }

    @Test
    public void testRender_noIconInsight_usesDefaultIcon() {
        mockPackageManagerResolvesIntent();
        ActionableInsight insight = createActionableInsight("Test Title", null);
        Notification.Action action = renderAndGetAction(insight);

        assertThat(action.getIcon().getResPackage()).isEqualTo("pkg");
        assertThat(action.getIcon().getResId()).isEqualTo(TEST_APP_RESOURCE_ID);
    }

    @Test
    public void testRender_cannotResolveIntent_noAction() {
        NotificationHint hint =
                new NotificationHint.Builder(
                                new NotificationEnqueuedEvent(
                                        mNotification, NOTIFICATION_CHANNEL, RANKING_MAP))
                        .build();
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setIntent(new Intent("ACTION")).build();
        InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("Test Title", FAKE_ICON).build();
        ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails)
                        .setOriginHints(List.of(hint))
                        .build();
        when(mPackageManager.queryIntentActivitiesAsUser(any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        mRenderer.render(insight, false);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    private ActionableInsight createActionableInsight(@Nullable String title, @Nullable Icon icon) {
        NotificationHint hint =
                new NotificationHint.Builder(
                                new NotificationEnqueuedEvent(
                                        mNotification, NOTIFICATION_CHANNEL, RANKING_MAP))
                        .build();
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setIntent(new Intent("ACTION")).build();
        InsightDisplayDetails.Builder displayDetailsBuilder;
        if (title != null && icon != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title, icon);
        } else if (title != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title);
        } else {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(icon);
        }
        InsightDisplayDetails displayDetails = displayDetailsBuilder.build();
        return new ActionableInsight.Builder(actionDetails, displayDetails)
                .setOriginHints(List.of(hint))
                .build();
    }

    private Notification.Action renderAndGetAction(ActionableInsight insight) {
        mRenderer.render(insight, false);

        ArgumentCaptor<List<Adjustment>> captor = ArgumentCaptor.forClass(List.class);
        verify(mNotificationManagerInternal).requestSystemAdjustments(captor.capture());

        List<Adjustment> adjustments = captor.getValue();
        assertThat(adjustments).hasSize(1);
        Adjustment adjustment = adjustments.get(0);
        assertThat(adjustment.getPackage()).isEqualTo(mNotification.getPackageName());
        assertThat(adjustment.getKey()).isEqualTo(mNotification.getKey());
        assertThat(adjustment.getUser()).isEqualTo(mNotification.getUser().getIdentifier());

        ArrayList<Notification.Action> contextualActions =
                adjustment
                        .getSignals()
                        .getParcelableArrayList(
                                Adjustment.KEY_CONTEXTUAL_ACTIONS, Notification.Action.class);
        assertThat(contextualActions).hasSize(1);
        return contextualActions.get(0);
    }

    private void mockPackageManagerResolvesIntent() {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = "pkg";
        activityInfo.name = "TestActivity";
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.packageName = "pkg";
        activityInfo.icon = TEST_APP_RESOURCE_ID;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;

        when(mPackageManager.queryIntentActivitiesAsUser(any(), anyInt(), anyInt()))
                .thenReturn(List.of(resolveInfo));
        when(mPackageManager.getApplicationLabel(any(ApplicationInfo.class)))
                .thenReturn(TEST_APP_NAME);
    }
}
