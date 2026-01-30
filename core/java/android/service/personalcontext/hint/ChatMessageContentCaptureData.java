/*
 * Copyright 2026 The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.view.autofill.AutofillId;

import java.util.Objects;

/**
 * Additional data for a single message extracted from a message view by the Content Capture API.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ChatMessageContentCaptureData implements Parcelable {

    private final @NonNull String mRawParsedTimeString;
    private final @NonNull String mRawParsedDateString;
    private final @NonNull AutofillId mAutofillId;

    private ChatMessageContentCaptureData(
            @NonNull String rawParsedTimeString,
            @NonNull String rawParsedDateString,
            @NonNull AutofillId autofillId) {
        mRawParsedTimeString = rawParsedTimeString;
        mRawParsedDateString = rawParsedDateString;
        mAutofillId = autofillId;
    }

    private ChatMessageContentCaptureData(Parcel in) {
        mRawParsedTimeString = in.readString8();
        mRawParsedDateString = in.readString8();
        mAutofillId = in.readTypedObject(AutofillId.CREATOR);
    }

    /**
     * Returns a user-friendly string representing the time of the message (e.g., "10:00 AM").
     *
     * <p>This string is parsed directly from the message view and returns as-is.
     */
    @NonNull
    public String getRawParsedTimeString() {
        return mRawParsedTimeString;
    }

    /**
     * Returns a user-friendly string representing the date of the message (e.g., "Today").
     *
     * <p>This string is parsed directly from the message view and returns as-is.
     */
    @NonNull
    public String getRawParsedDateString() {
        return mRawParsedDateString;
    }

    /** Returns the {@link AutofillId} of the view that this message corresponds to. */
    @NonNull
    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mRawParsedTimeString);
        dest.writeString8(mRawParsedDateString);
        dest.writeTypedObject(mAutofillId, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChatMessageContentCaptureData that)) return false;
        return Objects.equals(mRawParsedTimeString, that.mRawParsedTimeString)
                && Objects.equals(mRawParsedDateString, that.mRawParsedDateString)
                && Objects.equals(mAutofillId, that.mAutofillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRawParsedTimeString, mRawParsedDateString, mAutofillId);
    }

    @Override
    public String toString() {
        return "ChatMessageContentCaptureData{"
                + "mRawParsedTimeString='"
                + mRawParsedTimeString
                + "'"
                + ", mRawParsedDateString='"
                + mRawParsedDateString
                + "'"
                + ", mAutofillId="
                + mAutofillId
                + '}';
    }

    /** Builder for {@link ChatMessageContentCaptureData}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private @NonNull String mRawParsedTimeString;
        private @NonNull String mRawParsedDateString;
        private @NonNull AutofillId mAutofillId;

        /**
         * Sets a user-friendly string representing the time of the message (e.g., "10:00 AM").
         *
         * <p>This is a required builder input and should be a string that is parsed directly from
         * the message view and provided as-is.
         */
        @NonNull
        public Builder setRawParsedTimeString(@NonNull String rawParsedTimeString) {
            mRawParsedTimeString = requireNonNull(rawParsedTimeString);
            return this;
        }

        /**
         * Sets a user-friendly string representing the date of the message (e.g., "Today").
         *
         * <p>This is a required builder input should be a string that is parsed directly from the
         * message view and provided as-is.
         */
        @NonNull
        public Builder setRawParsedDateString(@NonNull String rawParsedDateString) {
            mRawParsedDateString = requireNonNull(rawParsedDateString);
            return this;
        }

        /**
         * Sets the {@link AutofillId} of the view that this message corresponds to.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setAutofillId(@NonNull AutofillId autofillId) {
            mAutofillId = requireNonNull(autofillId);
            return this;
        }

        /**
         * Builds the {@link ChatMessageContentCaptureData}.
         *
         * @throws NullPointerException if any of {@link #setRawParsedTimeString(String)}, {@link
         *     #setRawParsedDateString(String)}, and {@link #setAutofillId(AutofillId)} are not set.
         */
        @NonNull
        public ChatMessageContentCaptureData build() {
            return new ChatMessageContentCaptureData(
                    requireNonNull(mRawParsedTimeString),
                    requireNonNull(mRawParsedDateString),
                    requireNonNull(mAutofillId));
        }
    }

    public static final @NonNull Creator<ChatMessageContentCaptureData> CREATOR =
            new Creator<>() {
                @Override
                public ChatMessageContentCaptureData createFromParcel(Parcel in) {
                    return new ChatMessageContentCaptureData(in);
                }

                @Override
                public ChatMessageContentCaptureData[] newArray(int size) {
                    return new ChatMessageContentCaptureData[size];
                }
            };
}
