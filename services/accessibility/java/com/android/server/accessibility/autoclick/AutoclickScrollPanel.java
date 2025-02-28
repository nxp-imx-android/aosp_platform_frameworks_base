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

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

public class AutoclickScrollPanel {
    private final Context mContext;
    private final View mContentView;
    private final WindowManager mWindowManager;
    private boolean mInScrollMode = false;

    public AutoclickScrollPanel(Context context, WindowManager windowManager) {
        mContext = context;
        mWindowManager = windowManager;
        mContentView = LayoutInflater.from(context).inflate(
                R.layout.accessibility_autoclick_scroll_panel, null);
    }

    /**
     * Shows the autoclick scroll panel.
     */
    public void show() {
        if (mInScrollMode) {
            return;
        }
        mWindowManager.addView(mContentView, getLayoutParams());
        mInScrollMode = true;
    }

    /**
     * Hides the autoclick scroll panel.
     */
    public void hide() {
        if (!mInScrollMode) {
            return;
        }
        mWindowManager.removeView(mContentView);
        mInScrollMode = false;
    }

    /**
     * Retrieves the layout params for AutoclickScrollPanel, used when it's added to the Window
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
        layoutParams.setTitle(AutoclickScrollPanel.class.getSimpleName());
        layoutParams.accessibilityTitle =
                mContext.getString(R.string.accessibility_autoclick_scroll_panel_title);
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        return layoutParams;
    }

    @VisibleForTesting
    public boolean isVisible() {
        return mInScrollMode;
    }

    @VisibleForTesting
    public View getContentViewForTesting() {
        return mContentView;
    }

    @VisibleForTesting
    public WindowManager.LayoutParams getLayoutParamsForTesting() {
        return getLayoutParams();
    }
}
