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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.view.autofill.AutofillId;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Data for a single message within a conversation snapshot.
 *
 * <p>This class represents the semantic information extracted from a single message view on screen,
 * such as a message bubble. It includes the text content, author, and relevant timestamps.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ChatMessageData implements Parcelable {

    private final @NonNull String mText;
    private final @NonNull String mAuthor;
    private final @NonNull Instant mReferenceTime;
    private final boolean mIsOutgoingMessage;
    private final @NonNull String mTimeText;
    private final @NonNull String mDateText;
    private final @NonNull AutofillId mAutofillId;
    private final @Nullable String mContentDescription;

    private ChatMessageData(
            @NonNull String text,
            @NonNull String author,
            @NonNull Instant referenceTime,
            boolean isOutgoingMessage,
            @NonNull String timeText,
            @NonNull String dateText,
            @NonNull AutofillId autofillId,
            @Nullable String contentDescription) {
        mText = text;
        mAuthor = author;
        mReferenceTime = referenceTime;
        mIsOutgoingMessage = isOutgoingMessage;
        mTimeText = timeText;
        mDateText = dateText;
        mAutofillId = autofillId;
        mContentDescription = contentDescription;
    }

    private ChatMessageData(Parcel in) {
        mText = in.readString8();
        mAuthor = in.readString8();
        mReferenceTime = Instant.ofEpochMilli(in.readLong());
        mIsOutgoingMessage = in.readBoolean();
        mTimeText = in.readString8();
        mDateText = in.readString8();
        mAutofillId = in.readTypedObject(AutofillId.CREATOR);
        mContentDescription = in.readString8();
    }

    /** Returns the text of the message. */
    @NonNull
    public String getText() {
        return mText;
    }

    /** Returns the author of the message. */
    @NonNull
    public String getAuthor() {
        return mAuthor;
    }

    /** Returns the reference timestamp of the message. */
    @NonNull
    public Instant getReferenceTime() {
        return mReferenceTime;
    }

    /** Returns {@code true} if this is an outgoing message, and {@code false} otherwise. */
    public boolean isOutgoingMessage() {
        return mIsOutgoingMessage;
    }

    /** Returns a user-friendly string representing the time of the message (e.g., "10:00 AM"). */
    @NonNull
    public String getTimeText() {
        return mTimeText;
    }

    /** Returns a user-friendly string representing the date of the message (e.g., "Today"). */
    @NonNull
    public String getDateText() {
        return mDateText;
    }

    /** Returns the {@link AutofillId} of the view that this message corresponds to. */
    @NonNull
    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    /**
     * Returns the content description of the view that this message corresponds to. This may be
     * {@code null} if the view does not have a content description.
     */
    @Nullable
    public String getContentDescription() {
        return mContentDescription;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mText);
        dest.writeString8(mAuthor);
        dest.writeLong(mReferenceTime.toEpochMilli());
        dest.writeBoolean(mIsOutgoingMessage);
        dest.writeString8(mTimeText);
        dest.writeString8(mDateText);
        dest.writeTypedObject(mAutofillId, flags);
        dest.writeString8(mContentDescription);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessageData)) return false;
        ChatMessageData that = (ChatMessageData) o;
        return mIsOutgoingMessage == that.mIsOutgoingMessage
                && Objects.equals(mText, that.mText)
                && Objects.equals(mAuthor, that.mAuthor)
                && Objects.equals(mReferenceTime, that.mReferenceTime)
                && Objects.equals(mTimeText, that.mTimeText)
                && Objects.equals(mDateText, that.mDateText)
                && Objects.equals(mAutofillId, that.mAutofillId)
                && Objects.equals(mContentDescription, that.mContentDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mText,
                mAuthor,
                mReferenceTime,
                mIsOutgoingMessage,
                mTimeText,
                mDateText,
                mAutofillId,
                mContentDescription);
    }

    @Override
    public String toString() {
        return "ChatMessageData{"
                + "mText='"
                + mText
                + "'"
                + ", mAuthor='"
                + mAuthor
                + "'"
                + ", mReferenceTime="
                + mReferenceTime
                + ", mIsOutgoingMessage="
                + mIsOutgoingMessage
                + ", mTimeText='"
                + mTimeText
                + "'"
                + ", mDateText='"
                + mDateText
                + "'"
                + ", mAutofillId="
                + mAutofillId
                + ", mContentDescription='"
                + mContentDescription
                + "'"
                + "}";
    }

    /** Builder for {@link ChatMessageData}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private @Nullable String mText;
        private @Nullable String mAuthor;
        private @Nullable Instant mReferenceTime;
        private @Nullable AutofillId mAutofillId;
        private @Nullable Boolean mIsOutgoingMessage;
        private @Nullable String mTimeText;
        private @Nullable String mDateText;
        private @Nullable String mContentDescription;

        public Builder() {}

        /** Sets whether this is an outgoing message. */
        @NonNull
        public Builder setOutgoingMessage(boolean isOutgoingMessage) {
            mIsOutgoingMessage = isOutgoingMessage;
            return this;
        }

        /** Sets the text of the message. */
        @NonNull
        public Builder setText(@NonNull String text) {
            mText = text;
            return this;
        }

        /** Sets the author of the message. */
        @NonNull
        public Builder setAuthor(@NonNull String author) {
            mAuthor = author;
            return this;
        }

        /** Sets the reference timestamp of the message. */
        @NonNull
        public Builder setReferenceTime(@NonNull Instant referenceTime) {
            mReferenceTime = referenceTime;
            return this;
        }

        /** Sets the {@link AutofillId} of the view that this message corresponds to. */
        @NonNull
        public Builder setAutofillId(@NonNull AutofillId autofillId) {
            mAutofillId = autofillId;
            return this;
        }

        /** Sets a user-friendly string representing the time of the message (e.g., "10:00 AM"). */
        @NonNull
        public Builder setTimeText(@NonNull String timeText) {
            mTimeText = timeText;
            return this;
        }

        /** Sets a user-friendly string representing the date of the message (e.g., "Today"). */
        @NonNull
        public Builder setDateText(@NonNull String dateText) {
            mDateText = dateText;
            return this;
        }

        /** Sets the content description of the view that this message corresponds to. */
        @NonNull
        public Builder setContentDescription(@Nullable String contentDescription) {
            mContentDescription = contentDescription;
            return this;
        }

        /** Builds the {@link ChatMessageData}. */
        @NonNull
        public ChatMessageData build() {
            return new ChatMessageData(
                    requireNonNull(mText),
                    requireNonNull(mAuthor),
                    requireNonNull(mReferenceTime),
                    requireNonNull(mIsOutgoingMessage),
                    requireNonNull(mTimeText),
                    requireNonNull(mDateText),
                    requireNonNull(mAutofillId),
                    mContentDescription);
        }
    }

    public static final @NonNull Creator<ChatMessageData> CREATOR =
            new Creator<>() {
                @Override
                public ChatMessageData createFromParcel(Parcel in) {
                    return new ChatMessageData(in);
                }

                @Override
                public ChatMessageData[] newArray(int size) {
                    return new ChatMessageData[size];
                }
            };
}
