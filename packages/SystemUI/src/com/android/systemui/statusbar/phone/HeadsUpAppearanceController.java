/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.util.MathUtils;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.shade.ShadeHeadsUpTracker;
import com.android.systemui.shade.ShadeViewController;
import com.android.systemui.statusbar.HeadsUpStatusBarView;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.SourceType;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager;
import com.android.systemui.statusbar.notification.headsup.OnHeadsUpChangedListener;
import com.android.systemui.statusbar.notification.headsup.PinnedStatus;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.shared.AsyncGroupHeaderViewInflation;
import com.android.systemui.statusbar.notification.stack.NotificationRoundnessManager;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarScope;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.ViewController;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * Controls the appearance of heads up notifications in the icon area and the header itself.
 * It also controls the roundness of the heads up notifications and the pulsing notifications.
 */
@HomeStatusBarScope
public class HeadsUpAppearanceController extends ViewController<HeadsUpStatusBarView>
        implements OnHeadsUpChangedListener,
        NotificationWakeUpCoordinator.WakeUpListener {
    private static final SourceType HEADS_UP = SourceType.from("HeadsUp");
    private static final SourceType PULSING = SourceType.from("Pulsing");
    private final HeadsUpManager mHeadsUpManager;
    private final NotificationStackScrollLayoutController mStackScrollerController;

    private final ShadeViewController mShadeViewController;
    private final NotificationRoundnessManager mNotificationRoundnessManager;
    private final Consumer<ExpandableNotificationRow>
            mSetTrackingHeadsUp = this::setTrackingHeadsUp;
    private final BiConsumer<Float, Float> mSetExpandedHeight = this::setAppearFraction;
    private final KeyguardBypassController mBypassController;
    private final StatusBarStateController mStatusBarStateController;
    private final PhoneStatusBarTransitions mPhoneStatusBarTransitions;
    private final NotificationWakeUpCoordinator mWakeUpCoordinator;

    @VisibleForTesting
    float mExpandedHeight;
    @VisibleForTesting
    float mAppearFraction;
    private ExpandableNotificationRow mTrackedChild;
    private final KeyguardStateController mKeyguardStateController;

    @VisibleForTesting
    @Inject
    public HeadsUpAppearanceController(
            HeadsUpManager headsUpManager,
            StatusBarStateController stateController,
            PhoneStatusBarTransitions phoneStatusBarTransitions,
            KeyguardBypassController bypassController,
            NotificationWakeUpCoordinator wakeUpCoordinator,
            KeyguardStateController keyguardStateController,
            NotificationStackScrollLayoutController stackScrollerController,
            ShadeViewController shadeViewController,
            NotificationRoundnessManager notificationRoundnessManager,
            // TODO(b/444176294): Delete headsUpStatusBarView now that it's unused.
            HeadsUpStatusBarView headsUpStatusBarView) {
        super(headsUpStatusBarView);
        mNotificationRoundnessManager = notificationRoundnessManager;
        mHeadsUpManager = headsUpManager;

        // We may be mid-HUN-expansion when this controller is re-created (for example, if the user
        // has started pulling down the notification shade from the HUN and then the font size
        // changes). We need to re-fetch these values since they're used to correctly display the
        // HUN during this shade expansion.
        mTrackedChild = shadeViewController.getShadeHeadsUpTracker()
                .getTrackedHeadsUpNotification();
        mAppearFraction = stackScrollerController.getAppearFraction();
        mExpandedHeight = stackScrollerController.getExpandedHeight();

        mStackScrollerController = stackScrollerController;
        mShadeViewController = shadeViewController;
        mStackScrollerController.setHeadsUpAppearanceController(this);
        mBypassController = bypassController;
        mStatusBarStateController = stateController;
        mPhoneStatusBarTransitions = phoneStatusBarTransitions;
        mWakeUpCoordinator = wakeUpCoordinator;
        mKeyguardStateController = keyguardStateController;
    }

    @Override
    protected void onViewAttached() {
        mHeadsUpManager.addListener(this);
        getShadeHeadsUpTracker().addTrackingHeadsUpListener(mSetTrackingHeadsUp);
        getShadeHeadsUpTracker().setHeadsUpAppearanceController(this);
        mStackScrollerController.addOnExpandedHeightChangedListener(mSetExpandedHeight);
    }

    private ShadeHeadsUpTracker getShadeHeadsUpTracker() {
        return mShadeViewController.getShadeHeadsUpTracker();
    }

    @Override
    protected void onViewDetached() {
        mHeadsUpManager.removeListener(this);
        getShadeHeadsUpTracker().removeTrackingHeadsUpListener(mSetTrackingHeadsUp);
        getShadeHeadsUpTracker().setHeadsUpAppearanceController(null);
        mStackScrollerController.removeOnExpandedHeightChangedListener(mSetExpandedHeight);
    }

    @Override
    public void onHeadsUpPinned(NotificationEntry entry) {
        updateHeader(entry.getRow());
        updateHeadsUpAndPulsingRoundness(entry.getRow());
    }

    @Override
    public void onHeadsUpStateChanged(@NonNull NotificationEntry entry, boolean isHeadsUp) {
        updateHeadsUpAndPulsingRoundness(entry.getRow());
        mPhoneStatusBarTransitions.onHeadsUpStateChanged(isHeadsUp);
    }

    // TODO(b/444176294): Remove this method.
    @VisibleForTesting
    public PinnedStatus getPinnedStatus() {
        return PinnedStatus.NotPinned;
    }

    /** True if the device's current state allows us to show HUNs and false otherwise. */
    private boolean canShowHeadsUp() {
        boolean notificationsShown = !mWakeUpCoordinator.getNotificationsFullyHidden();
        if (mBypassController.getBypassEnabled() &&
                (mStatusBarStateController.getState() == StatusBarState.KEYGUARD
                        || mKeyguardStateController.isKeyguardGoingAway())
                && notificationsShown) {
            return true;
        }
        return !isExpanded() && notificationsShown;
    }

    /**
     * True if the headsup status bar view (which has just the HUN icon and app name) should be
     * visible right now and false otherwise.
     *
     * @deprecated use {@link com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor#getStatusBarHeadsUpState()}
     *    instead.
     */
    // TODO(b/444176294): Remove this method.
    @Deprecated
    public boolean shouldHeadsUpStatusBarBeVisible() {
        return false;
    }

    @Override
    public void onHeadsUpUnPinned(NotificationEntry entry) {
        updateHeader(entry.getRow());
        updateHeadsUpAndPulsingRoundness(entry.getRow());
    }

    public void setAppearFraction(float expandedHeight, float appearFraction) {
        boolean changed = expandedHeight != mExpandedHeight;

        mExpandedHeight = expandedHeight;
        mAppearFraction = appearFraction;
        // We only notify if the expandedHeight changed and not on the appearFraction, since
        // otherwise we may run into an infinite loop where the panel and this are constantly
        // updating themselves over just a small fraction
        if (changed) {
            updateHeadsUpHeaders();
        }
    }

    /**
     * Set a headsUp to be tracked, meaning that it is currently being pulled down after being
     * in a pinned state on the top. The expand animation is different in that case and we need
     * to update the header constantly afterwards.
     *
     * @param trackedChild the tracked headsUp or null if it's not tracking anymore.
     */
    public void setTrackingHeadsUp(ExpandableNotificationRow trackedChild) {
        ExpandableNotificationRow previousTracked = mTrackedChild;
        mTrackedChild = trackedChild;
        if (previousTracked != null) {
            updateHeader(previousTracked);
            updateHeadsUpAndPulsingRoundness(previousTracked);
        }
    }

    private boolean isExpanded() {
        return mExpandedHeight > 0;
    }

    private void updateHeadsUpHeaders() {
        mHeadsUpManager.getAllEntries().forEach(entry -> {
            updateHeader(entry.getRow());
            updateHeadsUpAndPulsingRoundness(entry.getRow());
        });
    }

    public void updateHeader(ExpandableNotificationRow row) {
        float headerVisibleAmount = 1.0f;
        // To fix the invisible HUN group header issue
        if (!AsyncGroupHeaderViewInflation.isEnabled()) {
            if (row.isPinned() || row.isHeadsUpAnimatingAway() || row == mTrackedChild
                    || row.showingPulsing()) {
                headerVisibleAmount = mAppearFraction;
            }
        }
        row.setHeaderVisibleAmount(headerVisibleAmount);
    }

    /**
     * Update the HeadsUp and the Pulsing roundness based on current state
     * @param row target notification row
     */
    public void updateHeadsUpAndPulsingRoundness(ExpandableNotificationRow row) {
        boolean isTrackedChild = row == mTrackedChild;
        if (row.isPinned() || row.isHeadsUpAnimatingAway() || isTrackedChild) {
            float roundness = MathUtils.saturate(1f - mAppearFraction);
            row.requestRoundness(roundness, roundness, HEADS_UP);
        } else {
            row.requestRoundnessReset(HEADS_UP);
        }
        if (mNotificationRoundnessManager.shouldRoundNotificationPulsing()) {
            if (row.showingPulsing()) {
                row.requestRoundness(/* top = */ 1f, /* bottom = */ 1f, PULSING);
            } else {
                row.requestRoundnessReset(PULSING);
            }
        }
    }
}
