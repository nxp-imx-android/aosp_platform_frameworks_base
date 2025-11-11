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

package android.app.contentrestriction;

import android.annotation.IntDef;
import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A result reported by a Content Restriction app in response to a content classification request.
 *
 * @hide
 */
public final class ContentClassificationResult implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "TYPE_" }, value = {
            TYPE_ALLOWED,
            TYPE_BLOCKED,
            TYPE_UNCLASSIFIED
    })
    public @interface ClassificationType {}

    /**
     * Indicates that the content is allowed.
     */
    public static final int TYPE_ALLOWED = 0;

    /**
     * Indicates that the content is blocked.
     */
    public static final int TYPE_BLOCKED = 1;

    /**
     * Indicates that the content could not be classified by the content restriction app.
     */
    public static final int TYPE_UNCLASSIFIED = 2;

    /**
     * The LocusId of the content that was filtered.
     */
    private final LocusId mLocusId;

    /**
     * The classification type of the content.
     */
    private final @ClassificationType int mType;

    // TODO(b/458080360): Add javadoc for the public methods.
    public ContentClassificationResult(LocusId locusId, @ClassificationType int type) {
        mLocusId = locusId;
        mType = type;
    }

    public ContentClassificationResult(Parcel in) {
        mLocusId = in.readTypedObject(LocusId.CREATOR);
        mType = in.readInt();
    }

    public LocusId getLocusId() {
        return mLocusId;
    }

    public @ClassificationType int getType() {
        return mType;
    }

    public boolean isAllowed() {
        return mType == TYPE_ALLOWED;
    }

    public static final Creator<ContentClassificationResult> CREATOR =
            new Creator<ContentClassificationResult>() {
        @Override
        public ContentClassificationResult createFromParcel(Parcel in) {
            return new ContentClassificationResult(in);
        }

        @Override
        public ContentClassificationResult[] newArray(int size) {
            return new ContentClassificationResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mLocusId, flags);
        parcel.writeInt(mType);
    }
}
