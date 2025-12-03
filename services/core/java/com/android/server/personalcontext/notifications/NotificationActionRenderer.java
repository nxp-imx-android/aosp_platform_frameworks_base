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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.Adjustment;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightCollection;
import android.service.personalcontext.insight.InsightDisplayDetails;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;
import com.android.server.personalcontext.notifications.ContextActionResolver.ResolutionResult;
import com.android.server.personalcontext.util.InsightRouter;
import com.android.server.personalcontext.util.InsightVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link Renderer} that adds contextual {@link android.app.Notification.Action}s or text replies
 * to a {@link StatusBarNotification} based on a given {@link ContextInsight}.
 *
 * <p>This renderer inspects the provided {@link ContextInsight} to identify the target notification
 * and the type of action to be added. It then constructs the appropriate action or reply and
 * applies it to the notification using an {@link Adjustment}. This renderer can handle a single
 * {@link ActionableInsight} or an {@link InsightCollection} of them. When processing an {@link
 * InsightCollection}, it groups insights by notification and creates a single {@link Adjustment}
 * per notification.
 *
 * @hide
 */
public class NotificationActionRenderer implements Renderer {
    private static final String TAG = "NotifActionRenderer";

    static final int MAX_NOTIFICATION_ACTIONS = 4;
    static final int MAX_TEXT_REPLIES = 5;
    static final int MAX_RECURSION_DEPTH = 10;

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Explanations are not used by the system, therefore we do not provide one. */
    private static final String NO_EXPLANATION = "";

    private final NotificationManagerInternal mNotificationManagerInternal;
    private final ContextActionResolver mActionResolver;
    private final PackageManager mPackageManager;
    private final Context mContext;
    private final InsightRouter mInsightRouter;
    private final UUID mComponentId = UUID.randomUUID();

    @VisibleForTesting
    public NotificationActionRenderer(
            Context context,
            NotificationManagerInternal nmi,
            PackageManager pm,
            ContextActionResolver actionResolver,
            InsightRouter insightRouter) {
        mContext = context;
        mNotificationManagerInternal = nmi;
        mPackageManager = pm;
        mActionResolver = actionResolver;
        mInsightRouter = insightRouter;
    }

    public NotificationActionRenderer(
            Context context, NotificationManagerInternal nmi, PackageManager pm) {
        this(context, nmi, pm, new ContextActionResolver(context), new InsightRouter());
    }

    @Nullable
    private static StatusBarNotification getSbnFromInsight(ContextInsight insight) {
        for (ContextHint hint : ContextHintWithSignature.unwrapList(insight.getOriginHints())) {
            if (hint instanceof NotificationHint notificationHint
                    && notificationHint.getNotificationEvent()
                            instanceof NotificationEnqueuedEvent enqueuedEvent) {
                return enqueuedEvent.getStatusBarNotification();
            }
        }
        return null;
    }

    @Override
    public void render(@NonNull ContextInsight insight) {
        if (mNotificationManagerInternal == null) {
            Slog.e(TAG, "NotificationManagerInternal not found.");
            return;
        }

        final Map<String, InsightGroup> insightsByNotificationKey = new HashMap<>();
        final InsightCollector collector =
                new InsightCollector(insightsByNotificationKey, mInsightRouter);
        mInsightRouter.dispatch(insight, collector);

        if (insightsByNotificationKey.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, "No relevant insights to render from: " + insight);
            }
            return;
        }

        List<Adjustment> adjustments = createAdjustments(insightsByNotificationKey);

        if (!adjustments.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, "Creating adjustments: " + adjustments);
            }
            mNotificationManagerInternal.requestSystemAdjustments(adjustments);
        }
    }

    /**
     * Creates a list of {@link Adjustment}s from a map of grouped insights.
     *
     * @param insightsByNotificationKey A map of insights grouped by notification key.
     * @return A list of {@link Adjustment}s to be applied.
     */
    @NonNull
    private List<Adjustment> createAdjustments(
            @NonNull Map<String, InsightGroup> insightsByNotificationKey) {
        final List<Adjustment> adjustments = new ArrayList<>();
        for (final InsightGroup insightGroup : insightsByNotificationKey.values()) {
            final Adjustment adjustment = createAdjustmentForInsightGroup(insightGroup);
            if (adjustment != null) {
                adjustments.add(adjustment);
            }
        }
        return adjustments;
    }

    /**
     * Creates an {@link Adjustment} for a group of {@link ContextInsight}s that belong to the same
     * notification.
     *
     * @param insightGroup A group of {@link ContextInsight}s for a single notification.
     * @return An {@link Adjustment} containing all the generated actions and replies, or {@code
     *     null} if none could be created.
     */
    @Nullable
    private Adjustment createAdjustmentForInsightGroup(@NonNull InsightGroup insightGroup) {
        if (insightGroup.mActionableInsights.isEmpty() && insightGroup.mDisplayInsights.isEmpty()) {
            return null;
        }

        final StatusBarNotification sbn = insightGroup.mSbn;
        final List<Notification.Action> notificationActions = new ArrayList<>();
        for (final ActionableInsight actionableInsight : insightGroup.mActionableInsights) {
            if (notificationActions.size() >= MAX_NOTIFICATION_ACTIONS) {
                Slog.w(
                        TAG,
                        "Max number of actions reached. Skipping insight: " + actionableInsight);
                break;
            }
            final Notification.Action action = createNotificationAction(actionableInsight);
            if (action != null) {
                notificationActions.add(action);
            }
        }

        final List<CharSequence> textReplies = new ArrayList<>();
        for (final DisplayInsight displayInsight : insightGroup.mDisplayInsights) {
            if (textReplies.size() >= MAX_TEXT_REPLIES) {
                Slog.w(TAG, "Max number of replies reached. Skipping insight: " + displayInsight);
                break;
            }
            final CharSequence reply = displayInsight.getDetails().getTitle();
            if (reply != null) {
                textReplies.add(reply);
            }
        }

        if (notificationActions.isEmpty() && textReplies.isEmpty()) {
            Slog.w(
                    TAG,
                    "Could not create any notification actions or replies for sbn: "
                            + sbn.getKey());
            return null;
        }

        return createAdjustment(sbn, notificationActions, textReplies);
    }

    /**
     * Creates a {@link Notification.Action} from the provided {@link ActionableInsight}.
     *
     * <p>This method extracts the action intent, title, and icon from the insight. If the title or
     * icon are not available in the insight, it falls back to using the application's label and
     * icon.
     *
     * @param insight The insight containing the details for the action.
     * @return A {@link Notification.Action} if it can be created, or {@code null} otherwise.
     */
    @Nullable
    private Notification.Action createNotificationAction(ActionableInsight insight) {
        final InsightDisplayDetails displayDetails = insight.getDisplayDetails();
        final boolean needsComponentInfo =
                displayDetails.getTitle() == null || displayDetails.getIcon() == null;

        final ResolutionResult resolutionResult =
                mActionResolver.resolveActionIntent(insight, needsComponentInfo);

        if (resolutionResult == null || resolutionResult.pendingIntent == null) {
            return null;
        }

        if (needsComponentInfo
                && (resolutionResult.resolveInfo == null
                        || resolutionResult.actionType == ActionType.UNKNOWN)) {
            Slog.w(TAG, "Needed component info but could not resolve it.");
            return null;
        }

        final PendingIntent pendingIntent = resolutionResult.pendingIntent;
        final ResolveInfo resolveInfo = resolutionResult.resolveInfo;
        final ActionType actionType = resolutionResult.actionType;
        final ComponentInfo componentInfo =
                resolveInfo != null ? resolveInfo.getComponentInfo() : null;

        if (needsComponentInfo && componentInfo == null) {
            Slog.w(TAG, "Missing title/icon, and component info is null.");
            return null;
        }

        final Icon icon = getIconOrDefault(displayDetails.getIcon(), componentInfo);

        // TODO(b/460848566): icon is not included for some CUJs, handle gracefully
        if (icon == null) {
            Slog.w(
                    TAG,
                    "Could not get icon to create notification action for "
                            + (componentInfo != null ? componentInfo.packageName : "unknown"));
            return null;
        }

        final CharSequence title =
                getTitleOrDefault(displayDetails.getTitle(), componentInfo, actionType);

        final Bundle extras = new Bundle();
        final CharSequence contentDescription = displayDetails.getContentDescription();
        if (contentDescription != null) {
            extras.putCharSequence(
                    Notification.Action.EXTRA_CONTENT_DESCRIPTION, contentDescription);
        }
        extras.putBoolean(Notification.Action.EXTRA_IS_ANIMATED, true);

        return new Notification.Action.Builder(icon, title, pendingIntent)
                .setContextual(true)
                .addExtras(extras)
                .build();
    }

    @NonNull
    private CharSequence getTitleOrDefault(
            @Nullable CharSequence title,
            @Nullable ComponentInfo componentInfo,
            @NonNull ActionType actionType) {
        if (title != null) {
            return title;
        }
        if (componentInfo == null) {
            return "";
        }
        final CharSequence appLabel =
                mPackageManager.getApplicationLabel(componentInfo.applicationInfo);
        if (actionType == ActionType.ACTIVITY) {
            return mContext.getString(com.android.internal.R.string.open_app_name, appLabel);
        }
        return appLabel;
    }

    @Nullable
    private Icon getIconOrDefault(@Nullable Icon icon, @Nullable ComponentInfo componentInfo) {
        if (icon != null) {
            return icon;
        }
        if (componentInfo == null) {
            return null;
        }
        int iconRes = componentInfo.getIconResource();
        if (iconRes == 0) {
            iconRes = componentInfo.applicationInfo.icon;
        }
        if (iconRes != 0) {
            return Icon.createWithResource(componentInfo.packageName, iconRes);
        }
        return null;
    }

    private Adjustment createAdjustment(
            StatusBarNotification sbn,
            List<Notification.Action> actions,
            List<CharSequence> textReplies) {
        final Bundle signals = new Bundle();

        if (!actions.isEmpty()) {
            signals.putParcelableArrayList(
                    Adjustment.KEY_CONTEXTUAL_ACTIONS, new ArrayList<>(actions));
        }

        if (!textReplies.isEmpty()) {
            signals.putCharSequenceArrayList(
                    Adjustment.KEY_TEXT_REPLIES, new ArrayList<>(textReplies));
        }

        return new Adjustment(
                sbn.getPackageName(), sbn.getKey(), signals, NO_EXPLANATION, sbn.getUser());
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Override
    public boolean isInterestedInInsight(ContextInsight insight) {
        // Notifications should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    private static class InsightGroup {
        final StatusBarNotification mSbn;
        final List<ActionableInsight> mActionableInsights = new ArrayList<>();
        final List<DisplayInsight> mDisplayInsights = new ArrayList<>();

        InsightGroup(StatusBarNotification sbn) {
            mSbn = sbn;
        }
    }

    private static class InsightCollector implements InsightVisitor {
        private final Map<String, InsightGroup> mInsightsByNotificationKey;
        private final InsightRouter mInsightRouter;
        private int mDepth;

        InsightCollector(
                Map<String, InsightGroup> insightsByNotificationKey, InsightRouter insightRouter) {
            this.mInsightsByNotificationKey = insightsByNotificationKey;
            this.mInsightRouter = insightRouter;
            this.mDepth = 0;
        }

        private InsightGroup getOrCreateGroup(ContextInsight insight) {
            if (mDepth >= MAX_RECURSION_DEPTH) {
                Slog.w(TAG, "Max recursion depth reached. Skipping insight: " + insight);
                return null;
            }
            final StatusBarNotification sbn = getSbnFromInsight(insight);
            if (sbn != null) {
                return mInsightsByNotificationKey.computeIfAbsent(
                        sbn.getKey(), k -> new InsightGroup(sbn));
            } else if (DEBUG) {
                Slog.d(TAG, "Skipping insight, SBN not found: " + insight);
            }
            return null;
        }

        @Override
        public void visit(ActionableInsight insight) {
            final InsightGroup group = getOrCreateGroup(insight);
            if (group != null) {
                group.mActionableInsights.add(insight);
            }
        }

        @Override
        public void visit(DisplayInsight insight) {
            final InsightGroup group = getOrCreateGroup(insight);
            if (group != null) {
                group.mDisplayInsights.add(insight);
            }
        }

        @Override
        public void visit(InsightCollection collection) {
            if (mDepth >= MAX_RECURSION_DEPTH) {
                Slog.w(TAG, "Max recursion depth reached. Skipping insight: " + collection);
                return;
            }
            mDepth++;
            for (ContextInsight insight : collection) {
                mInsightRouter.dispatch(insight, this);
            }
            mDepth--;
        }

        @Override
        public void visitUnknown(ContextInsight insight) {
            if (DEBUG) {
                Slog.d(TAG, "Unknown insight type, ignoring: " + insight);
            }
        }
    }
}
