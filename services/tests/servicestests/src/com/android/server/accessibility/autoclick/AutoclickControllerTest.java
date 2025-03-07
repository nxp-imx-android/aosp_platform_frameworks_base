/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.testutils.MockitoUtilsKt.eq;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.accessibility.util.AccessibilityUtils;
import com.android.server.accessibility.AccessibilityTraceManager;
import com.android.server.accessibility.BaseEventStreamTransformation;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickController}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickControllerTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    private TestableLooper mTestableLooper;
    @Mock private AccessibilityTraceManager mMockTrace;
    @Mock private WindowManager mMockWindowManager;
    private AutoclickController mController;

    private static class MotionEventCaptor extends BaseEventStreamTransformation {
        public MotionEvent downEvent;

        @Override
        public void onMotionEvent(MotionEvent event, MotionEvent rawEvent, int policyFlags) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downEvent = event;
                    break;
            }
        }
    }

    @Before
    public void setUp() {
        mTestableLooper = TestableLooper.get(this);
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mController =
                new AutoclickController(mTestableContext, mTestableContext.getUserId(), mMockTrace);
    }

    @After
    public void tearDown() {
        mController.onDestroy();
        mTestableLooper.processAllMessages();
        TestableLooper.remove(this);
    }

    @Test
    public void onMotionEvent_lazyInitClickScheduler() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mClickScheduler).isNotNull();
    }

    @Test
    public void onMotionEvent_nonMouseSource_notInitClickScheduler() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeNonMouseActionHoverMoveEvent();

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    public void onMotionEvent_lazyInitAutoclickSettingsObserver() {
        assertThat(mController.mAutoclickSettingsObserver).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickSettingsObserver).isNotNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickIndicatorScheduler() {
        assertThat(mController.mAutoclickIndicatorScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorScheduler).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickIndicatorScheduler() {
        assertThat(mController.mAutoclickIndicatorScheduler).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorScheduler).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickIndicatorView() {
        assertThat(mController.mAutoclickIndicatorView).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorView).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickIndicatorView() {
        assertThat(mController.mAutoclickIndicatorView).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickIndicatorView).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickTypePanelView() {
        assertThat(mController.mAutoclickTypePanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickTypePanel).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickTypePanelView() {
        assertThat(mController.mAutoclickTypePanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickTypePanel).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_addAutoclickIndicatorViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();

        verify(mMockWindowManager).addView(eq(mController.mAutoclickIndicatorView), any());
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_removeAutoclickIndicatorViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        verify(mMockWindowManager).removeView(mController.mAutoclickIndicatorView);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_removeAutoclickTypePanelViewToWindowManager() {
        injectFakeMouseActionHoverMoveEvent();
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        mController.onDestroy();

        verify(mockAutoclickTypePanel).hide();
    }

    @Test
    public void onMotionEvent_initClickSchedulerDelayFromSetting() {
        injectFakeMouseActionHoverMoveEvent();

        int delay =
                Settings.Secure.getIntForUser(
                        mTestableContext.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_DELAY,
                        AccessibilityManager.AUTOCLICK_DELAY_DEFAULT,
                        mTestableContext.getUserId());
        assertThat(mController.mClickScheduler.getDelayForTesting()).isEqualTo(delay);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_initCursorAreaSizeFromSetting() {
        injectFakeMouseActionHoverMoveEvent();

        int size =
                Settings.Secure.getIntForUser(
                        mTestableContext.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                        AccessibilityManager.AUTOCLICK_CURSOR_AREA_SIZE_DEFAULT,
                        mTestableContext.getUserId());
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(size);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_doNotUpdateMetaStateWhenControllerIsNull() {
        assertThat(mController.mClickScheduler).isNull();

        injectFakeKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON);

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_updateMetaStateWhenControllerNotNull() {
        injectFakeMouseActionHoverMoveEvent();

        int metaState = KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;
        injectFakeKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, metaState);

        assertThat(mController.mClickScheduler).isNotNull();
        assertThat(mController.mClickScheduler.getMetaStateForTesting()).isEqualTo(metaState);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onKeyEvent_modifierKey_cancelAutoClickWhenAdditionalRegularKeyPresssed() {
        injectFakeMouseActionHoverMoveEvent();

        injectFakeKeyEvent(KeyEvent.KEYCODE_J, KeyEvent.META_ALT_ON);

        assertThat(mController.mClickScheduler).isNotNull();
        assertThat(mController.mClickScheduler.getMetaStateForTesting()).isEqualTo(0);
    }

    @Test
    public void onDestroy_clearClickScheduler() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mClickScheduler).isNull();
    }

    @Test
    public void onDestroy_clearAutoclickSettingsObserver() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mAutoclickSettingsObserver).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_clearAutoclickIndicatorScheduler() {
        injectFakeMouseActionHoverMoveEvent();

        mController.onDestroy();

        assertThat(mController.mAutoclickIndicatorScheduler).isNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_hoverEnter_doesNotScheduleClick() {
        injectFakeMouseActionHoverMoveEvent();

        // Send hover enter event.
        MotionEvent hoverEnter = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_ENTER,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverEnter.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverEnter, hoverEnter, /* policyFlags= */ 0);

        // Verify there is no pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_hoverMove_scheduleClick() {
        injectFakeMouseActionHoverMoveEvent();

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);

        // Verify there is a pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
    }

    @Test
    public void smallJitteryMovement_doesNotTriggerClick() {
        // Initial hover move to set an anchor point.
        MotionEvent initialHoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 40f,
                /* metaState= */ 0);
        initialHoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(initialHoverMove, initialHoverMove, /* policyFlags= */ 0);

        // Get the initial scheduled click time.
        long initialScheduledTime = mController.mClickScheduler.getScheduledClickTimeForTesting();

        // Simulate small, jittery movements (all within the default slop).
        MotionEvent jitteryMove1 = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 150,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 31f,  // Small change in x
                /* y= */ 41f,  // Small change in y
                /* metaState= */ 0);
        jitteryMove1.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(jitteryMove1, jitteryMove1, /* policyFlags= */ 0);

        MotionEvent jitteryMove2 = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 200,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30.5f, // Small change in x
                /* y= */ 39.8f, // Small change in y
                /* metaState= */ 0);
        jitteryMove2.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(jitteryMove2, jitteryMove2, /* policyFlags= */ 0);

        // Verify that the scheduled click time has NOT changed.
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting())
                .isEqualTo(initialScheduledTime);
    }

    @Test
    public void singleSignificantMovement_triggersClick() {
        // Initial hover move to set an anchor point.
        MotionEvent initialHoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 40f,
                /* metaState= */ 0);
        initialHoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(initialHoverMove, initialHoverMove, /* policyFlags= */ 0);

        // Get the initial scheduled click time.
        long initialScheduledTime = mController.mClickScheduler.getScheduledClickTimeForTesting();

        // Simulate a single, significant movement (greater than the default slop).
        MotionEvent significantMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 150,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 60f,  // Significant change in x (30f difference)
                /* y= */ 70f,  // Significant change in y (30f difference)
                /* metaState= */ 0);
        significantMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(significantMove, significantMove, /* policyFlags= */ 0);

        // Verify that the scheduled click time has changed (click was rescheduled).
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting())
                .isNotEqualTo(initialScheduledTime);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onCursorAreaSizeSettingsChange_moveWithinCustomRadius_clickNotTriggered() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(customSize);

        // Move the mouse down, less than customSize radius so a click is not triggered.
        float moveDownY = customSize - 25;
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 150,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 0f,
                /* y= */ moveDownY,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onCursorAreaSizeSettingsChange_moveOutsideCustomRadius_clickTriggered() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));
        assertThat(mController.mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(customSize);

        // Move the mouse right, greater than customSize radius so a click is triggered.
        float moveRightX = customSize + 100;
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 200,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ moveRightX,
                /* y= */ 0,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onIgnoreCursorMovementFromSettingsChange_clickTriggered() {
        // Send initial mouse movement.
        injectFakeMouseActionHoverMoveEvent();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));

        // Move the mouse down less than customSize radius but ignore custom movement is not enabled
        // so a click is triggered.
        float moveDownY = customSize - 100;
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 150,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 0f,
                /* y= */ moveDownY,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onIgnoreCursorMovementFromSettingsChange_clickNotTriggered() {
        // Move mouse to initialize autoclick panel before enabling ignore minor cursor movement.
        injectFakeMouseActionHoverMoveEvent();
        enableIgnoreMinorCursorMovement();

        // Set a custom cursor area size.
        int customSize = 250;
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
                customSize,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE));

        // After enabling ignore custom movement, move the mouse right, less than customSize radius
        // so a click won't be triggered.
        float moveRightX = customSize - 100;
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 200,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ moveRightX,
                /* y= */ 0,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_flagOn_clickNotTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);

        // Verify there is not a pending click.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);

        // Resume autoclick.
        when(mockAutoclickTypePanel.isPaused()).thenReturn(false);

        // Send initial move event again. Because this is the first recorded move, a click won't be
        // scheduled.
        injectFakeMouseActionHoverMoveEvent();
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);

        // Send move again to trigger click and verify there is now a pending click.
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_panelNotHovered_clickNotTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and ensure the panel is not hovered.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(false);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);

        // Verify click is not triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_panelHovered_clickTriggeredWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and hover the panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);

        // Verify click is triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void pauseButton_unhoveringCancelsClickWhenPaused() {
        injectFakeMouseActionHoverMoveEvent();

        // Pause autoclick and hover the panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isPaused()).thenReturn(true);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);

        // Verify click is triggered.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isTrue();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isNotEqualTo(-1);

        // Now simulate the pointer being moved outside the panel.
        when(mockAutoclickTypePanel.isHovered()).thenReturn(false);
        mController.clickPanelController.onHoverChange(/* hovered= */ false);

        // Verify pending click is canceled.
        assertThat(mController.mClickScheduler.getIsActiveForTesting()).isFalse();
        assertThat(mController.mClickScheduler.getScheduledClickTimeForTesting()).isEqualTo(-1);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOn_lazyInitAutoclickScrollPanel() {
        assertThat(mController.mAutoclickScrollPanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickScrollPanel).isNotNull();
    }

    @Test
    @DisableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onMotionEvent_flagOff_notInitAutoclickScrollPanel() {
        assertThat(mController.mAutoclickScrollPanel).isNull();

        injectFakeMouseActionHoverMoveEvent();

        assertThat(mController.mAutoclickScrollPanel).isNull();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void onDestroy_flagOn_hideAutoclickScrollPanel() {
        injectFakeMouseActionHoverMoveEvent();
        AutoclickScrollPanel mockAutoclickScrollPanel = mock(AutoclickScrollPanel.class);
        mController.mAutoclickScrollPanel = mockAutoclickScrollPanel;

        mController.onDestroy();

        verify(mockAutoclickScrollPanel).hide();
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void changeFromScrollToOtherClickType_hidesScrollPanel() {
        injectFakeMouseActionHoverMoveEvent();

        // Set active click type to SCROLL.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL);

        // Show the scroll panel.
        mController.mAutoclickScrollPanel.show();
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isTrue();

        // Change click type to LEFT_CLICK.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK);

        // Verify scroll panel is hidden.
        assertThat(mController.mAutoclickScrollPanel.isVisible()).isFalse();
    }

    @Test
    public void sendClick_clickType_leftClick() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        mTestableLooper.processAllMessages();

        // Verify left click sent.
        assertThat(motionEventCaptor.downEvent).isNotNull();
        assertThat(motionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
    }

    @Test
    public void sendClick_clickType_rightClick() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);

        // Set click type to right click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK);

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        mTestableLooper.processAllMessages();

        // Verify right click sent.
        assertThat(motionEventCaptor.downEvent).isNotNull();
        assertThat(motionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_SECONDARY);
    }

    @Test
    @EnableFlags(com.android.server.accessibility.Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
    public void hoverOnAutoclickPanel_rightClickType_forceTriggerLeftClick() {
        MotionEventCaptor motionEventCaptor = new MotionEventCaptor();
        mController.setNext(motionEventCaptor);

        injectFakeMouseActionHoverMoveEvent();
        // Set delay to zero so click is scheduled to run immediately.
        mController.mClickScheduler.updateDelay(0);

        // Set click type to right click.
        mController.clickPanelController.handleAutoclickTypeChange(
                AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK);
        // Set mouse to hover panel.
        AutoclickTypePanel mockAutoclickTypePanel = mock(AutoclickTypePanel.class);
        when(mockAutoclickTypePanel.isHovered()).thenReturn(true);
        mController.mAutoclickTypePanel = mockAutoclickTypePanel;

        // Send hover move event.
        MotionEvent hoverMove = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 100,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 30f,
                /* y= */ 0f,
                /* metaState= */ 0);
        hoverMove.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(hoverMove, hoverMove, /* policyFlags= */ 0);
        mTestableLooper.processAllMessages();

        // Verify left click is sent due to the mouse hovering the panel.
        assertThat(motionEventCaptor.downEvent).isNotNull();
        assertThat(motionEventCaptor.downEvent.getButtonState()).isEqualTo(
                MotionEvent.BUTTON_PRIMARY);
    }

    private void injectFakeMouseActionHoverMoveEvent() {
        MotionEvent event = getFakeMotionHoverMoveEvent();
        event.setSource(InputDevice.SOURCE_MOUSE);
        mController.onMotionEvent(event, event, /* policyFlags= */ 0);
    }

    private void injectFakeNonMouseActionHoverMoveEvent() {
        MotionEvent event = getFakeMotionHoverMoveEvent();
        event.setSource(InputDevice.SOURCE_KEYBOARD);
        mController.onMotionEvent(event, event, /* policyFlags= */ 0);
    }

    private void injectFakeKeyEvent(int keyCode, int modifiers) {
        KeyEvent keyEvent = new KeyEvent(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ KeyEvent.ACTION_DOWN,
                /* code= */ keyCode,
                /* repeat= */ 0,
                /* metaState= */ modifiers);
        mController.onKeyEvent(keyEvent, /* policyFlags= */ 0);
    }

    private MotionEvent getFakeMotionHoverMoveEvent() {
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);
    }

    private void enableIgnoreMinorCursorMovement() {
        Settings.Secure.putIntForUser(mTestableContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT,
                AccessibilityUtils.State.ON,
                mTestableContext.getUserId());
        mController.onChangeForTesting(/* selfChange= */ true,
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT));
    }
}
