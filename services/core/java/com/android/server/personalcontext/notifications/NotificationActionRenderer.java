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

import android.annotation.Nullable;
import android.annotation.NonNull;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.Adjustment;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.InsightDisplayDetails;
import android.util.Slog;

import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.component.Renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link Renderer} that adds contextual {@link android.app.Notification.Action}s to a {@link
 * StatusBarNotification} based on a given {@link ContextInsight}.
 *
 * <p>This renderer inspects the provided {@link ContextInsight} to identify the target notification
 * and the type of action to be added. It then constructs the appropriate action and applies it to
 * the notification using an {@link Adjustment}.
 *
 * @hide
 */
public class NotificationActionRenderer implements Renderer {
    private static final String TAG = "NotificationActionRenderer";

    /** Explanations are not used by the system, therefore we do not provide one. */
    private static final String NO_EXPLANATION = "";

    private final NotificationManagerInternal mNotificationManagerInternal;
    private final PackageManager mPackageManager;
    private final Context mContext;
    private final UUID mComponentId = UUID.randomUUID();

    public NotificationActionRenderer(
            Context context, NotificationManagerInternal nmi, PackageManager pm) {
        mContext = context;
        mNotificationManagerInternal = nmi;
        mPackageManager = pm;
    }

    @Nullable
    private StatusBarNotification getSbnFromInsight(ContextInsight insight) {
        for (ContextHint hint : insight.getOriginHints()) {
            if (hint instanceof NotificationHint notificationHint
                    && notificationHint.getNotificationEvent()
                            instanceof NotificationEnqueuedEvent enqueuedEvent) {
                return enqueuedEvent.getStatusBarNotification();
            }
        }
        return null;
    }

    @Override
    public void render(@NonNull ContextInsight insight, boolean alreadyRendered) {
        if (!(insight instanceof ActionableInsight actionableInsight)) {
            return;
        }

        if (mNotificationManagerInternal == null) {
            Slog.e(TAG, "NotificationManagerInternal not found.");
            return;
        }

        final StatusBarNotification sbn = getSbnFromInsight(actionableInsight);
        if (sbn == null) {
            Slog.w(TAG, "Could not find SBN for insight: " + actionableInsight);
            return;
        }

        final UserHandle user = sbn.getUser();
        final Notification.Action notificationAction =
                createNotificationAction(actionableInsight, user);
        if (notificationAction == null) {
            Slog.w(
                    TAG,
                    "Could not create notification action for insight: "
                            + actionableInsight
                            + " for user: "
                            + user);
            return;
        }
        final Adjustment adjustment = createAdjustment(sbn, notificationAction);
        mNotificationManagerInternal.requestSystemAdjustments(List.of(adjustment));
    }

    /**
     * Creates a {@link Notification.Action} from the provided {@link ActionableInsight}.
     *
     * <p>This method extracts the action intent, title, and icon from the insight. If the title or
     * icon are not available in the insight, it falls back to using the application's label and
     * icon.
     *
     * @param insight The insight containing the details for the action.
     * @param user The user for whom the action is being created.
     * @return A {@link Notification.Action} if it can be created, or {@code null} otherwise.
     */
    @Nullable
    private Notification.Action createNotificationAction(
            ActionableInsight insight, UserHandle user) {
        final Intent actionIntent = insight.createActionIntent();
        final ActivityInfo activityInfo = getActivityInfo(actionIntent, user);

        if (activityInfo == null) {
            return null;
        }

        final InsightDisplayDetails displayDetails = insight.getDisplayDetails();

        final Icon icon = getIconOrDefault(displayDetails.getIcon(), activityInfo);

        if (icon == null) {
            Slog.w(
                    TAG,
                    "Could not get icon to create notification action for "
                            + activityInfo.packageName);
            return null;
        }

        final CharSequence title = getTitleOrDefault(displayDetails.getTitle(), activityInfo);

        final PendingIntent pendingIntent =
                PendingIntent.getActivityAsUser(
                        mContext,
                        /* requestCode= */ actionIntent.hashCode(),
                        actionIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                        /* options= */ null,
                        user);

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
            @Nullable CharSequence title, @NonNull ActivityInfo activityInfo) {
        if (title != null) {
            return title;
        }
        final CharSequence appLabel =
                mPackageManager.getApplicationLabel(activityInfo.applicationInfo);
        return mContext.getString(com.android.internal.R.string.open_app_name, appLabel);
    }

    @Nullable
    private ActivityInfo getActivityInfo(Intent actionIntent, UserHandle user) {
        final List<ResolveInfo> resolveInfos =
                mPackageManager.queryIntentActivitiesAsUser(
                        actionIntent, PackageManager.MATCH_ALL, user.getIdentifier());
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            Slog.w(
                    TAG,
                    "Could not resolve action intent to get app info: "
                            + actionIntent
                            + " for user: "
                            + user);
            return null;
        }
        return resolveInfos.get(0).activityInfo;
    }

    @Nullable
    private Icon getIconOrDefault(@Nullable Icon icon, @NonNull ActivityInfo activityInfo) {
        if (icon != null) {
            return icon;
        }
        return Icon.createWithResource(activityInfo.packageName, activityInfo.getIconResource());
    }

    private Adjustment createAdjustment(StatusBarNotification sbn, Notification.Action action) {
        final Bundle signals = new Bundle();
        final ArrayList<Notification.Action> actions = new ArrayList<>();
        actions.add(action);
        signals.putParcelableArrayList(Adjustment.KEY_CONTEXTUAL_ACTIONS, actions);

        return new Adjustment(
                sbn.getPackageName(), sbn.getKey(), signals, NO_EXPLANATION, sbn.getUser());
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Override
    public boolean isInsightInteresting(ContextInsight insight) {
        return insight instanceof ActionableInsight && getSbnFromInsight(insight) != null;
    }
}
