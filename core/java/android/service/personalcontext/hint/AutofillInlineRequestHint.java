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

package android.service.personalcontext.hint;

import android.annotation.FlaggedApi;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Parcel;
import android.service.autofill.augmented.FillRequest;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * A hint representing an incoming request from the autofill framework for inline suggestions for
 * use by augmented autofill.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class AutofillInlineRequestHint extends ContextHint {
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_TASK_ID = "task_id";
    private static final String KEY_REQUEST_TIMESTAMP = "request_timestamp";
    private static final String KEY_ACTIVITY_COMPONENT = "activity_component";
    private static final String KEY_FOCUSED_ID = "focused_id";
    private static final String KEY_AUTOFILL_VALUE = "autofill_value";
    private static final String KEY_INLINE_SUGGESTIONS_REQUEST = "inline_suggestions_request";

    private final int mSessionId;
    private final int mTaskId;
    private final Instant mRequestTimestamp;
    private final ComponentName mActivityComponent;
    private final AutofillId mFocusedId;
    private final AutofillValue mAutofillValue;
    private final InlineSuggestionsRequest mInlineSuggestionsRequest;

    /** Internal constructor only for use by {@link Builder}. */
    private AutofillInlineRequestHint(
            @NonNull ConstructorParams baseParams,
            int sessionId,
            int taskId,
            @NonNull Instant requestTimestamp,
            @NonNull ComponentName activityComponent,
            @NonNull AutofillId focusedId,
            @NonNull AutofillValue autofillValue,
            @NonNull InlineSuggestionsRequest inlineSuggestionsRequest) {
        super(baseParams);
        mSessionId = sessionId;
        mTaskId = taskId;
        mRequestTimestamp = Objects.requireNonNull(requestTimestamp);
        mActivityComponent = Objects.requireNonNull(activityComponent);
        mFocusedId = Objects.requireNonNull(focusedId);
        mAutofillValue = Objects.requireNonNull(autofillValue);
        mInlineSuggestionsRequest = Objects.requireNonNull(inlineSuggestionsRequest);
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    AutofillInlineRequestHint(@NonNull ConstructorParams baseParams, Bundle bundle) {
        super(baseParams);
        mSessionId = bundle.getInt(KEY_SESSION_ID);
        mTaskId = bundle.getInt(KEY_TASK_ID);
        mRequestTimestamp =
                Objects.requireNonNull(
                        bundle.getSerializable(KEY_REQUEST_TIMESTAMP, Instant.class));
        mActivityComponent =
                Objects.requireNonNull(
                        bundle.getParcelable(KEY_ACTIVITY_COMPONENT, ComponentName.class));
        mFocusedId =
                Objects.requireNonNull(bundle.getParcelable(KEY_FOCUSED_ID, AutofillId.class));
        mAutofillValue =
                Objects.requireNonNull(
                        bundle.getParcelable(KEY_AUTOFILL_VALUE, AutofillValue.class));
        mInlineSuggestionsRequest =
                Objects.requireNonNull(
                        bundle.getParcelable(
                                KEY_INLINE_SUGGESTIONS_REQUEST, InlineSuggestionsRequest.class));
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_AUTOFILL_INLINE_REQUEST;
    }

    /**
     * Returns the autofill session ID this request is associated with. This is an internal ID used
     * by the renderer to identify what autofill session to associate a produced insight with.
     */
    public int getSessionId() {
        return mSessionId;
    }

    /**
     * Returns the ID of the task of the activity associated with this request.
     *
     * @see android.app.TaskInfo#taskId
     * @see FillRequest#getTaskId()
     */
    public int getTaskId() {
        return mTaskId;
    }

    /** Returns the timestamp of the request. */
    @NonNull
    public Instant getRequestTimestamp() {
        return mRequestTimestamp;
    }

    /** Returns the component name of the activity involved in autofill. */
    @NonNull
    public ComponentName getActivityComponent() {
        return mActivityComponent;
    }

    /** Returns the ID for the node focused by autofill. */
    @NonNull
    public AutofillId getFocusedId() {
        return mFocusedId;
    }

    /** Returns the {@link AutofillValue} associated with the view to be filled. */
    @NonNull
    public AutofillValue getAutofillValue() {
        return mAutofillValue;
    }

    /** Returns the {@link InlineSuggestionsRequest} associated with this hint. */
    @NonNull
    public InlineSuggestionsRequest getInlineSuggestionsRequest() {
        return mInlineSuggestionsRequest;
    }

    /** @hide */
    @Override
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeInt(mSessionId);
        dest.writeInt(mTaskId);
        dest.writeLong(mRequestTimestamp.toEpochMilli());
        dest.writeString(mActivityComponent.flattenToString());
        dest.writeParcelable(mFocusedId, 0);
        dest.writeParcelable(mAutofillValue, 0);
        // Inline suggestion request has binders inside so we can't use it to sign, just write out
        // some relevant parts.
        dest.writeString(mInlineSuggestionsRequest.getHostPackageName());
        dest.writeInt(mInlineSuggestionsRequest.getHostDisplayId());
        dest.writeParcelableList(mInlineSuggestionsRequest.getInlinePresentationSpecs(), 0);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AutofillInlineRequestHint that)) return false;
        if (!super.equals(o)) return false;
        return mSessionId == that.mSessionId
                && mTaskId == that.mTaskId
                && Objects.equals(mRequestTimestamp, that.mRequestTimestamp)
                && Objects.equals(mActivityComponent, that.mActivityComponent)
                && Objects.equals(mFocusedId, that.mFocusedId)
                && Objects.equals(mAutofillValue, that.mAutofillValue)
                && Objects.equals(mInlineSuggestionsRequest, that.mInlineSuggestionsRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                mSessionId,
                mTaskId,
                mRequestTimestamp,
                mActivityComponent,
                mFocusedId,
                mAutofillValue,
                mInlineSuggestionsRequest);
    }

    @Override
    public String toString() {
        return "AutofillInlineRequestHint{"
                + "mSessionId="
                + mSessionId
                + ", mTaskId="
                + mTaskId
                + ", mRequestTimestamp="
                + mRequestTimestamp
                + ", mActivityComponent="
                + mActivityComponent
                + ", mFocusedId="
                + mFocusedId
                + ", mAutofillValue="
                + mAutofillValue
                + ", mInlineSuggestionsRequest="
                + mInlineSuggestionsRequest
                + '}';
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_TASK_ID, mTaskId);
        bundle.putSerializable(KEY_REQUEST_TIMESTAMP, mRequestTimestamp);
        bundle.putParcelable(KEY_ACTIVITY_COMPONENT, mActivityComponent);
        bundle.putParcelable(KEY_FOCUSED_ID, mFocusedId);
        bundle.putParcelable(KEY_AUTOFILL_VALUE, mAutofillValue);
        bundle.putParcelable(KEY_INLINE_SUGGESTIONS_REQUEST, mInlineSuggestionsRequest);
        return bundle;
    }

    /** Builder used to create a {@link AutofillInlineRequestHint}. */
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private int mSessionId;
        private int mTaskId;
        private Instant mRequestTimestamp;
        private ComponentName mActivityComponent;
        private AutofillId mFocusedId;
        private AutofillValue mAutofillValue;
        private InlineSuggestionsRequest mInlineSuggestionsRequest;

        /** Creates an instance of {@link AutofillInlineRequestHint.Builder}. */
        public Builder() {}

        /**
         * Sets the autofill session ID this request is associated with. This is an internal ID used
         * by the renderer to identify what autofill session to associate a produced insight with.
         *
         * @param sessionId ID of the autofill session this request is associated with
         */
        @NonNull
        public Builder setSessionId(int sessionId) {
            mSessionId = sessionId;
            return this;
        }

        /**
         * Sets the ID of the task of the activity associated with this request.
         *
         * @param taskId ID of the task of the activity associated with this request
         * @see android.app.TaskInfo#taskId
         * @see FillRequest#getTaskId()
         */
        @NonNull
        public Builder setTaskId(int taskId) {
            mTaskId = taskId;
            return this;
        }

        /**
         * Sets the timestamp of the request.
         *
         * @param requestTimestamp the timestamp of the request
         */
        @NonNull
        public Builder setRequestTimestamp(@NonNull Instant requestTimestamp) {
            mRequestTimestamp = requestTimestamp;
            return this;
        }

        /**
         * Sets the component name of the activity involved in autofill.
         *
         * @param activityComponent component name of the activity involved in autofill
         */
        @NonNull
        public Builder setActivityComponent(@NonNull ComponentName activityComponent) {
            mActivityComponent = activityComponent;
            return this;
        }

        /**
         * Sets the ID for the node focused by autofill.
         *
         * @param focusedId ID for the node focused by autofill
         */
        @NonNull
        public Builder setFocusedId(@NonNull AutofillId focusedId) {
            mFocusedId = focusedId;
            return this;
        }

        /**
         * Sets the {@link AutofillValue} associated with the view to be filled.
         *
         * @param autofillValue information on the view to be filled
         */
        @NonNull
        public Builder setAutofillValue(@NonNull AutofillValue autofillValue) {
            mAutofillValue = autofillValue;
            return this;
        }

        /**
         * Sets the {@link InlineSuggestionsRequest} associated with this hint.
         *
         * @param inlineSuggestionsRequest request data for this hint
         */
        @NonNull
        public Builder setInlineSuggestionsRequest(
                @NonNull InlineSuggestionsRequest inlineSuggestionsRequest) {
            mInlineSuggestionsRequest = inlineSuggestionsRequest;
            return this;
        }

        /**
         * Adds a token to the resulting {@link AutofillInlineRequestHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Returns the built {@link AutofillInlineRequestHint}. */
        @NonNull
        public AutofillInlineRequestHint build() {
            return new AutofillInlineRequestHint(
                    mBaseBuilder.build(),
                    mSessionId,
                    mTaskId,
                    Objects.requireNonNull(mRequestTimestamp),
                    Objects.requireNonNull(mActivityComponent),
                    Objects.requireNonNull(mFocusedId),
                    Objects.requireNonNull(mAutofillValue),
                    Objects.requireNonNull(mInlineSuggestionsRequest));
        }
    }
}
