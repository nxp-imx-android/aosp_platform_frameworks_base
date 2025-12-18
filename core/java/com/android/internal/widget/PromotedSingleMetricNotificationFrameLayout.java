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

package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.NotificationTopLineView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.R;

/**
 * A custom {@link FrameLayout} designed specifically for displaying promoted single metric
 * {@link Notification.MetricStyle}
 *
 * it measures the single metric value (which has maximum width) and adjust end margins of
 * {@link NotificationTopLineView} and label accordingly to render header and label correctly.
 * @hide
 */
@RemoteViews.RemoteView
public class PromotedSingleMetricNotificationFrameLayout extends FrameLayout {

    @NonNull
    private NotificationTopLineView mNotificationToplineView;

    @NonNull
    private View mMetricValueContainer;

    @NonNull
    private View mMainContainer;

    private float mValueContainerMaxFraction;

    public PromotedSingleMetricNotificationFrameLayout(
            @NonNull Context context) {
        super(context);
        init();
    }

    public PromotedSingleMetricNotificationFrameLayout(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PromotedSingleMetricNotificationFrameLayout(@NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PromotedSingleMetricNotificationFrameLayout(@NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mValueContainerMaxFraction = getContext().getResources().getFraction(
                R.fraction.notification_single_promoted_metric_value_max_fraction, 1, 1
        );
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mNotificationToplineView = requireViewById(R.id.notification_top_line);
        mMetricValueContainer = requireViewById(R.id.metric_value_container);
        mMainContainer = requireViewById(R.id.notification_main_column);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildWithMargins(mMetricValueContainer,
                widthMeasureSpec, 0,
                heightMeasureSpec, 0);

        final int toplineMarginEnd = getToplineMarginEnd();
        mNotificationToplineView.setHeaderTextMarginEnd(toplineMarginEnd);

        final MarginLayoutParams layoutParams =
                (MarginLayoutParams) mMainContainer.getLayoutParams();

        if (layoutParams != null) {
            layoutParams.setMarginEnd(toplineMarginEnd);
            mMainContainer.setLayoutParams(layoutParams);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * NOTE: mMetricValueContainer needs to be measured before this method.
     */
    private int getToplineMarginEnd() {
        final MarginLayoutParams layoutParams =
                (MarginLayoutParams) mMetricValueContainer.getLayoutParams();

        final int horizontalMargins;
        if (layoutParams != null) {
            horizontalMargins = layoutParams.getMarginStart() + layoutParams.getMarginEnd();
        } else {
            horizontalMargins = 0;
        }

        return mMetricValueContainer.getMeasuredWidth() + horizontalMargins;
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        if (child.getId() == R.id.metric_value_container) {
            final int availableWidth = MeasureSpec.getSize(parentWidthMeasureSpec);
            final int maxAllowedWidth = (int) (availableWidth * mValueContainerMaxFraction);
            final int restrictedWidthSpec = MeasureSpec.makeMeasureSpec(maxAllowedWidth,
                    MeasureSpec.AT_MOST);
            super.measureChildWithMargins(
                    child,
                    restrictedWidthSpec,
                    widthUsed,
                    parentHeightMeasureSpec,
                    heightUsed
            );
        } else {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }
    }
}
