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

package com.android.server.notification;


import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_DEFAULT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_LOW;
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.service.notification.Adjustment.KEY_HIGHLIGHT;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_LIGHT;
import static android.service.notification.Adjustment.KEY_MODE_BREAKTHROUGHS;
import static android.service.notification.Adjustment.KEY_NOTIFICATION_RULES;
import static android.service.notification.Adjustment.KEY_SOUND;
import static android.service.notification.Adjustment.KEY_TYPE;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.app.Flags;
import android.app.NotificationRule;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.notification.Adjustment;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.UiServiceTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH})
public class NotificationRuleManagerTest extends UiServiceTestCase {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private NotificationManagerPrivate mNmPrivate;
    private int mUser;

    private NotificationRuleManager underTest;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        underTest = new NotificationRuleManager(mContext, mNmPrivate);
        mUser = ActivityManager.getCurrentUser();
    }

    private NotificationRule createRule(int id, String name, boolean isEnabled) {
        return new NotificationRule.Builder(id, name).setEnabled(isEnabled).build();
    }

    @Test
    public void addRule() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);

        assertThat(underTest.getNotificationRule(mUser, 100)).isEqualTo(rule);
    }

    @Test
    public void addRule_outOfRange_negative() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, -10, rule);

        assertThat(underTest.getNotificationRule(mUser, 100)).isEqualTo(rule);
    }

    @Test
    public void addRule_outOfRange_positive() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 10, rule);

        assertThat(underTest.getNotificationRule(mUser, 100)).isEqualTo(rule);
    }

    @Test
    public void addRule_withSameId() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);
        NotificationRule rule2 = createRule(100, "test2", true);
        underTest.addNotificationRule(mUser, 0, rule2);

        assertThat(underTest.getNotificationRules(mUser).size()).isEqualTo(1);
        assertThat(underTest.getNotificationRule(mUser, 100)).isEqualTo(rule);
    }

    @Test
    public void addRule_multiUser() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);
        NotificationRule rule2 = createRule(100, "test2", true);
        underTest.addNotificationRule(mUser + 1, 0, rule2);

        assertThat(underTest.getNotificationRules(mUser)).containsExactly(rule);
        assertThat(underTest.getNotificationRules(mUser + 1)).containsExactly(rule2);
    }

    @Test
    public void updateRule() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);

        NotificationRule updatedRule = createRule(100, "test", false);
        underTest.updateNotificationRule(mUser, updatedRule);

        assertThat(underTest.getNotificationRules(mUser)).contains(updatedRule);
        assertThat(underTest.getNotificationRules(mUser)).doesNotContain(rule);
    }

    @Test
    public void removeRule() {
        NotificationRule rule = createRule(100, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);
        assertThat(underTest.getNotificationRules(mUser)).contains(rule);

        assertThat(underTest.removeNotificationRule(mUser, rule.getId())).isTrue();
        assertThat(underTest.getNotificationRules(mUser)).doesNotContain(rule);
    }

    @Test
    public void removeRule_doesNotExist() {
        assertThat(underTest.removeNotificationRule(mUser, 100)).isFalse();
    }

    @Test
    public void removeRule_failsForReservedIds() {
        NotificationRule rule1 = createRule(RESERVED_ID_PROMOTED, "test", true);
        underTest.addNotificationRule(mUser, 0, rule1);
        NotificationRule rule2 = createRule(RESERVED_ID_STATIC_BUNDLES, "test", true);
        underTest.addNotificationRule(mUser, 0, rule2);
        NotificationRule rule3 = createRule(RESERVED_ID_IMPORTANT_NOTIFICATIONS, "test", true);
        underTest.addNotificationRule(mUser, 0, rule3);
        NotificationRule rule4 = createRule(RESERVED_ID_PRIORITY_CONVERSATIONS, "test", true);
        underTest.addNotificationRule(mUser, 0, rule4);

        underTest.removeNotificationRule(mUser, rule1.getId());
        underTest.removeNotificationRule(mUser, rule2.getId());
        underTest.removeNotificationRule(mUser, rule3.getId());
        underTest.removeNotificationRule(mUser, rule4.getId());

        assertThat(underTest.getNotificationRules(mUser)).containsAtLeastElementsIn(
                List.of(rule1, rule2, rule3, rule4));
    }

    @Test
    public void setNotificationRules() {
        NotificationRule rule1 = createRule(RESERVED_ID_PROMOTED, "test", true);
        NotificationRule rule2 = createRule(RESERVED_ID_STATIC_BUNDLES, "test", true);
        NotificationRule rule3 = createRule(RESERVED_ID_IMPORTANT_NOTIFICATIONS, "test", true);
        NotificationRule rule4 = createRule(RESERVED_ID_PRIORITY_CONVERSATIONS, "test", true);
        NotificationRule rule5 = createRule(101, "test", true);
        underTest.setNotificationRules(mUser, List.of(rule1, rule2, rule3, rule4, rule5));

        assertThat(underTest.getNotificationRules(mUser)).containsExactly(
                rule1, rule2, rule3, rule4, rule5);
    }

    @Test
    public void onUserRemoved() {
        NotificationRule rule = createRule(RESERVED_ID_PROMOTED, "test", true);
        underTest.addNotificationRule(mUser, 0, rule);

        underTest.onUserRemoved(mUser);

        assertThat(underTest.getNotificationRules(mUser)).isEmpty();
    }

    @Test
    public void onNotificationAssistantChanged() {
        NotificationRule rule = createRule(100, "test", true);
        NotificationRule rule1 = createRule(RESERVED_ID_PROMOTED, "test", true);
        NotificationRule rule2 = createRule(RESERVED_ID_STATIC_BUNDLES, "test", true);
        NotificationRule rule3 = createRule(RESERVED_ID_IMPORTANT_NOTIFICATIONS, "test", true);
        NotificationRule rule4 = createRule(RESERVED_ID_PRIORITY_CONVERSATIONS, "test", true);
        underTest.setNotificationRules(mUser, List.of(rule, rule1, rule2, rule3, rule4));

        underTest.onNotificationAssistantChanged(mUser);

        assertThat(underTest.getNotificationRules(mUser)).containsExactly(rule1, rule4);
    }

    @Test
    public void getAdjustmentsForRules_singleRule_highlight() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT)
                        .setSoundHapticOverride(Uri.EMPTY)
                        .setModeBreakthroughIds(List.of("manual"))
                        .setLightColorOverride(Color.parseColor("blue"))
                        .build())
                .build();
        underTest.addNotificationRule(mUser, 0, rule);

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, new ArrayList<>(List.of(100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        assertThat(behavioralAdjustments).hasSize(1);
        Adjustment behavioralAdjustment = behavioralAdjustments.get(0);
        assertThat(behavioralAdjustment.getPackage()).isEqualTo(original.getPackage());
        assertThat(behavioralAdjustment.getKey()).isEqualTo(original.getKey());
        assertThat(behavioralAdjustment.getUserHandle()).isEqualTo(original.getUserHandle());
        assertThat(behavioralAdjustment.getOriginatingRuleId()).isEqualTo(rule.getId());
        Bundle actualSignals = behavioralAdjustment.getSignals();

        assertThat(actualSignals.getBoolean(KEY_HIGHLIGHT)).isEqualTo(true);
        assertThat(actualSignals.getParcelable(KEY_SOUND, Uri.class)).isEqualTo(Uri.EMPTY);
        assertThat(actualSignals.getStringArrayList(KEY_MODE_BREAKTHROUGHS))
                .containsExactly("manual");
        assertThat(actualSignals.getInt(KEY_LIGHT)).isEqualTo(Color.parseColor("blue"));
    }

    @Test
    public void getAdjustmentsForRules_singleRule_default() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_DEFAULT)
                        .setSoundHapticOverride(Uri.EMPTY)
                        .setModeBreakthroughIds(List.of("manual"))
                        .setLightColorOverride(Color.parseColor("blue"))
                        .build())
                .build();
        underTest.addNotificationRule(mUser, 0, rule);

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, new ArrayList<>(List.of(100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        Adjustment behavioralAdjustment = behavioralAdjustments.get(0);
        Bundle actualSignals = behavioralAdjustment.getSignals();

        assertThat(actualSignals.getInt(KEY_IMPORTANCE)).isEqualTo(IMPORTANCE_DEFAULT);
        assertThat(actualSignals.getParcelable(KEY_SOUND, Uri.class)).isEqualTo(Uri.EMPTY);
        assertThat(actualSignals.getStringArrayList(KEY_MODE_BREAKTHROUGHS)).
                containsExactly("manual");
        assertThat(actualSignals.getInt(KEY_LIGHT)).isEqualTo(Color.parseColor("blue"));
    }

    @Test
    public void getAdjustmentsForRules_singleRule_low() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_LOW)
                        .build())
                .build();
        underTest.addNotificationRule(mUser, 0, rule);

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, new ArrayList<>(List.of(100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        Adjustment behavioralAdjustment = behavioralAdjustments.get(0);
        Bundle actualSignals = behavioralAdjustment.getSignals();

        assertThat(actualSignals.getInt(KEY_IMPORTANCE)).isEqualTo(IMPORTANCE_LOW);
        assertThat(actualSignals.getParcelable(KEY_SOUND, Uri.class)).isNull();
        assertThat(actualSignals.getStringArrayList(KEY_MODE_BREAKTHROUGHS)).isNull();
        assertThat(actualSignals.getInt(KEY_LIGHT)).isEqualTo(0);
    }

    @Test
    public void getAdjustmentsForRules_singleRule_bundle() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE)
                        .build())
                .build();
        underTest.addNotificationRule(mUser, 0, rule);

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, new ArrayList<>(List.of(100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        Adjustment behavioralAdjustment = behavioralAdjustments.get(0);
        Bundle actualSignals = behavioralAdjustment.getSignals();

        assertThat(actualSignals.getInt(KEY_TYPE)).isEqualTo(rule.getId());
    }

    @Test
    public void getAdjustmentsForRules_singleRule_block() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK)
                        .build())
                .build();
        underTest.addNotificationRule(mUser, 0, rule);

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, new ArrayList<>(List.of(100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        Adjustment behavioralAdjustment = behavioralAdjustments.get(0);
        Bundle actualSignals = behavioralAdjustment.getSignals();

        assertThat(actualSignals.getInt(KEY_IMPORTANCE)).isEqualTo(IMPORTANCE_NONE);
    }

    @Test
    public void getAdjustmentsForRules_multipleRules() {
        NotificationRule rule = new NotificationRule.Builder(100, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT)
                        .setSoundHapticOverride(Uri.EMPTY)
                        .setModeBreakthroughIds(List.of("manual"))
                        .setLightColorOverride(Color.parseColor("blue"))
                        .build())
                .build();
        NotificationRule rule2 = new NotificationRule.Builder(101, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_DEFAULT)
                        .setSoundHapticOverride(Uri.fromParts("hi", "hi", "hi"))
                        .setModeBreakthroughIds(List.of("bedtime"))
                        .setLightColorOverride(Color.parseColor("red"))
                        .build())
                .build();
        NotificationRule rule3 = new NotificationRule.Builder(RESERVED_ID_STATIC_BUNDLES, "test")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build())
                .build();
        underTest.setNotificationRules(mUser, List.of(rule, rule2, rule3));

        Bundle signals = new Bundle();
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES,
                new ArrayList<>(List.of(RESERVED_ID_STATIC_BUNDLES, 101, 100)));
        Adjustment original = new Adjustment("pkg", "key", signals, null, UserHandle.of(mUser));

        List<Adjustment> behavioralAdjustments = underTest.getAdjustmentsForRules(mUser, original);
        assertThat(behavioralAdjustments).hasSize(3);

        Adjustment behavioralAdjustment100 = behavioralAdjustments.get(0);
        assertThat(behavioralAdjustment100.getOriginatingRuleId()).isEqualTo(rule.getId());
        Bundle actualSignals = behavioralAdjustment100.getSignals();
        assertThat(actualSignals.getBoolean(KEY_HIGHLIGHT)).isEqualTo(true);
        assertThat(actualSignals.getParcelable(KEY_SOUND, Uri.class)).isEqualTo(Uri.EMPTY);
        assertThat(actualSignals.getStringArrayList(KEY_MODE_BREAKTHROUGHS))
                .containsExactly("manual");
        assertThat(actualSignals.getInt(KEY_LIGHT)).isEqualTo(Color.parseColor("blue"));

        Adjustment behavioralAdjustment101 = behavioralAdjustments.get(1);
        assertThat(behavioralAdjustment101.getOriginatingRuleId()).isEqualTo(rule2.getId());
        actualSignals = behavioralAdjustment101.getSignals();
        assertThat(actualSignals.getInt(KEY_IMPORTANCE)).isEqualTo(IMPORTANCE_DEFAULT);
        assertThat(actualSignals.getParcelable(KEY_SOUND, Uri.class)).isEqualTo(
                Uri.fromParts("hi", "hi", "hi"));
        assertThat(actualSignals.getStringArrayList(KEY_MODE_BREAKTHROUGHS))
                .containsExactly("bedtime");
        assertThat(actualSignals.getInt(KEY_LIGHT)).isEqualTo(Color.parseColor("red"));

        Adjustment behavioralAdjustment203 = behavioralAdjustments.get(2);
        assertThat(behavioralAdjustment203.getOriginatingRuleId()).isEqualTo(rule3.getId());
        actualSignals = behavioralAdjustment203.getSignals();
        assertThat(actualSignals.keySet().size()).isEqualTo(1);
        assertThat(actualSignals.getInt(KEY_TYPE)).isEqualTo(rule3.getId());
    }
}
