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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.time.Instant;
import java.util.Objects;

/**
 * Data for a single message within a conversation.
 *
 * <p>If this chat message originated from the Content Capture API, it will contain additional
 * metadata in {@link #getContentCaptureData()}.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ChatMessageData implements Parcelable {

    private final @NonNull String mText;
    private final @NonNull String mAuthor;
    private final @NonNull Instant mReferenceTime;
    private final boolean mIsOutgoingMessage;
    private final @Nullable String mContentDescription;
    private final @Nullable ChatMessageContentCaptureData mContentCaptureData;

    private ChatMessageData(
            @NonNull String text,
            @NonNull String author,
            @NonNull Instant referenceTime,
            boolean isOutgoingMessage,
            @Nullable String contentDescription,
            @Nullable ChatMessageContentCaptureData contentCaptureData) {
        mText = text;
        mAuthor = author;
        mReferenceTime = referenceTime;
        mIsOutgoingMessage = isOutgoingMessage;
        mContentDescription = contentDescription;
        mContentCaptureData = contentCaptureData;
    }

    private ChatMessageData(Parcel in) {
        mText = in.readString8();
        mAuthor = in.readString8();
        mReferenceTime = Instant.ofEpochMilli(in.readLong());
        mIsOutgoingMessage = in.readBoolean();
        mContentDescription = in.readString8();
        mContentCaptureData = in.readTypedObject(ChatMessageContentCaptureData.CREATOR);
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

    /**
     * Returns the content description of the view that this message corresponds to. This may be
     * {@code null} if the view does not have a content description.
     */
    @Nullable
    public String getContentDescription() {
        return mContentDescription;
    }

    /** Returns the content capture metadata associated with this message, if any. */
    @Nullable
    public ChatMessageContentCaptureData getContentCaptureData() {
        return mContentCaptureData;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mText);
        dest.writeString8(mAuthor);
        dest.writeLong(mReferenceTime.toEpochMilli());
        dest.writeBoolean(mIsOutgoingMessage);
        dest.writeString8(mContentDescription);
        dest.writeTypedObject(mContentCaptureData, flags);
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
                && Objects.equals(mContentDescription, that.mContentDescription)
                && Objects.equals(mContentCaptureData, that.mContentCaptureData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mText,
                mAuthor,
                mReferenceTime,
                mIsOutgoingMessage,
                mContentDescription,
                mContentCaptureData);
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
                + ", mContentDescription='"
                + mContentDescription
                + "'"
                + ", mContentCaptureData="
                + mContentCaptureData
                + "}";
    }

    /** Builder for {@link ChatMessageData}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private @Nullable String mText;
        private @Nullable String mAuthor;
        private @Nullable Instant mReferenceTime;
        private @Nullable Boolean mIsOutgoingMessage;
        private @Nullable String mContentDescription;
        private @Nullable ChatMessageContentCaptureData mContentCaptureData;

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
            mText = requireNonNull(text);
            return this;
        }

        /** Sets the author of the message. */
        @NonNull
        public Builder setAuthor(@NonNull String author) {
            mAuthor = requireNonNull(author);
            return this;
        }

        /** Sets the reference timestamp of the message. */
        @NonNull
        public Builder setReferenceTime(@NonNull Instant referenceTime) {
            mReferenceTime = requireNonNull(referenceTime);
            return this;
        }

        /** Sets the content description of the view that this message corresponds to. */
        @NonNull
        public Builder setContentDescription(@NonNull String contentDescription) {
            mContentDescription = requireNonNull(contentDescription);
            return this;
        }

        /** Sets the content capture metadata associated with this message. */
        @NonNull
        public Builder setContentCaptureData(
                @NonNull ChatMessageContentCaptureData contentCaptureData) {
            mContentCaptureData = requireNonNull(contentCaptureData);
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
                    mContentDescription,
                    mContentCaptureData);
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
