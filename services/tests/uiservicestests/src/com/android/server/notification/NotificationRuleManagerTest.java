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


import static android.app.Notification.CATEGORY_ALARM;
import static android.app.Notification.CATEGORY_MESSAGE;
import static android.app.Notification.FLAG_PROMOTED_ONGOING;
import static android.app.NotificationLoggingConstants.DATA_TYPE_NOTIFICATION_RULES;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_DEFAULT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_LOW;
import static android.app.NotificationRule.Filter.CONVERSATION_LEVEL_PRIORITY;
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.os.UserHandle.USER_ALL;
import static android.service.notification.Adjustment.KEY_HIGHLIGHT;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_LIGHT;
import static android.service.notification.Adjustment.KEY_MODE_BREAKTHROUGHS;
import static android.service.notification.Adjustment.KEY_NOTIFICATION_RULES;
import static android.service.notification.Adjustment.KEY_SOUND;
import static android.service.notification.Adjustment.KEY_TYPE;
import static android.service.notification.Adjustment.TYPE_NEWS;
import static android.service.notification.Adjustment.TYPE_PROMOTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.Flags;
import android.app.NotificationRule;
import android.app.backup.BackupRestoreEventLogger;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.notification.Adjustment;
import android.util.Log;
import android.util.Xml;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.UiServiceTestCase;
import com.android.server.pm.UserManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH})
public class NotificationRuleManagerTest extends UiServiceTestCase {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    BackupRestoreEventLogger mLogger;
    @Mock
    private NotificationManagerPrivate mNmPrivate;
    private int mUser;
    private int mUserSecondary;
    private int mUserProfile;
    @Mock
    private UserManagerInternal mUmInternal;

    private NotificationRuleManager underTest;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mUser = ActivityManager.getCurrentUser();
        mUserSecondary = mUser + 10;
        mUserProfile = mUser + 1;

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUmInternal);

        when(mUmInternal.getProfileParentId(mUser)).thenReturn(mUser);
        when(mUmInternal.getProfileParentId(mUserSecondary)).thenReturn(mUserSecondary);
        when(mUmInternal.getProfileParentId(mUserProfile)).thenReturn(mUser);

        underTest = new NotificationRuleManager(mContext, mNmPrivate);

    }

    private NotificationRule createRule(int id, String name, boolean isEnabled) {
        return new NotificationRule.Builder(id, name).setEnabled(isEnabled).build();
    }

    static NotificationRule createFullRule(int id, String name, boolean isEnabled) {
        int primaryAction = NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
        String editAction = "editAction";
        String bundleName = "bundleName";
        String emojiIcon = "\uD83D\uDE42";
        int lightColor = Color.RED;
        List<String> modes = List.of("manual", "bedtime");
        Uri soundHaptics = Settings.System.DEFAULT_NOTIFICATION_URI;
        List<String> categories = List.of(CATEGORY_MESSAGE, CATEGORY_ALARM);
        int contactLevel = NotificationRule.Filter.CONTACT_LEVEL_CONTACT;
        int conversationLevel = CONVERSATION_LEVEL_PRIORITY;
        List<Uri> contacts = List.of(Uri.EMPTY, Uri.fromParts("hi", "hi", "hi"));
        List<Integer> excludedPackages = List.of(1234, 4321);
        List<Integer> includedPackages = List.of(5678, 8765);
        int flagMask = FLAG_PROMOTED_ONGOING;
        List<String> keywords = List.of("keyword", "another");
        List<String> shortcutIds = List.of("abc", "def");
        List<Integer> staticBundleTypes = List.of(Adjustment.TYPE_NEWS, Adjustment.TYPE_PROMOTION);
        List<UserHandle> userHandles = List.of(UserHandle.SYSTEM, UserHandle.CURRENT);
        List<Integer> days = List.of(0, 1);
        int startHour = 1;
        int startMinute = 30;
        int endHour = 3;
        int endMinute = 45;
        double latitude = 5.0;
        double longitude = 20.0;
        float radius = 10f;

        return new NotificationRule.Builder(id, name)
                .setAction(new NotificationRule.Action.Builder(primaryAction)
                        .setDynamicBundleName(bundleName)
                        .setDynamicBundleEmojiIcon(emojiIcon)
                        .setLightColorOverride(lightColor)
                        .setModeBreakthroughIds(modes)
                        .setSoundHapticOverride(soundHaptics)
                        .build())
                .setConditions(List.of(
                        NotificationRule.Condition.createTimeCondition(
                                days, startHour, startMinute, endHour, endMinute),
                        NotificationRule.Condition.createLocationCondition(
                                latitude, longitude, radius))
                )
                .setFilters(List.of(new NotificationRule.Filter.Builder()
                        .setCategories(categories)
                        .setContactLevel(contactLevel)
                        .setConversationLevel(conversationLevel)
                        .setContacts(contacts)
                        .setExcludedPackageUids(excludedPackages)
                        .setFlags(flagMask)
                        .setIncludedPackageUids(includedPackages)
                        .setKeywords(keywords)
                        .setShortcutIds(shortcutIds)
                        .setStaticBundleTypes(staticBundleTypes)
                        .setUsers(userHandles)
                        .build()))
                .setCanBeDisabled(false)
                .setEditIntentAction(editAction)
                .setEnabled(isEnabled)
                .build();
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
        underTest.addNotificationRule(mUserSecondary, 0, rule2);

        assertThat(underTest.getNotificationRules(mUser)).containsExactly(rule);
        assertThat(underTest.getNotificationRules(mUserSecondary)).containsExactly(rule2);
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

    @Test
    public void readWriteXml_local() throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);

        NotificationRule rule1User1 = createFullRule(100, "test", true);
        NotificationRule rule2User1 = createFullRule(101, "another", false);
        NotificationRule rule3User1 = createRule(103, "boo", true);
        underTest.setNotificationRules(mUser, List.of(rule1User1, rule2User1, rule3User1));
        NotificationRule rule1User2 = createFullRule(101, "secondary", true);
        NotificationRule rule2User2 = createFullRule(102, "third", false);
        underTest.setNotificationRules(mUserSecondary, List.of(rule1User2, rule2User2));

        underTest.writeXml(serializer, false, UserHandle.USER_ALL, null, false);
        serializer.endDocument();
        serializer.flush();

        // Uncomment if needed for debugging
        // Log.v(getClass().getSimpleName(), baos.toString("UTF-8"));

        TypedXmlPullParser parser = Xml.newFastPullParser();
        byte[] byteArray = baos.toByteArray();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(byteArray)), null);
        parser.nextTag();

        underTest = new NotificationRuleManager(mContext, mNmPrivate);
        underTest.readXml(parser, false, UserHandle.USER_ALL, null);

        assertThat(underTest.getNotificationRules(mUser))
                .containsExactly(rule1User1, rule2User1, rule3User1).inOrder();
        assertThat(underTest.getNotificationRules(mUserSecondary)).containsExactly(
                rule1User2, rule2User2).inOrder();
        assertThat(underTest.getNotificationRules(UserHandle.USER_ALL)).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWriteXml_local_wrongUser_onWrite() throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);

        NotificationRule rule1User1 = createFullRule(100, "test", true);
        underTest.setNotificationRules(mUser, List.of(rule1User1));

        underTest.writeXml(serializer, false, mUser, null, false);
        serializer.endDocument();
        serializer.flush();
    }

    @Test(expected = IllegalArgumentException.class)
    public void readWriteXml_local_wrongUser_onRead() throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);

        NotificationRule rule1User1 = createFullRule(100, "test", true);
        underTest.setNotificationRules(mUser, List.of(rule1User1));

        underTest.writeXml(serializer, false, UserHandle.USER_ALL, null, false);
        serializer.endDocument();
        serializer.flush();

        TypedXmlPullParser parser = Xml.newFastPullParser();
        byte[] byteArray = baos.toByteArray();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(byteArray)), null);
        parser.nextTag();

        underTest = new NotificationRuleManager(mContext, mNmPrivate);
        underTest.readXml(parser, false, mUser, null);

        assertThat(underTest.getNotificationRules(mUser)).isEmpty();
    }

    @Test
    public void readWriteXml_backupRestore() throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);

        NotificationRule rule1User1 = createFullRule(100, "test", true);
        NotificationRule rule2User1 = createFullRule(101, "another", false);
        NotificationRule rule3User1 = createRule(103, "boo", true);
        underTest.setNotificationRules(mUser, List.of(rule1User1, rule2User1, rule3User1));
        NotificationRule rule1User2 = createFullRule(101, "secondary", true);
        NotificationRule rule2User2 = createFullRule(102, "third", false);
        underTest.setNotificationRules(mUserSecondary, List.of(rule1User2, rule2User2));

        underTest.writeXml(serializer, true, mUser,
                android.app.Flags.backupRestoreLogging() ? mLogger : null, false);
        serializer.endDocument();
        serializer.flush();

        // Uncomment if needed for debugging
        // Log.v(getClass().getSimpleName(), baos.toString("UTF-8"));

        if (android.app.Flags.backupRestoreLogging()) {
            verify(mLogger).logItemsBackedUp(DATA_TYPE_NOTIFICATION_RULES, 3);
        }

        TypedXmlPullParser parser = Xml.newFastPullParser();
        byte[] byteArray = baos.toByteArray();
        parser.setInput(new BufferedInputStream(new ByteArrayInputStream(byteArray)), null);
        parser.nextTag();

        underTest = new NotificationRuleManager(mContext, mNmPrivate);
        underTest.readXml(parser, true, mUserSecondary, mLogger);

        if (android.app.Flags.backupRestoreLogging()) {
            verify(mLogger).logItemsRestored(DATA_TYPE_NOTIFICATION_RULES, 3);
            verify(mLogger, never()).logItemsRestoreFailed(anyString(), anyInt(), anyString());
        }

        assertThat(underTest.getNotificationRules(mUserSecondary))
                .containsExactly(rule1User1, rule2User1, rule3User1).inOrder();
        assertThat(underTest.getNotificationRules(mUser)).isEmpty();
    }

    @Test
    public void onUserAdded_primary() {
        underTest.onUserAdded(mUser);

        assertThat(underTest.getNotificationRules(mUser)).hasSize(4);
        assertThat(underTest.getNotificationRules(mUserSecondary)).hasSize(0);
        assertThat(underTest.getNotificationRules(mUserProfile)).hasSize(0);

        assertThat(underTest.getNotificationRules(mUser).stream().mapToInt(NotificationRule::getId))
                .containsExactly(RESERVED_ID_PROMOTED, RESERVED_ID_PRIORITY_CONVERSATIONS,
                        RESERVED_ID_STATIC_BUNDLES, RESERVED_ID_IMPORTANT_NOTIFICATIONS);
    }

    @Test
    public void onUserAdded_secondary() {
        underTest.onUserAdded(mUserSecondary);

        assertThat(underTest.getNotificationRules(mUserSecondary)).hasSize(4);
    }

    @Test
    public void onUserAdded_profile() {
        underTest.onUserAdded(mUserProfile);
        assertThat(underTest.getNotificationRules(mUserProfile)).hasSize(0);
    }

    @Test
    public void onUserAdded_bundleRule() {
        underTest.onUserAdded(mUser);

        NotificationRule actual = underTest.getNotificationRule(
                mUser, RESERVED_ID_STATIC_BUNDLES);

        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getUsers()).containsExactly(UserHandle.of(mUser));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_BUNDLE);
        assertThat(actual.getAction().getModeBreakthroughIds()).isEmpty();
        assertThat(actual.getAction().getDynamicBundleEmojiIcon()).isNull();
        assertThat(actual.getAction().getDynamicBundleName()).isNull();
        assertThat(actual.getAction().getLightColorOverride()).isEqualTo(0);
        assertThat(actual.getAction().getSoundHapticOverride()).isNull();
        assertThat(actual.canBeDisabled()).isTrue();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("BUNDLE");

        Parcel parcel = Parcel.obtain();

        actual.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NotificationRule fromParcel = NotificationRule.CREATOR.createFromParcel(parcel);
        assertThat(fromParcel).isEqualTo(actual);
    }

    @Test
    public void onUserAdded_importantRule() {
        underTest.onUserAdded(mUser);

        NotificationRule actual = underTest.getNotificationRule(
                mUser, RESERVED_ID_IMPORTANT_NOTIFICATIONS);

        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters()).isEmpty();
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_HIGHLIGHT);
        assertThat(actual.canBeDisabled()).isTrue();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).isNull();

        Parcel parcel = Parcel.obtain();

        actual.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NotificationRule fromParcel = NotificationRule.CREATOR.createFromParcel(parcel);
        assertThat(fromParcel).isEqualTo(actual);
    }

    @Test
    public void onUserAdded_promotedRule() {
        underTest.onUserAdded(mUser);

        NotificationRule actual = underTest.getNotificationRule(
                mUser, RESERVED_ID_PROMOTED);

        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getFlags()).isEqualTo(FLAG_PROMOTED_ONGOING);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_HIGHLIGHT);
        assertThat(actual.canBeDisabled()).isFalse();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("PROMOTED");

        Parcel parcel = Parcel.obtain();

        actual.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NotificationRule fromParcel = NotificationRule.CREATOR.createFromParcel(parcel);
        assertThat(fromParcel).isEqualTo(actual);
    }

    @Test
    public void onUserAdded_conversationRule() {
        underTest.onUserAdded(mUser);

        NotificationRule actual = underTest.getNotificationRule(
                mUser, RESERVED_ID_PRIORITY_CONVERSATIONS);

        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getConversationLevel())
                .isEqualTo(CONVERSATION_LEVEL_PRIORITY);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_HIGHLIGHT);
        assertThat(actual.canBeDisabled()).isFalse();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("CONVERSATION");

        Parcel parcel = Parcel.obtain();

        actual.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NotificationRule fromParcel = NotificationRule.CREATOR.createFromParcel(parcel);
        assertThat(fromParcel).isEqualTo(actual);
    }
}
