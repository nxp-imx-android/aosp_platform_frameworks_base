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

import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AutoclickType;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.ClickPanelControllerInterface;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickTypePanel}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickTypePanelTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    private AutoclickTypePanel mAutoclickTypePanel;
    @Mock private WindowManager mMockWindowManager;
    private LinearLayout mLeftClickButton;
    private LinearLayout mRightClickButton;
    private LinearLayout mDoubleClickButton;
    private LinearLayout mDragButton;
    private LinearLayout mScrollButton;
    private LinearLayout mPauseButton;
    private LinearLayout mPositionButton;

    private @AutoclickType int mActiveClickType = AUTOCLICK_TYPE_LEFT_CLICK;
    private boolean mPaused;

    private final ClickPanelControllerInterface clickPanelController =
            new ClickPanelControllerInterface() {
                @Override
                public void handleAutoclickTypeChange(@AutoclickType int clickType) {
                    mActiveClickType = clickType;
                }

                @Override
                public void toggleAutoclickPause(boolean paused) {
                    mPaused = paused;
                }
            };

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);

        mAutoclickTypePanel =
                new AutoclickTypePanel(mTestableContext, mMockWindowManager, clickPanelController);
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        mLeftClickButton = contentView.findViewById(R.id.accessibility_autoclick_left_click_layout);
        mRightClickButton =
                contentView.findViewById(R.id.accessibility_autoclick_right_click_layout);
        mDoubleClickButton =
                contentView.findViewById(R.id.accessibility_autoclick_double_click_layout);
        mScrollButton = contentView.findViewById(R.id.accessibility_autoclick_scroll_layout);
        mDragButton = contentView.findViewById(R.id.accessibility_autoclick_drag_layout);
        mPauseButton = contentView.findViewById(R.id.accessibility_autoclick_pause_layout);
        mPositionButton = contentView.findViewById(R.id.accessibility_autoclick_position_layout);
    }

    @Test
    public void AutoclickTypePanel_initialState_expandedFalse() {
        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isFalse();
    }

    @Test
    public void AutoclickTypePanel_initialState_correctButtonVisibility() {
        // On initialization, only left button is visible.
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void AutoclickTypePanel_initialState_correctButtonStyle() {
        verifyButtonHasSelectedStyle(mLeftClickButton);
    }

    @Test
    public void togglePanelExpansion_onClick_expandedTrue() {
        // On clicking left click button, the panel is expanded and all buttons are visible.
        mLeftClickButton.callOnClick();

        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isTrue();
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.VISIBLE);

        // Pause button is always visible.
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void togglePanelExpansion_onClickAgain_expandedFalse() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();
        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isTrue();

        // Clicks any button in the expanded state, the panel is expected to collapse
        // with only the clicked button visible.
        mScrollButton.callOnClick();

        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isFalse();
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.GONE);

        // Pause button is always visible.
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void togglePanelExpansion_selectButton_correctStyle() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();

        // Clicks any button in the expanded state to select a type button.
        mScrollButton.callOnClick();

        verifyButtonHasSelectedStyle(mScrollButton);
    }

    @Test
    public void togglePanelExpansion_selectButton_correctActiveClickType() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();

        // Clicks any button in the expanded state to select a type button.
        mScrollButton.callOnClick();

        assertThat(mActiveClickType).isEqualTo(AUTOCLICK_TYPE_SCROLL);
    }

    @Test
    public void moveToNextCorner_positionButton_rotatesThroughAllPositions() {
        // Define all positions in sequence
        int[][] expectedPositions = {
                {0, Gravity.END | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90},
                {1, Gravity.START | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90},
                {2, Gravity.START | Gravity.TOP, /*x=*/ 15, /*y=*/ 30},
                {3, Gravity.END | Gravity.TOP, /*x=*/ 15, /*y=*/ 30},
                {0, Gravity.END | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90}
        };

        // Check initial position
        verifyPanelPosition(expectedPositions[0]);

        // Move through all corners.
        for (int i = 1; i < expectedPositions.length; i++) {
            mPositionButton.callOnClick();
            verifyPanelPosition(expectedPositions[i]);
        }
    }

    @Test
    public void pauseButton_onClick() {
        mPauseButton.callOnClick();
        assertThat(mPaused).isTrue();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        mPauseButton.callOnClick();
        assertThat(mPaused).isFalse();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    private void verifyButtonHasSelectedStyle(@NonNull LinearLayout button) {
        GradientDrawable gradientDrawable = (GradientDrawable) button.getBackground();
        assertThat(gradientDrawable.getColor().getDefaultColor())
                .isEqualTo(mTestableContext.getColor(R.color.materialColorPrimary));
    }

    private void verifyPanelPosition(int[] expectedPosition) {
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParams();
        assertThat(mAutoclickTypePanel.getCurrentCornerIndexForTesting()).isEqualTo(
                expectedPosition[0]);
        assertThat(params.gravity).isEqualTo(expectedPosition[1]);
        assertThat(params.x).isEqualTo(expectedPosition[2]);
        assertThat(params.y).isEqualTo(expectedPosition[3]);
    }
}
