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

package android.service.personalcontext.insight.interaction;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides information about feedback requested for an insight along with user responses.
 *
 * <p>Understanders that want feedback on an insight can call {@code setUserFeedbackRequest} on
 * a {@link android.service.personalcontext.insight.ContextInsight} builder to request user
 * feedback.
 *
 * <p>Renderers that want to allow users to provide feedback can inspect this object to see what
 * kinds of feedback are requested. Some renderers may support initial user responses. If those are
 * available, the renderer builds a {@link Bundle} using the keys from
 * {@link FeedbackField#getKey()} and the type prescribed by {@link FeedbackField#getType()}.
 *
 * <p>When the user asks to supply feedback, the renderer calls
 * {@link android.service.personalcontext.PersonalContextManager#reportUserFeedback} with a Bundle
 * of any initial responses available to send feedback into the system.
 *
 * <p>Once the system receives a user feedback request, it may decide to show a UI to allow the
 * user to fill in missing feedback fields, or to amend their previous response. Once the user has
 * finished, the result will be delivered to the understander via
 * {@link android.service.personalcontext.understander.ContextUnderstanderService#onHandleUserFeedback}.
 * Not all feedback fields may be filled out, so the understander needs to be capable of handling
 * partial responses.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class FeedbackRequest implements Parcelable {
    /** @hide */
    @IntDef(prefix = {"FEEDBACK_TYPE_"}, value = {
            FEEDBACK_TYPE_UNKNOWN,
            FEEDBACK_TYPE_APPROVAL,
            FEEDBACK_TYPE_DESCRIPTION,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FeedbackFieldType {
    }

    /** Unknown type of feedback being requested. */
    public static final int FEEDBACK_TYPE_UNKNOWN = 0;
    /** Feedback being requested is user approval, values are {@code APPROVAL_VALUE_*}. */
    public static final int FEEDBACK_TYPE_APPROVAL = 1;
    /** Feedback being requested is free-form text, values are CharSequence. */
    public static final int FEEDBACK_TYPE_DESCRIPTION = 2;

    /** @hide */
    @IntDef(prefix = {"APPROVAL_VALUE_"}, value = {
            APPROVAL_VALUE_UNKNOWN,
            APPROVAL_VALUE_APPROVE,
            APPROVAL_VALUE_DISAPPROVE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApprovalValueType {
    }

    public static final int APPROVAL_VALUE_UNKNOWN = 0;
    public static final int APPROVAL_VALUE_APPROVE = 1;
    public static final int APPROVAL_VALUE_DISAPPROVE = 2;

    private final List<FeedbackField> mFields;

    /**
     * Creates a new instance with a list of feedback fields.
     *
     * @throws IllegalArgumentException if the fields do not have unique keys
     *
     * @param fields the list of feedback fields being requested
     */
    public FeedbackRequest(@NonNull Collection<FeedbackField> fields) {
        final Set<String> keys = new HashSet<>();
        for (FeedbackField field : fields) {
            if (keys.contains(field.mKey)) {
                throw new IllegalArgumentException("Duplicate key found in fields: " + field.mKey);
            }
            keys.add(field.mKey);
        }

        mFields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    private FeedbackRequest(Parcel in) {
        this(in.readParcelableList(new ArrayList<>(), null, FeedbackField.class));
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelableList(mFields, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<FeedbackRequest> CREATOR = new Creator<>() {
        @Override
        public FeedbackRequest createFromParcel(Parcel in) {
            return new FeedbackRequest(in);
        }

        @Override
        public FeedbackRequest[] newArray(int size) {
            return new FeedbackRequest[size];
        }
    };

    /** Gets the list of feedback fields that are being requested. */
    @NonNull
    public List<FeedbackField> getFields() {
        return mFields;
    }

    /** Feedback request / response for a single field. */
    public static final class FeedbackField implements Parcelable {
        @FeedbackFieldType
        private final int mType;
        @NonNull
        private final String mKey;
        @NonNull
        private final CharSequence mTitle;
        private final CharSequence mDescription;

        /**
         * Creates a new {@link FeedbackField} with a type, a key, and display information.
         *
         * @param type Type of feedback requested (see {@code FeedbackRequest.FEEDBACK_TYPE_*})
         * @param key key to use when putting feedback into the resulting {@link Bundle}
         * @param title title to show when requesting user feedback
         * @param description description to show when requesting user feedback (optional)
         */
        public FeedbackField(
                int type,
                @NonNull String key,
                @NonNull CharSequence title,
                @Nullable CharSequence description) {
            mType = type;
            mKey = requireNonNull(key);
            mTitle = requireNonNull(title);
            mDescription = description;
        }

        private FeedbackField(Parcel in) {
            mType = in.readInt();
            mKey = requireNonNull(in.readString8());
            mTitle = requireNonNull(in.readCharSequence());
            mDescription = in.readCharSequence();
        }

        @NonNull
        public static final Creator<FeedbackField> CREATOR = new Creator<>() {
            @Override
            public FeedbackField createFromParcel(Parcel in) {
                return new FeedbackField(in);
            }

            @Override
            public FeedbackField[] newArray(int size) {
                return new FeedbackField[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mType);
            dest.writeString8(mKey);
            dest.writeCharSequence(mTitle);
            dest.writeCharSequence(mDescription);
        }

        /** Gets the type of feedback being requested. */
        @FeedbackFieldType
        public int getType() {
            return mType;
        }

        /** Gets the key to use in the resulting {@link Bundle} for this feedback field. */
        @NonNull
        public String getKey() {
            return mKey;
        }

        /** Gets the title of this feedback field. */
        @NonNull
        public CharSequence getTitle() {
            return mTitle;
        }

        /** Gets a description of his feedback field. */
        @Nullable
        public CharSequence getDescription() {
            return mDescription;
        }
    }
}

