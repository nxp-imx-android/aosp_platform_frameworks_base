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

package android.service.personalcontext.insight;

import static android.service.personalcontext.insight.ContextInsight.INSIGHT_TYPE_ACTIONABLE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;
import android.text.TextUtils;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An insight that contains information about an action and how to invoke it. */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ActionableInsight extends ContextInsight {
    private static final String KEY_ACTION_INTENT = "key_action_intent";
    private static final String KEY_DISPLAY_DETAILS = "key_display_details";

    private final InsightDisplayDetails mDisplayDetails;
    private final Intent mActionIntent;

    /** Private constructor used by the builder. */
    private ActionableInsight(
            @NonNull List<ContextHint> originHints,
            @NonNull Intent actionIntent,
            @NonNull InsightDisplayDetails displayDetails) {
        super(originHints);
        mActionIntent = actionIntent;
        mDisplayDetails = displayDetails;
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    ActionableInsight(@NonNull Bundle b) {
        super(b);

        final Bundle insightData = b.getBundle(KEY_INSIGHT_DATA);
        Objects.requireNonNull(insightData, "Bundle must contain insight data");

        mActionIntent = insightData.getParcelable(KEY_ACTION_INTENT, Intent.class);
        mDisplayDetails =
                insightData.getParcelable(KEY_DISPLAY_DETAILS, InsightDisplayDetails.class);
    }

    @Override
    Bundle toBundleImpl() {
        final Bundle b = new Bundle();
        b.putParcelable(KEY_ACTION_INTENT, mActionIntent);
        b.putParcelable(KEY_DISPLAY_DETAILS, mDisplayDetails);
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionableInsight)) return false;
        if (!super.equals(o)) return false;

        ActionableInsight that = (ActionableInsight) o;
        return Objects.equals(mActionIntent, that.mActionIntent)
                && Objects.equals(mDisplayDetails, that.mDisplayDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mActionIntent, mDisplayDetails);
    }

    @Override
    public String toString() {
        return "ActionableInsight{"
                + "mDisplayDetails="
                + mDisplayDetails
                + ", mActionIntent="
                + mActionIntent
                + ", "
                + super.toString()
                + '}';
    }

    /**
     * Returns the display details of the actionable insight.
     *
     * @return the display details of the actionable insight.
     */
    @NonNull
    public InsightDisplayDetails getDisplayDetails() {
        return mDisplayDetails;
    }

    /**
     * Returns the intent to be invoked when the actionable insight triggers.
     *
     * @return the intent representing the action to be invoked.
     */
    @NonNull
    public Intent createActionIntent() {
        return new Intent(mActionIntent);
    }

    @Override
    public int getInsightType() {
        return INSIGHT_TYPE_ACTIONABLE;
    }

    /** Builder for {@link ActionableInsight}. */
    public static final class Builder {
        List<ContextHint> mOriginHints = new ArrayList<>();
        private final Intent mActionIntent;
        private final InsightDisplayDetails mDisplayDetails;

        /**
         * Creates a new builder for an actionable insight. By default, no hints are present. They
         * can be added using {@link #setOriginHints(List)}.
         *
         * @param actionIntent the intent to be invoked when the actionable insight is clicked.
         * @param displayDetails the display details of the actionable insight.
         */
        public Builder(
                @NonNull Intent actionIntent,
                @NonNull InsightDisplayDetails displayDetails) {
            Preconditions.checkNotNull(actionIntent, "actionIntent is null");
            Preconditions.checkNotNull(displayDetails, "displayDetails is null");

            mActionIntent = actionIntent;
            mDisplayDetails = displayDetails;
        }

        @NonNull
        public Builder setOriginHints(@NonNull List<ContextHint> originHint) {
            mOriginHints = List.copyOf(originHint);
            return this;
        }

        /**
         * Builds the actionable insight.
         *
         * @return the actionable insight.
         */
        @NonNull
        public ActionableInsight build() {
            return new ActionableInsight(mOriginHints, mActionIntent, mDisplayDetails);
        }
    }
}
