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

package com.android.systemui.communal;

import static com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_ASLEEP;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.shared.model.DozeStateModel;
import com.android.systemui.shared.condition.Condition;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.kotlin.JavaAdapter;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

import javax.inject.Inject;

/**
 * Condition which estimates device inactivity in order to avoid launching a full-screen activity
 * while the user is actively using the device.
 */
public class DeviceInactiveCondition extends Condition {
    private final KeyguardStateController mKeyguardStateController;
    private final WakefulnessLifecycle mWakefulnessLifecycle;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final KeyguardInteractor mKeyguardInteractor;
    private final JavaAdapter mJavaAdapter;
    private Job mAnyDozeListenerJob;
    private boolean mAnyDoze;
    private final KeyguardStateController.Callback mKeyguardStateCallback =
            new KeyguardStateController.Callback() {
                @Override
                public void onKeyguardShowingChanged() {
                    updateState();
                }
            };
    private final WakefulnessLifecycle.Observer mWakefulnessObserver =
            new WakefulnessLifecycle.Observer() {
                @Override
                public void onStartedGoingToSleep() {
                    updateState();
                }
            };
    private final KeyguardUpdateMonitorCallback mKeyguardUpdateCallback =
            new KeyguardUpdateMonitorCallback() {
                @Override
                public void onDreamingStateChanged(boolean dreaming) {
                    updateState();
                }
            };

    @Inject
    public DeviceInactiveCondition(@Application CoroutineScope scope,
            KeyguardStateController keyguardStateController,
            WakefulnessLifecycle wakefulnessLifecycle, KeyguardUpdateMonitor keyguardUpdateMonitor,
            KeyguardInteractor keyguardInteractor, JavaAdapter javaAdapter) {
        super(scope);
        mKeyguardStateController = keyguardStateController;
        mWakefulnessLifecycle = wakefulnessLifecycle;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mKeyguardInteractor = keyguardInteractor;
        mJavaAdapter = javaAdapter;
    }

    @Override
    protected void start() {
        updateState();
        mKeyguardStateController.addCallback(mKeyguardStateCallback);
        mKeyguardUpdateMonitor.registerCallback(mKeyguardUpdateCallback);
        mWakefulnessLifecycle.addObserver(mWakefulnessObserver);
        mAnyDozeListenerJob = mJavaAdapter.alwaysCollectFlow(
                mKeyguardInteractor.getDozeTransitionModel(), dozeModel -> {
                    mAnyDoze = !DozeStateModel.Companion.isDozeOff(dozeModel.getTo());
                    updateState();
                });
    }

    @Override
    protected void stop() {
        mKeyguardStateController.removeCallback(mKeyguardStateCallback);
        mKeyguardUpdateMonitor.removeCallback(mKeyguardUpdateCallback);
        mWakefulnessLifecycle.removeObserver(mWakefulnessObserver);
        mAnyDozeListenerJob.cancel(null);
    }

    @Override
    protected int getStartStrategy() {
        return START_EAGERLY;
    }

    private void updateState() {
        final boolean asleep = mWakefulnessLifecycle.getWakefulness() == WAKEFULNESS_ASLEEP;
        // Doze/AoD is also a dream, but we should never override it with low light as to the user
        // it's totally unrelated.
        updateCondition(!mAnyDoze && (asleep || mKeyguardStateController.isShowing()
                || mKeyguardUpdateMonitor.isDreaming()));
    }
}
