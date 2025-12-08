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
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;

import com.android.internal.util.Preconditions;

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
            @NonNull ContextInsight.ConstructorParams baseParams,
            @NonNull InsightActionDetails actionDetails,
            @NonNull InsightDisplayDetails displayDetails) {
        super(baseParams);
        mActionDetails = actionDetails;
        mDisplayDetails = displayDetails;
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    ActionableInsight(@NonNull ContextInsight.ConstructorParams baseParams, @NonNull Bundle b) {
        this(
                baseParams,
                b.getParcelable(KEY_ACTION_DETAILS, InsightActionDetails.class),
                b.getParcelable(KEY_DISPLAY_DETAILS, InsightDisplayDetails.class));
    }

    @Override
    @NonNull
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
     * (such as an {@link Intent}) that can be used when the insight triggers.
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

    /** @hide */
    @Override
    public void accept(@NonNull InsightVisitor visitor) {
        visitor.visit(this);
    }

    /** Builder for {@link ActionableInsight}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final InsightActionDetails mActionDetails;
        private final InsightDisplayDetails mDisplayDetails;

        /**
         * Creates a new builder for an actionable insight.
         *
         * @param actionDetails the action details of the actionable insight
         * @param displayDetails the display details of the actionable insight
         */
        public Builder(
                @NonNull InsightActionDetails actionDetails,
                @NonNull InsightDisplayDetails displayDetails) {
            mActionDetails = Preconditions.checkNotNull(actionDetails, "actionDetails is null");
            mDisplayDetails = Preconditions.checkNotNull(displayDetails, "displayDetails is null");
        }

        /**
         * Adds an origin {@link ContextHint} to the resulting {@link BundleInsight}.
         *
         * @param hint the origin {@link ContextHint} to add
         */
        @NonNull
        public Builder addOriginHint(@NonNull ContextHintWithSignature hint) {
            mBaseBuilder.addOriginHint(hint);
            return this;
        }

        /**
         * Adds a token to the resulting {@link ContextInsight}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * Builds the actionable insight.
         *
         * @return the actionable insight
         */
        @NonNull
        public ActionableInsight build() {
            return new ActionableInsight(mBaseBuilder.build(), mActionDetails, mDisplayDetails);
        }
    }
}
