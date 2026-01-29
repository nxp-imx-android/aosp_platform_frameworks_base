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
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.service.notification.Adjustment.KEY_HIGHLIGHT;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_LIGHT;
import static android.service.notification.Adjustment.KEY_MODE_BREAKTHROUGHS;
import static android.service.notification.Adjustment.KEY_SOUND;
import static android.service.notification.Adjustment.KEY_TYPE;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.NotificationRule;
import android.app.backup.BackupRestoreEventLogger;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.Adjustment;
import android.util.ArrayMap;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * This class manages each user's {@link NotificationRule}s. It also provides some helper methods
 * to convert and sort Adjustments according to rule behavior and priority.
 */
public class NotificationRuleManager {
    private Context mContext;
    private NotificationManagerPrivate mNmPrivate;

    // key: user id. value: notification rules in priority order
    private final Map<Integer, List<NotificationRule>> mNotificationRules = new ArrayMap<>();

    public NotificationRuleManager(Context context, NotificationManagerPrivate nmPrivate) {
        mContext = context;
        mNmPrivate = nmPrivate;
    }

    // for bugreports
    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        // TODO(b/477961511)
    }

    // for backup and restore
    boolean readXml(TypedXmlPullParser parser, boolean forRestore, int userId,
            @Nullable BackupRestoreEventLogger logger) {
        // TODO(b/477961511)
        return true;
    }

    void writeXml(TypedXmlSerializer out, boolean forBackup,int userId,
            @Nullable BackupRestoreEventLogger logger, boolean useLegacyBundleStorage) {
        // TODO(b/477961511)
    }

    /**
     * Returns the notifications rules for a given user.
     */
    List<NotificationRule> getNotificationRules(@UserIdInt int user) {
        return mNotificationRules.getOrDefault(user, new ArrayList<>());
    }

    /**
     * Returns the notification rule for a given user with the given id.
     */
    @Nullable NotificationRule getNotificationRule(@UserIdInt int user, int ruleId) {
        List<NotificationRule> rulesForUser =
                mNotificationRules.getOrDefault(user, new ArrayList<>());
        Optional<NotificationRule> match = rulesForUser.stream().filter(
                notificationRule -> ruleId == notificationRule.getId()).findFirst();
        return match.get();
    }

    /**
     * Updates a notification rule without changing its position.
     * @return true if the rule was updated, false otherwise
     */
    boolean updateNotificationRule(@UserIdInt int user, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
        AtomicReference<Boolean> appliedChange = new AtomicReference<>(false);
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
        return appliedChange.get();
    }

    /**
     * Inserts a new rule for a given user at a given position.
     */
    boolean addNotificationRule(@UserIdInt int user, int position, NotificationRule rule) {
        // TODO(b/477961511): validate the rule first
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
        return true;
    }

    /**
     * Removes a rule for a given user with a given id.
     * @return true if there was a rule removed, false otherwise
     */
    boolean removeNotificationRule(@UserIdInt int user, int ruleId) {
        if (ruleId == RESERVED_ID_PROMOTED || ruleId == RESERVED_ID_PRIORITY_CONVERSATIONS
                || ruleId == RESERVED_ID_IMPORTANT_NOTIFICATIONS
                || ruleId == RESERVED_ID_STATIC_BUNDLES) {
            return false;
        }
        boolean removed = false;
        List<NotificationRule> rulesForUser =
                mNotificationRules.getOrDefault(user, new ArrayList<>());
        removed = rulesForUser.removeIf(notificationRule -> ruleId == notificationRule.getId());
        mNotificationRules.put(user, rulesForUser);

        return removed;
    }

    /**
     * Replaces all rules for the given user.
     */
    void setNotificationRules(int user, List<NotificationRule> rules) {
        // TODO(b/477961511): validate the rules first
        // TODO(b/477961511): don't remove system rules
        mNotificationRules.put(user, new ArrayList<>(rules));
    }

    /**
     * Adds system/NAS rules for the given user.
     */
    void onUserAdded(@UserIdInt int user) {
        // TODO(b/477961511): add system rules
    }

    /**
     * Clears rule data for the given user
     */
    void onUserRemoved(@UserIdInt int user) {
        mNotificationRules.remove(user);
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
                case NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT:
                    signals.putBoolean(KEY_HIGHLIGHT, true);
                    // TODO(b/438704204): Add highlight default behaviors
                    break;
                case NotificationRule.Action.PRIMARY_ACTION_DEFAULT:
                    signals.putInt(KEY_IMPORTANCE, IMPORTANCE_DEFAULT);
                    break;
                case NotificationRule.Action.PRIMARY_ACTION_LOW:
                    signals.putInt(KEY_IMPORTANCE, IMPORTANCE_LOW);
                    break;
                case NotificationRule.Action.PRIMARY_ACTION_BUNDLE:
                    signals.putInt(KEY_TYPE, rule.getId());
                    break;
                case NotificationRule.Action.PRIMARY_ACTION_BLOCK:
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
        List<NotificationRule> rulesForUser =
                mNotificationRules.getOrDefault(user, new ArrayList<>());
        rulesForUser.removeIf(notificationRule ->
                !(RESERVED_ID_PRIORITY_CONVERSATIONS == notificationRule.getId()
                || RESERVED_ID_PROMOTED == notificationRule.getId()));
        mNotificationRules.put(user, rulesForUser);
    }
}
