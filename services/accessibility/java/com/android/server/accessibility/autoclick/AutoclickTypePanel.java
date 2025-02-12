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

    // Types of click the AutoclickTypePanel supports.
    @IntDef({
        AUTOCLICK_TYPE_LEFT_CLICK,
        AUTOCLICK_TYPE_RIGHT_CLICK,
        AUTOCLICK_TYPE_DOUBLE_CLICK,
        AUTOCLICK_TYPE_DRAG,
        AUTOCLICK_TYPE_SCROLL,
    })
    public @interface AutoclickType {}

    // An interface exposed to {@link AutoclickController) to handle different actions on the panel,
    // including changing autoclick type, pausing/resuming autoclick.
    public interface ClickPanelControllerInterface {
        // Allows users to change a different autoclick type.
        void handleAutoclickTypeChange(@AutoclickType int clickType);

        // Allows users to pause/resume the autoclick.
        void toggleAutoclickPause();
    }

    private final Context mContext;

    private final View mContentView;

    private final WindowManager mWindowManager;

    private final ClickPanelControllerInterface mClickPanelController;

    // Whether the panel is expanded or not.
    private boolean mExpanded = false;

    private final LinearLayout mLeftClickButton;
    private final LinearLayout mRightClickButton;
    private final LinearLayout mDoubleClickButton;
    private final LinearLayout mDragButton;
    private final LinearLayout mScrollButton;

    private LinearLayout mSelectedButton;

    public AutoclickTypePanel(
            Context context,
            WindowManager windowManager,
            ClickPanelControllerInterface clickPanelController) {
        mContext = context;
        mWindowManager = windowManager;
        mClickPanelController = clickPanelController;

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

        initializeButtonState();
    }

    private void initializeButtonState() {
        mLeftClickButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_LEFT_CLICK));
        mRightClickButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_RIGHT_CLICK));
        mDoubleClickButton.setOnClickListener(
                v -> togglePanelExpansion(AUTOCLICK_TYPE_DOUBLE_CLICK));
        mScrollButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_SCROLL));
        mDragButton.setOnClickListener(v -> togglePanelExpansion(AUTOCLICK_TYPE_DRAG));

        // TODO(b/388872274): registers listener for pause button and allows users to pause/resume
        // the autoclick.
        // TODO(b/388847771): registers listener for position button and allows users to move the
        // panel to a different position.

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
        mWindowManager.addView(mContentView, getLayoutParams());
    }

    public void hide() {
        // Sets the button background to unselected styling, this is necessary to make sure the
        // button background styling is correct when the panel shows up next time.
        toggleSelectedButtonStyle(mSelectedButton, /* isSelected= */ false);

        mWindowManager.removeView(mContentView);
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

    @VisibleForTesting
    boolean getExpansionStateForTesting() {
        return mExpanded;
    }

    @VisibleForTesting
    @NonNull
    View getContentViewForTesting() {
        return mContentView;
    }

    /**
     * Retrieves the layout params for AutoclickIndicatorView, used when it's added to the Window
     * Manager.
     */
    @NonNull
    private WindowManager.LayoutParams getLayoutParams() {
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
        // TODO(b/388847771): Compute position based on user interaction.
        layoutParams.x = 15;
        layoutParams.y = 90;
        layoutParams.gravity = Gravity.END | Gravity.BOTTOM;

        return layoutParams;
    }
}
