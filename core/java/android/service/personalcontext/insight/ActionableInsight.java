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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An insight that contains information about an action and how to invoke it. */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ActionableInsight extends ContextInsight {
    private static final String KEY_ACTION_DETAILS = "key_action_details";
    private static final String KEY_DISPLAY_DETAILS = "key_display_details";

    private final InsightActionDetails mActionDetails;
    private final InsightDisplayDetails mDisplayDetails;

    /** Private constructor. Used by the builder. */
    private ActionableInsight(
            @NonNull List<ContextHint> originHints,
            @NonNull InsightActionDetails actionDetails,
            @NonNull InsightDisplayDetails displayDetails) {
        super(originHints);
        mActionDetails = actionDetails;
        mDisplayDetails = displayDetails;
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    ActionableInsight(@NonNull Bundle b) {
        super(b);

        final Bundle insightData = b.getBundle(KEY_INSIGHT_DATA);
        Objects.requireNonNull(insightData, "Bundle must contain insight data");

        mActionDetails = insightData.getParcelable(KEY_ACTION_DETAILS, InsightActionDetails.class);
        mDisplayDetails =
                insightData.getParcelable(KEY_DISPLAY_DETAILS, InsightDisplayDetails.class);
    }

    @Override
    Bundle toBundleImpl() {
        final Bundle b = new Bundle();
        b.putParcelable(KEY_ACTION_DETAILS, mActionDetails);
        b.putParcelable(KEY_DISPLAY_DETAILS, mDisplayDetails);
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionableInsight)) return false;
        if (!super.equals(o)) return false;

        ActionableInsight that = (ActionableInsight) o;
        return Objects.equals(mActionDetails, that.mActionDetails)
                && Objects.equals(mDisplayDetails, that.mDisplayDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mActionDetails, mDisplayDetails);
    }

    @Override
    public String toString() {
        return "ActionableInsight{"
                + "mDisplayDetails="
                + mDisplayDetails
                + ", mActionDetails="
                + mActionDetails
                + ", "
                + super.toString()
                + '}';
    }

    /**
     * Returns the display details of the actionable insight.
     */
    @NonNull
    public InsightDisplayDetails getDisplayDetails() {
        return mDisplayDetails;
    }

    /**
     * Returns the action details of the actionable insight. Action details contain information
     * (such as an {@Intent}) that can be used when the insight triggers.
     */
    @NonNull
    public InsightActionDetails getActionDetails() {
        return mActionDetails;
    }

    /**
     * Returns the intent to be invoked when the actionable insight triggers, or null if the action
     * details does not contain an intent.
     * @deprecated get the intent from {@link InsightActionDetails}
     */
    @Deprecated
    @Nullable
    public Intent createActionIntent() {
        return mActionDetails.createActionIntent();
    }

    /** @hide */
    @Override
    @InsightType
    public int getInsightType() {
        return INSIGHT_TYPE_ACTIONABLE;
    }

    /** Builder for {@link ActionableInsight}. */
    public static final class Builder {
        private List<ContextHint> mOriginHints = new ArrayList<>();
        private final InsightActionDetails mActionDetails;
        private final InsightDisplayDetails mDisplayDetails;

        /**
         * Creates a new builder for an actionable insight. By default, no hints are present. They
         * can be added using {@link #setOriginHints(List)}.
         *
         * @param actionDetails the action details of the actionable insight.
         * @param displayDetails the display details of the actionable insight.
         */
        public Builder(
                @NonNull InsightActionDetails actionDetails,
                @NonNull InsightDisplayDetails displayDetails) {
            Objects.requireNonNull(actionDetails, "actionDetails is null");
            Objects.requireNonNull(displayDetails, "displayDetails is null");

            mActionDetails = actionDetails;
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
            return new ActionableInsight(mOriginHints, mActionDetails, mDisplayDetails);
        }
    }
}
