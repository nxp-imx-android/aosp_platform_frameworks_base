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
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.text.TextUtils;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represents the display details for an insight.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightDisplayDetails implements Parcelable {
    private final CharSequence mTitle;
    private final Icon mIcon;
    private final CharSequence mContentDescription;

    private InsightDisplayDetails(
            @Nullable CharSequence title,
            @Nullable Icon icon,
            @Nullable CharSequence contentDescription) {
        this.mTitle = title;
        this.mIcon = icon;
        this.mContentDescription = contentDescription;
    }

    /**
     * Returns the title of the insight. If {@code null}, the icon should be used instead.
     *
     * @return the title of the insight.
     */
    @Nullable
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Returns the icon of the insight. If {@code null}, the title should be used instead.
     *
     * @return the icon of the insight.
     */
    @Nullable
    public Icon getIcon() {
        return mIcon;
    }

    /**
     * Returns the content description of the insight.
     *
     * @return the content description of the insight.
     */
    @Nullable
    public CharSequence getContentDescription() {
        return mContentDescription;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InsightDisplayDetails that = (InsightDisplayDetails) o;
        return TextUtils.equals(mTitle, that.mTitle)
                && Objects.equals(mIcon, that.mIcon)
                && TextUtils.equals(mContentDescription, that.mContentDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mIcon, mContentDescription);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeCharSequence(mTitle);
        dest.writeTypedObject(mIcon, flags);
        dest.writeCharSequence(mContentDescription);
    }

    private InsightDisplayDetails(@NonNull Parcel in) {
        mTitle = in.readCharSequence();
        mIcon = in.readTypedObject(Icon.CREATOR);
        mContentDescription = in.readCharSequence();
    }

    public static final @NonNull Creator<InsightDisplayDetails> CREATOR =
            new Creator<InsightDisplayDetails>() {
                @Override
                public InsightDisplayDetails createFromParcel(@NonNull Parcel in) {
                    return new InsightDisplayDetails(in);
                }

                @Override
                public InsightDisplayDetails[] newArray(int size) {
                    return new InsightDisplayDetails[size];
                }
            };

    /** Builder for {@link InsightDisplayDetails}. */
    public static final class Builder {
        private CharSequence mTitle;
        private Icon mIcon;
        private CharSequence mContentDescription;

        /**
         * Creates a new builder for the insight display details. Used when only the title is
         * available.
         *
         * @param title the title of the insight.
         */
        public Builder(@NonNull CharSequence title) {
            Preconditions.checkNotNull(title, "title is null");
            mTitle = title;
        }

        /**
         * Creates a new builder for the insight display details. Used when only the icon is
         * available.
         *
         * @param icon the icon of the insight.
         */
        public Builder(@NonNull Icon icon) {
            Preconditions.checkNotNull(icon, "icon is null");
            mIcon = icon;
        }

        /**
         * Creates a new builder for the insight display details. Used when both the title and icon
         * are available.
         *
         * @param title the title of the insight.
         * @param icon the icon of the insight.
         */
        public Builder(@NonNull CharSequence title, @NonNull Icon icon) {
            Preconditions.checkNotNull(title, "title is null");
            Preconditions.checkNotNull(icon, "icon is null");
            mTitle = title;
            mIcon = icon;
        }

        /**
         * Sets the content description of the insight.
         *
         * @param contentDescription the content description of the insight. {@code null} can be
         *     specified to clear the content description.
         */
        @NonNull
        public Builder setContentDescription(@Nullable CharSequence contentDescription) {
            mContentDescription = contentDescription;
            return this;
        }

        /**
         * Builds the {@link InsightDisplayDetails}.
         *
         * @return the {@link InsightDisplayDetails}.
         */
        @NonNull
        public InsightDisplayDetails build() {
            return new InsightDisplayDetails(mTitle, mIcon, mContentDescription);
        }
    }
}
