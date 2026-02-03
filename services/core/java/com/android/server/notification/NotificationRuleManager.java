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

import static android.app.Notification.FLAG_PROMOTED_ONGOING;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_DEFAULT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Filter.CONVERSATION_LEVEL_PRIORITY;
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.app.NotificationRule.RULE_TAG;
import static android.app.NotificationRule.USER_ATTR;
import static android.os.UserHandle.USER_ALL;
import static android.service.notification.Adjustment.KEY_HIGHLIGHT;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_LIGHT;
import static android.service.notification.Adjustment.KEY_MODE_BREAKTHROUGHS;
import static android.service.notification.Adjustment.KEY_SOUND;
import static android.service.notification.Adjustment.KEY_TYPE;

import static android.app.NotificationLoggingConstants.DATA_TYPE_NOTIFICATION_RULES;
import static android.app.NotificationLoggingConstants.ERROR_XML_PARSING;

import static com.android.server.notification.NotificationManagerService.DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.NotificationRule;
import android.app.backup.BackupRestoreEventLogger;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.Adjustment;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class manages each user's {@link NotificationRule}s. It also provides some helper methods
 * to convert and sort Adjustments according to rule behavior and priority.
 */
public class NotificationRuleManager {
    private static final String TAG = "NotificationRuleManager";
    private final Object mLock = new Object();
    private Context mContext;
    private NotificationManagerPrivate mNmPrivate;
    private UserManagerInternal mUmInternal;

    // tags and attributes for persistence
    private final int mVersion = 1;
    private final String NOTIFICATION_RULES_TAG = "notification-rules";
    private final String VERSION_ATTR = "version";
    private final String RULES_TAG = "rules";

    // key: user id. value: notification rules in priority order
    private final Map<Integer, List<NotificationRule>> mNotificationRules = new ArrayMap<>();

    public NotificationRuleManager(Context context, NotificationManagerPrivate nmPrivate) {
        mContext = context;
        mNmPrivate = nmPrivate;
        mUmInternal = LocalServices.getService(UserManagerInternal.class);
    }

    // for bugreports
    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NotificationRules:");
        synchronized (mLock) {
            for (@UserIdInt int user : mNotificationRules.keySet()) {
                pw.println("  User " + user);
                for (NotificationRule rule : mNotificationRules.get(user)) {
                    pw.println("    rule: " + rule);
                }
            }
        }
    }

    // for backup and restore
    void readXml(TypedXmlPullParser parser, boolean forRestore, @UserIdInt int userId,
            @Nullable BackupRestoreEventLogger logger) throws XmlPullParserException, IOException {
        int type = parser.getEventType();
        if (type != XmlPullParser.START_TAG) return;
        String tag = parser.getName();
        if (!NOTIFICATION_RULES_TAG.equals(tag)) return;
        int successfulReads = 0;
        int unsuccessfulReads = 0;
        synchronized (mLock) {
            type = parser.next();
            tag = parser.getName();
            while (type != XmlPullParser.END_DOCUMENT
                    && !(NOTIFICATION_RULES_TAG.equals(tag) && type == XmlPullParser.END_TAG)) {
                tag = parser.getName();
                if (RULE_TAG.equals(tag) && type == XmlPullParser.START_TAG) {
                    int ruleUser = parser.getAttributeInt(null, USER_ATTR, USER_ALL);
                    if (userId == USER_ALL || userId == ruleUser) {
                        try {
                            NotificationRule rule = NotificationRule.readXml(parser, forRestore,
                                    mContext);
                            List<NotificationRule> rulesForUser =
                                    mNotificationRules.getOrDefault(ruleUser, new ArrayList<>());
                            rulesForUser.add(rule);
                            mNotificationRules.put(ruleUser, rulesForUser);
                            successfulReads++;
                        } catch (Exception e) {
                            Slog.d(TAG, "failed to restore rule", e);
                            unsuccessfulReads++;
                        }
                    }
                }
                type = parser.next();
                tag = parser.getName();
            }

            if (logger != null) {
                logger.logItemsRestored(DATA_TYPE_NOTIFICATION_RULES, successfulReads);
                if (unsuccessfulReads > 0) {
                    logger.logItemsRestoreFailed(
                            DATA_TYPE_NOTIFICATION_RULES, unsuccessfulReads, ERROR_XML_PARSING);
                }
            }
        }
    }

    void writeXml(TypedXmlSerializer out, boolean forBackup, @UserIdInt int userId,
            @Nullable BackupRestoreEventLogger logger, boolean useLegacyBundleStorage)
            throws IOException {
        synchronized (mLock) {
            out.startTag(null, NOTIFICATION_RULES_TAG);
            out.attributeInt(null, VERSION_ATTR, mVersion);
            out.startTag(null, RULES_TAG);
            int persistedRules = 0;
            for (@UserIdInt int user : mNotificationRules.keySet()) {
                if (userId == USER_ALL || userId == user) {
                    for (NotificationRule rule : mNotificationRules.get(user)) {
                        try {
                            rule.writeXml(out, forBackup, user, mContext);
                            persistedRules++;
                        } catch (Exception e) {
                            Slog.d(TAG, "Failed to backup " + rule.getName(), e);
                        }
                    }
                }
            }
            out.endTag(null, RULES_TAG);
            // TODO(b/478826998): add default highlighted behavior
            // TODO(b/479254459): migrate bundles
            out.endTag(null, NOTIFICATION_RULES_TAG);

            if (logger != null) {
                logger.logItemsBackedUp(DATA_TYPE_NOTIFICATION_RULES, persistedRules);
            }
        }
    }

    /**
     * Returns the notifications rules for a given user.
     */
    List<NotificationRule> getNotificationRules(@UserIdInt int user) {
        synchronized (mLock) {
            return mNotificationRules.getOrDefault(user, new ArrayList<>());
        }
    }

    /**
     * Returns the notification rule for a given user with the given id.
     */
    @Nullable NotificationRule getNotificationRule(@UserIdInt int user, int ruleId) {
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            Optional<NotificationRule> match = rulesForUser.stream().filter(
                    notificationRule -> ruleId == notificationRule.getId()).findFirst();
            return match.get();
        }
    }

    /**
     * Updates a notification rule without changing its position.
     * @return true if the rule was updated, false otherwise
     */
    boolean updateNotificationRule(@UserIdInt int user, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
        AtomicReference<Boolean> appliedChange = new AtomicReference<>(false);
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            rulesForUser.replaceAll(notificationRule -> {
                if (notificationRule.getId() == rule.getId()) {
                    appliedChange.set(true);
                    return rule;
                }
                return notificationRule;
            });
            mNotificationRules.put(user, rulesForUser);
        }
        return appliedChange.get();
    }

    /**
     * Inserts a new rule for a given user at a given position.
     */
    boolean addNotificationRule(@UserIdInt int user, int position, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            position = Math.clamp(position, 0, rulesForUser.size());
            // duplicate ids are not allowed
            if (!rulesForUser.stream().filter(
                            notificationRule -> notificationRule.getId() == rule.getId())
                    .findFirst().isEmpty()) {
                return false;
            }
            rulesForUser.add(position, rule);
            mNotificationRules.put(user, rulesForUser);
        }
        return true;
    }

    /**
     * Removes a rule for a given user with a given id.
     * @return true if there was a rule removed, false otherwise
     */
    boolean removeNotificationRule(@UserIdInt int user, int ruleId) {
        if (NotificationRule.isSystemRule(ruleId)) {
            return false;
        }
        boolean removed = false;
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            removed = rulesForUser.removeIf(notificationRule -> ruleId == notificationRule.getId());
            mNotificationRules.put(user, rulesForUser);
        }
        return removed;
    }

    /**
     * Replaces all rules for the given user.
     */
    void setNotificationRules(int user, List<NotificationRule> rules) {
        // TODO(b/477961511): validate the rules first
        // TODO(b/477961511): don't remove system rules
        synchronized (mLock) {
            mNotificationRules.put(user, new ArrayList<>(rules));
        }
    }

    /**
     * Adds system/NAS rules for the given user.
     */
    void onUserAdded(@UserIdInt int user) {
        int potentialParent = mUmInternal.getProfileParentId(user);
        if (potentialParent == user) {
            synchronized (mLock) {
                mNotificationRules.put(user, getDefaultSystemRules(user));
            }
        }
    }

    /**
     * Clears rule data for the given user
     */
    void onUserRemoved(@UserIdInt int user) {
        synchronized (mLock) {
            mNotificationRules.remove(user);
        }
    }

    /**
     * Validates incoming ids, and denormalizes the single rule adjustment into a list of behavioral
     * adjustments. Each valid ruleId in the provided Adjustment will turn into an Adjustment with
     * a collection of behavioral signals to apply. The list of Adjustments will be provided
     * in priority order from highest to lowest priority.
     */
    List<Adjustment> getAdjustmentsForRules(@UserIdInt int user, Adjustment ruleIdAdjustment) {
        List<Integer> ruleIds = ruleIdAdjustment.getSignals().getIntegerArrayList(
                Adjustment.KEY_NOTIFICATION_RULES);
        List<Adjustment> behavioralAdjustments = new ArrayList<>();
        synchronized (mLock) {
            List<NotificationRule> matchingRules =
                    mNotificationRules.getOrDefault(user, new ArrayList<>())
                            .stream().filter(notificationRule ->
                                    ruleIds.contains(notificationRule.getId())
                                            && notificationRule.isEnabled())
                            .toList();
            for (NotificationRule rule : matchingRules) {
                NotificationRule.Action action = rule.getAction();
                Bundle signals = new Bundle();
                switch (action.getPrimaryAction()) {
                    case PRIMARY_ACTION_HIGHLIGHT:
                        signals.putBoolean(KEY_HIGHLIGHT, true);
                        // TODO(b/438704204): Add highlight default behaviors
                        break;
                    case PRIMARY_ACTION_DEFAULT:
                        signals.putInt(KEY_IMPORTANCE, IMPORTANCE_DEFAULT);
                        break;
                    case NotificationRule.Action.PRIMARY_ACTION_LOW:
                        signals.putInt(KEY_IMPORTANCE, IMPORTANCE_LOW);
                        break;
                    case PRIMARY_ACTION_BUNDLE:
                        signals.putInt(KEY_TYPE, rule.getId());
                        break;
                    case PRIMARY_ACTION_BLOCK:
                        signals.putInt(KEY_IMPORTANCE, IMPORTANCE_NONE);
                        break;
                    default:
                        break;
                }
                addActionOverrideBehaviors(action, signals);
                Adjustment adjustment = new Adjustment(ruleIdAdjustment.getPackage(),
                        ruleIdAdjustment.getKey(), signals, null, ruleIdAdjustment.getUserHandle());
                // TODO(b/477961511): does this really matter? or just the 'type'?
                //adjustment.setOriginatingRuleOrder();
                adjustment.setOriginatingRuleId(rule.getId());
                behavioralAdjustments.add(adjustment);
            }
        }
        return behavioralAdjustments;
    }

    private void addActionOverrideBehaviors(NotificationRule.Action action, Bundle signals) {
        if (action.getLightColorOverride() != 0) {
            signals.putInt(KEY_LIGHT, action.getLightColorOverride());
        }
        if (action.getSoundHapticOverride() != null) {
            signals.putParcelable(KEY_SOUND, action.getSoundHapticOverride());
        }
        if (!action.getModeBreakthroughIds().isEmpty()) {
            signals.putStringArrayList(KEY_MODE_BREAKTHROUGHS,
                    new ArrayList<>(action.getModeBreakthroughIds()));
        }
    }

    /**
     * Disables all NAS owned rules and deletes all user owned rules when the NAS is disabled.
     */
    void onNotificationAssistantChanged(@UserIdInt int user) {
        synchronized (mLock) {
            List<NotificationRule> rulesForUser =
                    mNotificationRules.getOrDefault(user, new ArrayList<>());
            rulesForUser.removeIf(notificationRule ->
                    !(RESERVED_ID_PRIORITY_CONVERSATIONS == notificationRule.getId()
                            || RESERVED_ID_PROMOTED == notificationRule.getId()));
            mNotificationRules.put(user, rulesForUser);
        }
    }

    private ArrayList<NotificationRule> getDefaultSystemRules(@UserIdInt int userId) {
        ArrayList<NotificationRule> rules = new ArrayList<>();
        NotificationRule promoted = new NotificationRule.Builder(RESERVED_ID_PROMOTED, "Promoted")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(false)
                .setEditIntentAction("android.settings.MANAGE_APP_POST_PROMOTED_NOTIFICATIONS")
                .addFilter(new NotificationRule.Filter.Builder().setFlags(FLAG_PROMOTED_ONGOING)
                        .build())
                .build();
        rules.add(promoted);
        NotificationRule convos = new NotificationRule.Builder(RESERVED_ID_PRIORITY_CONVERSATIONS,
                "Conversations")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(false)
                .setEditIntentAction("android.settings.CONVERSATION_SETTINGS")
                .addFilter(new NotificationRule.Filter.Builder().setConversationLevel(
                        CONVERSATION_LEVEL_PRIORITY).build())
                .build();
        rules.add(convos);
        NotificationRule bundle = new NotificationRule.Builder(RESERVED_ID_STATIC_BUNDLES, "Bundle")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build())
                .setEditIntentAction("android.settings.NOTIFICATION_BUNDLES")
                .addFilter(new NotificationRule.Filter.Builder()
                        .setStaticBundleTypes(List.of(DEFAULT_ALLOWED_ADJUSTMENT_KEY_TYPES))
                        // bundles are only enabled for primary users by default
                        .addUser(UserHandle.of(userId))
                        .build())
                .setCanBeDisabled(true)
                .build();
        rules.add(bundle);
        NotificationRule important = new NotificationRule.Builder(
                RESERVED_ID_IMPORTANT_NOTIFICATIONS, "Important")
                .setAction(new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build())
                .setCanBeDisabled(true)
                .build();
        rules.add(important);

        return rules;
    }
}
