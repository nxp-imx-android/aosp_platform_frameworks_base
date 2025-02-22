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

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.IntDef;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

public class AutoclickTypePanel {

    private final String TAG = AutoclickTypePanel.class.getSimpleName();

    public static final int AUTOCLICK_TYPE_LEFT_CLICK = 0;
    public static final int AUTOCLICK_TYPE_RIGHT_CLICK = 1;
    public static final int AUTOCLICK_TYPE_DOUBLE_CLICK = 2;
    public static final int AUTOCLICK_TYPE_DRAG = 3;
    public static final int AUTOCLICK_TYPE_SCROLL = 4;

    public static final int CORNER_BOTTOM_RIGHT = 0;
    public static final int CORNER_BOTTOM_LEFT = 1;
    public static final int CORNER_TOP_LEFT = 2;
    public static final int CORNER_TOP_RIGHT = 3;

    // Distance between panel and screen edge.
    // TODO(b/396402941): Finalize edge margin.
    private static final int PANEL_EDGE_MARGIN = 15;

    // Touch point when drag starts, it can be anywhere inside the panel.
    private float mTouchStartX, mTouchStartY;
    // Initial panel position in screen coordinates.
    private int mPanelStartX, mPanelStartY;
    private boolean mIsDragging = false;

    // Types of click the AutoclickTypePanel supports.
    @IntDef({
        AUTOCLICK_TYPE_LEFT_CLICK,
        AUTOCLICK_TYPE_RIGHT_CLICK,
        AUTOCLICK_TYPE_DOUBLE_CLICK,
        AUTOCLICK_TYPE_DRAG,
        AUTOCLICK_TYPE_SCROLL,
    })
    public @interface AutoclickType {}

    @IntDef({
            CORNER_BOTTOM_RIGHT,
            CORNER_BOTTOM_LEFT,
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT
    })
    public @interface Corner {}

    private static final @Corner int[] CORNER_ROTATION_ORDER = {
            CORNER_BOTTOM_RIGHT,
            CORNER_BOTTOM_LEFT,
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT
    };

    // An interface exposed to {@link AutoclickController) to handle different actions on the panel,
    // including changing autoclick type, pausing/resuming autoclick.
    public interface ClickPanelControllerInterface {
        /**
         * Allows users to change a different autoclick type.
         *
         * @param clickType The new autoclick type to use. Should be one of the values defined in
         *                  {@link AutoclickType}.
         */
        void handleAutoclickTypeChange(@AutoclickType int clickType);

        /**
         * Allows users to pause or resume autoclick.
         *
         * @param paused {@code true} to pause autoclick, {@code false} to resume.
         */
        void toggleAutoclickPause(boolean paused);
    }

    private final Context mContext;

    private final View mContentView;

    private final WindowManager mWindowManager;

    private WindowManager.LayoutParams mParams;

    private final ClickPanelControllerInterface mClickPanelController;

    // Whether the panel is expanded or not.
    private boolean mExpanded = false;

    // Whether autoclick is paused.
    private boolean mPaused = false;
    // Tracks the current corner position of the panel using an index into CORNER_ROTATION_ORDER
    // array. This allows the panel to cycle through screen corners in a defined sequence when
    // repositioned.
    private int mCurrentCornerIndex = 0;

    private final LinearLayout mLeftClickButton;
    private final LinearLayout mRightClickButton;
    private final LinearLayout mDoubleClickButton;
    private final LinearLayout mDragButton;
    private final LinearLayout mScrollButton;
    private final LinearLayout mPauseButton;
    private final LinearLayout mPositionButton;

    private LinearLayout mSelectedButton;

    private final Drawable mPauseButtonDrawable;
    private final Drawable mResumeButtonDrawable;

    public AutoclickTypePanel(
            Context context,
            WindowManager windowManager,
            ClickPanelControllerInterface clickPanelController) {
        mContext = context;
        mWindowManager = windowManager;
        mClickPanelController = clickPanelController;
        mParams = getDefaultLayoutParams();

        mPauseButtonDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_pause);
        mResumeButtonDrawable = mContext.getDrawable(
                R.drawable.accessibility_autoclick_resume);

        mContentView =
                LayoutInflater.from(context)
                        .inflate(R.layout.accessibility_autoclick_type_panel, null);
        mLeftClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_left_click_layout);
        mRightClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_right_click_layout);
        mDoubleClickButton =
                mContentView.findViewById(R.id.accessibility_autoclick_double_click_layout);
        mScrollButton = mContentView.findViewById(R.id.accessibility_autoclick_scroll_layout);
        mDragButton = mContentView.findViewById(R.id.accessibility_autoclick_drag_layout);
        mPauseButton = mContentView.findViewById(R.id.accessibility_autoclick_pause_layout);
        mPositionButton = mContentView.findViewById(R.id.accessibility_autoclick_position_layout);

        initializeButtonState();

        // Set up touch event handling for the panel to allow the user to drag and reposition the
        // panel by touching and moving it.
        mContentView.setOnTouchListener(this::onPanelTouch);
    }

    /**
     * Handles touch events on the panel, enabling the user to drag and reposition it.
     * This function supports the draggable panel feature, allowing users to move the panel
     * to different screen locations for better usability and customization.
     */
    private boolean onPanelTouch(View v, MotionEvent event) {
        // TODO(b/397681794): Make sure this works on multiple screens.
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Store initial touch positions.
                mTouchStartX = event.getRawX();
                mTouchStartY = event.getRawY();

                // Store initial panel position relative to screen's top-left corner.
                // getLocationOnScreen provides coordinates relative to the top-left corner of the
                // screen's display. We are using this coordinate system to consistently track the
                // panel's position during drag operations.
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                mPanelStartX = location[0];
                mPanelStartY = location[1];
                return true;
            case MotionEvent.ACTION_MOVE:
                mIsDragging = true;

                // Set panel gravity to TOP|LEFT to match getLocationOnScreen's coordinate system
                mParams.gravity = Gravity.LEFT | Gravity.TOP;

                if (mIsDragging) {
                    // Calculate touch distance moved from start position.
                    float deltaX = event.getRawX() - mTouchStartX;
                    float deltaY = event.getRawY() - mTouchStartY;

                    // Update panel position, based on Top-Left absolute positioning.
                    mParams.x = mPanelStartX + (int) deltaX;
                    mParams.y = mPanelStartY + (int) deltaY;
                    mWindowManager.updateViewLayout(mContentView, mParams);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    // When drag ends, snap panel to nearest edge.
                    snapToNearestEdge(mParams);
                }
                mIsDragging = false;
                return true;
        }
        return false;
    }

    private void snapToNearestEdge(WindowManager.LayoutParams params) {
        // Get screen width to determine which side to snap to.
        // TODO(b/397944891): Handle device rotation case.
        int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int yPosition = params.y;

        // Determine which half of the screen the panel is on.
        boolean isOnLeftHalf = params.x < screenWidth / 2;

        if (isOnLeftHalf) {
            // Snap to left edge. Set params.gravity to make sure x, y offsets from correct anchor.
            params.gravity = Gravity.START | Gravity.TOP;
            // Set the current corner to be bottom-left to ensure that the subsequent reposition
            // action rotates the panel clockwise from bottom-left towards top-left.
            mCurrentCornerIndex = 1;
        } else {
            // Snap to right edge. Set params.gravity to make sure x, y offsets from correct anchor.
            params.gravity = Gravity.END | Gravity.TOP;
            // Set the current corner to be top-right to ensure that the subsequent reposition
            // action rotates the panel clockwise from top-right towards bottom-right.
            mCurrentCornerIndex = 3;
        }

        // Apply final position: set params.x to be edge margin, params.y to maintain vertical
        // position.
        params.x = PANEL_EDGE_MARGIN;
        params.y = yPosition;
        mWindowManager.updateViewLayout(mContentView, params);
    }

    private void initializeButtonState() {
        mLeftClickButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_LEFT_CLICK));
        mRightClickButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_RIGHT_CLICK));
        mDoubleClickButton.setOnClickListener(
                v -> togglePanelExpansion(AUTOCLICK_TYPE_DOUBLE_CLICK));
        mScrollButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_SCROLL));
        mDragButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_DRAG));
        mPositionButton.setOnClickListener(v -> moveToNextCorner());
        mPauseButton.setOnClickListener(v -> togglePause());

        // Initializes panel as collapsed state and only displays the left click button.
        hideAllClickTypeButtons();
        mLeftClickButton.setVisibility(View.VISIBLE);
        setSelectedClickType(AUTOCLICK_TYPE_LEFT_CLICK);
    }

    /** Sets the selected button and updates the newly and previously selected button styling. */
    private void setSelectedClickType(@AutoclickType int clickType) {
        final LinearLayout selectedButton = getButtonFromClickType(clickType);

        // Updates the previously selected button styling.
        if (mSelectedButton != null) {
            toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ false);
        }

        mSelectedButton = selectedButton;
        mClickPanelController.handleAutoclickTypeChange(clickType);

        // Updates the newly selected button styling.
        toggleSelectedButtonStyle(selectedButton, /* isSelected= */ true);
    }

    private void toggleSelectedButtonStyle(@NonNull LinearLayout button, boolean isSelected) {
        // Sets icon background color.
        GradientDrawable gradientDrawable = (GradientDrawable) button.getBackground();
        gradientDrawable.setColor(
                mContext.getColor(
                        isSelected
                                ? R.color.materialColorPrimary
                                : R.color.materialColorSurfaceContainer));

        // Sets icon color.
        ImageButton imageButton = (ImageButton) button.getChildAt(/* index= */ 0);
        Drawable drawable = imageButton.getDrawable();
        drawable.mutate()
                .setTint(
                        mContext.getColor(
                                isSelected
                                        ? R.color.materialColorSurfaceContainer
                                        : R.color.materialColorPrimary));
    }

    public void show() {
        mWindowManager.addView(mContentView, mParams);
    }

    public void hide() {
        // Sets the button background to unselected styling, this is necessary to make sure the
        // button background styling is correct when the panel shows up next time.
        toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ false);

        mWindowManager.removeView(mContentView);
    }

    public boolean isPaused() {
        return mPaused;
    }

    /** Toggles the panel expanded or collapsed state. */
    private void togglePanelExpansion(@AutoclickType int clickType) {
        final LinearLayout button = getButtonFromClickType(clickType);

        if (mExpanded) {
            // If the panel is already in expanded state, we should collapse it by hiding all
            // buttons except the one user selected.
            hideAllClickTypeButtons();
            button.setVisibility(View.VISIBLE);

            // Sets the newly selected button.
            setSelectedClickType(clickType);
        } else {
            // If the panel is already collapsed, we just need to expand it.
            showAllClickTypeButtons();
        }

        // Toggle the state.
        mExpanded = !mExpanded;
    }

    private void togglePause() {
        mPaused = !mPaused;
        mClickPanelController.toggleAutoclickPause(mPaused);

        ImageButton imageButton = (ImageButton) mPauseButton.getChildAt(/* index= */ 0);
        if (mPaused) {
            imageButton.setImageDrawable(mResumeButtonDrawable);
        } else {
            imageButton.setImageDrawable(mPauseButtonDrawable);
        }
    }

    /** Hide all buttons on the panel except pause and position buttons. */
    private void hideAllClickTypeButtons() {
        mLeftClickButton.setVisibility(View.GONE);
        mRightClickButton.setVisibility(View.GONE);
        mDoubleClickButton.setVisibility(View.GONE);
        mDragButton.setVisibility(View.GONE);
        mScrollButton.setVisibility(View.GONE);
    }

    /** Show all buttons on the panel except pause and position buttons. */
    private void showAllClickTypeButtons() {
        mLeftClickButton.setVisibility(View.VISIBLE);
        mRightClickButton.setVisibility(View.VISIBLE);
        mDoubleClickButton.setVisibility(View.VISIBLE);
        mDragButton.setVisibility(View.VISIBLE);
        mScrollButton.setVisibility(View.VISIBLE);
    }

    private LinearLayout getButtonFromClickType(@AutoclickType int clickType) {
        return switch (clickType) {
            case AUTOCLICK_TYPE_LEFT_CLICK -> mLeftClickButton;
            case AUTOCLICK_TYPE_RIGHT_CLICK -> mRightClickButton;
            case AUTOCLICK_TYPE_DOUBLE_CLICK -> mDoubleClickButton;
            case AUTOCLICK_TYPE_DRAG -> mDragButton;
            case AUTOCLICK_TYPE_SCROLL -> mScrollButton;
            default -> throw new IllegalArgumentException("Unknown clickType " + clickType);
        };
    }

    /** Moves the panel to the next corner in clockwise direction. */
    private void moveToNextCorner() {
        @Corner int nextCornerIndex = (mCurrentCornerIndex + 1) % CORNER_ROTATION_ORDER.length;
        mCurrentCornerIndex = nextCornerIndex;

        setPanelPositionForCorner(mParams, mCurrentCornerIndex);
        mWindowManager.updateViewLayout(mContentView, mParams);
    }

    private void setPanelPositionForCorner(WindowManager.LayoutParams params, @Corner int corner) {
        //  TODO(b/396402941): Replace hardcoded pixel values with proper dimension calculations,
        //  Current values are experimental and may not work correctly across different device
        //  resolutions and configurations.
        switch (corner) {
            case CORNER_BOTTOM_RIGHT:
                params.gravity = Gravity.END | Gravity.BOTTOM;
                params.x = PANEL_EDGE_MARGIN;
                params.y = 90;
                break;
            case CORNER_BOTTOM_LEFT:
                params.gravity = Gravity.START | Gravity.BOTTOM;
                params.x = PANEL_EDGE_MARGIN;
                params.y = 90;
                break;
            case CORNER_TOP_LEFT:
                params.gravity = Gravity.START | Gravity.TOP;
                params.x = PANEL_EDGE_MARGIN;
                params.y = 30;
                break;
            case CORNER_TOP_RIGHT:
                params.gravity = Gravity.END | Gravity.TOP;
                params.x = PANEL_EDGE_MARGIN;
                params.y = 30;
                break;
            default:
                throw new IllegalArgumentException("Invalid corner: " + corner);
        }
    }

    @VisibleForTesting
    boolean getExpansionStateForTesting() {
        return mExpanded;
    }

    @VisibleForTesting
    @NonNull
    View getContentViewForTesting() {
        return mContentView;
    }

    @VisibleForTesting
    @Corner
    int getCurrentCornerIndexForTesting() {
        return mCurrentCornerIndex;
    }

    @VisibleForTesting
    WindowManager.LayoutParams getLayoutParamsForTesting() {
        return mParams;
    }

    @VisibleForTesting
    boolean getIsDraggingForTesting() {
        return mIsDragging;
    }

    /**
     * Retrieves the layout params for AutoclickIndicatorView, used when it's added to the Window
     * Manager.
     */
    @NonNull
    private WindowManager.LayoutParams getDefaultLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.setFitInsetsTypes(WindowInsets.Type.statusBars());
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickTypePanel.class.getSimpleName());
        layoutParams.accessibilityTitle =
                mContext.getString(R.string.accessibility_autoclick_type_settings_panel_title);
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        setPanelPositionForCorner(layoutParams, CORNER_BOTTOM_RIGHT);
        return layoutParams;
    }
}
