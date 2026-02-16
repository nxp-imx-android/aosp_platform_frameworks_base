/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkArgument;

import android.annotation.NonNull;
import android.app.Flags;
import android.content.Context;
import android.text.Layout;
import android.text.PrecomputedText;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.IntArray;
import android.view.RemotableViewMethod;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Specialized {@link TextView} used to display {@link android.app.Notification.Metric.MetricValue}.
 * It will choose one the text options supplied to {@link #setTextVariants(List)} according
 * to their fit in the provided space without ellipsizing. If none of the options fit, the first
 * one will be picked.
 *
 * @hide
 */
@RemoteViews.RemoteView
public class NotificationMetricAdaptiveTextView extends NotificationMetricTextView {

    private static final int VARIANT_NONE = -1;

    private List<CharSequence> mTextVariants;

    // To avoid recomputation, we store the result of calculating the width of the text variants.
    // If we're measuring the same strings with the same TextPaint (as compared by Params) then
    // we can just return the previous value.
    private IntArray mTextVariantsCache;
    private PrecomputedText.Params mTextVariantsCacheParams;

    // Which of the variants is currently displayed, and whether we're currently swapping out one
    // variant from another. This is to optimize calls to setText() and avoid duplicating work.
    private int mVariantIndex = VARIANT_NONE;
    private boolean mReplacingText;

    public NotificationMetricAdaptiveTextView(Context context) {
        super(context);
    }

    public NotificationMetricAdaptiveTextView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationMetricAdaptiveTextView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NotificationMetricAdaptiveTextView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    @NonNull
    public Runnable setTextVariantsAsync(@NonNull List<CharSequence> textVariants) {
        return () -> setTextVariants(textVariants);
    }

    @Override
    @RemotableViewMethod(asyncImpl = "setTextVariantsAsync")
    public void setTextVariants(@NonNull List<CharSequence> textVariants) {
        if (!Flags.metricValueAlternativeStrings()) {
            super.setTextVariants(textVariants);
            return;
        }

        checkArgument(!textVariants.isEmpty(), "textVariants must have at least one entry");
        if (mTextVariants != null && mTextVariants.equals(textVariants)) {
            return;
        }
        mTextVariants = List.copyOf(textVariants);
        mTextVariantsCache = new IntArray();
        mVariantIndex = VARIANT_NONE;

        // Start with preferred text and let the measurement pass handle the swap, if needed.
        replaceTextBy(0);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (Flags.metricValueAlternativeStrings() && !mReplacingText) {
            // A "normal" call to setText overwrites previous calls to setTextVariants().
            mTextVariants = null;
            mTextVariantsCache = new IntArray();
            mVariantIndex = VARIANT_NONE;
        }

        super.setText(text, type);
    }

    private void replaceTextBy(int variantIndex) {
        if (mVariantIndex == variantIndex) {
            return; // Not switching, bail out.
        }
        mVariantIndex = variantIndex;
        boolean previousReplacing = mReplacingText;
        try {
            mReplacingText = true;
            setText(mTextVariants.get(variantIndex));
        } finally {
            mReplacingText = previousReplacing;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Flags.metricValueAlternativeStrings()) {
            maybeChooseAlternativeTextByMeasure(widthMeasureSpec);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void maybeChooseAlternativeTextByMeasure(int widthMeasureSpec) {
        if (mTextVariants == null || mTextVariants.size() < 2) {
            return; // No alternatives.
        }

        int specMode = MeasureSpec.getMode(widthMeasureSpec);
        if (specMode == MeasureSpec.UNSPECIFIED) {
            // No restrictions? Use preferred.
            replaceTextBy(0);
            return;
        }

        // specMode is EXACTLY or AT_MOST -> Choose most appropriate variant.
        int specSize = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = specSize - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (availableWidth <= 0) {
            return; // Hmm? Anyway we won't do anything useful by continuing, so give up.
        }

        // Need to clone TextPaint for later comparison, because it's not immutable and will be
        // modified by TextView methods that change text dimensions.
        TextPaint currentPaint = new TextPaint(getPaint());
        PrecomputedText.Params params = new PrecomputedText.Params.Builder(currentPaint).build();
        if (mTextVariantsCacheParams == null || !mTextVariantsCacheParams.equals(params)) {
            mTextVariantsCache = new IntArray();
            mTextVariantsCacheParams = params;
        }

        for (int i = 0; i < mTextVariants.size(); i++) {
            int variantWidth;
            if (mTextVariantsCache.size() > i) {
                variantWidth = mTextVariantsCache.get(i);
            } else {
                variantWidth = (int) Math.ceil(
                        Layout.getDesiredWidth(mTextVariants.get(i), currentPaint));
                mTextVariantsCache.add(variantWidth);
            }

            if (variantWidth <= availableWidth) {
                // In order of preference, found one that fits.
                replaceTextBy(i);
                return;
            }
        }

        // If all the options are too long, fall back to the default.
        replaceTextBy(0);
    }
}
