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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.contentrestriction.flags.Flags;
import android.content.LocusId;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.ParcelFileDescriptor;

/**
 * A class representing content to be classified.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_CONTENT_RESTRICTION_API)
public final class Content implements Parcelable {
    /**
     * The LocusId of the content.
     */
    private final LocusId mLocusId;

    /**
     * The MIME type of the content.
     */
    private final String mMimeType;

    /**
     * The URI of the content.
     */
    private final @Nullable Uri mUri;

    /**
     * The data of the content.
     */
    private final @Nullable ParcelFileDescriptor mData;

    // TODO(b/458079181): Add a builder for this class.
    // TODO(b/458080360): Add javadoc for the public methods.
    public Content(LocusId locusId, String mimeType, @Nullable Uri uri,
            @Nullable ParcelFileDescriptor data) {
        mLocusId = locusId;
        mMimeType = mimeType;
        mUri = uri;
        mData = data;
    }

    public Content(@NonNull Parcel in) {
        mMimeType = in.readString8();
        mLocusId = in.readTypedObject(LocusId.CREATOR);
        mUri = in.readTypedObject(Uri.CREATOR);
        mData = in.readTypedObject(ParcelFileDescriptor.CREATOR);
    }

    public LocusId getId() {
        return mLocusId;
    }

    public String getMimeType() {
        return mMimeType;
    }

    @Nullable
    public Uri getUri() {
        return mUri;
    }

    @Nullable
    public ParcelFileDescriptor getData() {
        return mData;
    }

    @NonNull
    public static final Creator<Content> CREATOR = new Creator<Content>() {
        @Override
        public Content createFromParcel(@NonNull Parcel in) {
            return new Content(in);
        }

        @Override
        public Content[] newArray(int size) {
            return new Content[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, @WriteFlags int flags) {
        parcel.writeString8(mMimeType);
        parcel.writeTypedObject(mLocusId, flags);
        parcel.writeTypedObject(mUri, flags);
        parcel.writeTypedObject(mData, flags);
    }
}
