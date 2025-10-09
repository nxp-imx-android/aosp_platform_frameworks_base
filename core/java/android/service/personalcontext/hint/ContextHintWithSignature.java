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
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Wrapper for a {@link ContextHint} that includes verified information about provenance.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ContextHintWithSignature implements Parcelable {
    /** @hide */
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    private final @NonNull byte[] mRawData;
    private final @NonNull byte[] mHash;
    private final @NonNull ContextHintWrapper mContextHintWrapper;
    private final @NonNull List<ContextHintWithSignature> mAttributionHints;
    private final @NonNull String mOriginatingPackage;
    private final @Nullable RenderToken mRenderToken;

    private ContextHintWithSignature(
            @NonNull byte[] rawData,
            @NonNull byte[] hash,
            @NonNull ContextHintWrapper contextHint,
            @NonNull List<ContextHintWithSignature> attributionHints,
            @NonNull String originatingPackage,
            @Nullable RenderToken renderToken) {
        mRawData = rawData;
        mHash = hash;
        mContextHintWrapper = contextHint;
        mAttributionHints = attributionHints;
        mOriginatingPackage = originatingPackage;
        mRenderToken = renderToken;
    }

    private ContextHintWithSignature(Parcel source) {
        mRawData = Objects.requireNonNull(source.createByteArray());
        mHash = Objects.requireNonNull(source.createByteArray());

        final Parcel details = Parcel.obtain();
        try {
            details.unmarshall(mRawData, 0, mRawData.length);
            details.setDataPosition(0);

            mContextHintWrapper = Objects.requireNonNull(
                    details.readParcelable(/* loader= */ null, ContextHintWrapper.class));

            final List<ContextHintWithSignature> hints = new ArrayList<>();
            details.readParcelableList(hints, /* loader= */ null, ContextHintWithSignature.class);
            mAttributionHints = hints;

            mOriginatingPackage = Objects.requireNonNull(details.readString());
            mRenderToken = details.readParcelable(/* loader= */ null, RenderToken.class);
        } finally {
            details.recycle();
        }
    }

    /** Returns the {@link ContextHint} contained in this wrapper. */
    @NonNull
    public ContextHint getContextHint() {
        return mContextHintWrapper.getContextHint();
    }

    /** Returns the {@link ContextHintWithSignature} hints that were used to create this hint. */
    @NonNull
    public List<ContextHintWithSignature> getAttributionHints() {
        return mAttributionHints;
    }

    /** Returns the package that originated this hint. */
    @NonNull
    public String getOriginatingPackage() {
        return mOriginatingPackage;
    }

    /** Returns the {@link RenderToken} that is associated with this hint. */
    @Nullable
    public RenderToken getRenderToken() {
        return mRenderToken;
    }

    /**
     * Checks that the data hasn't been tampered with.
     * @hide
     */
    public boolean isSignatureValid(SecretKeySpec secretKey) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKey);
        final byte[] signature = mac.doFinal(mRawData);
        return Arrays.equals(mHash, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final ContextHintWithSignature that = (ContextHintWithSignature) o;
        return Objects.deepEquals(mRawData, that.mRawData)
                && Objects.deepEquals(mHash, that.mHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mRawData), Arrays.hashCode(mHash));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(mRawData);
        dest.writeByteArray(mHash);
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignature} into a list of
     * {@link ContextHint}.
     */
    @NonNull
    public static List<ContextHint> unwrapList(
            @NonNull Collection<ContextHintWithSignature> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignature} into a collection
     * of {@link ContextHint}.
     */
    @NonNull
    public static <T extends Collection<ContextHint>> T unwrapInto(
            @NonNull Collection<ContextHintWithSignature> wrappers,
            @NonNull T into) {
        for (ContextHintWithSignature wrapper : wrappers) {
            into.add(wrapper.getContextHint());
        }
        return into;
    }

    public static final @NonNull Creator<ContextHintWithSignature> CREATOR =
            new Creator<>() {
                @Override
                public ContextHintWithSignature createFromParcel(Parcel source) {
                    return new ContextHintWithSignature(source);
                }

                @Override
                public ContextHintWithSignature[] newArray(int size) {
                    return new ContextHintWithSignature[size];
                }
            };

    /** @hide */
    public static final class Builder {
        private final @NonNull ContextHintWrapper mContextHintWrapper;
        private final @NonNull List<ContextHintWithSignature> mAttributionHints = new ArrayList<>();
        private final @NonNull String mOriginatingPackage;
        private @Nullable RenderToken mRenderToken = null;

        public Builder(
                @NonNull ContextHintWrapper contextHintWrapper,
                @NonNull String originatingPackage) {
            mContextHintWrapper = contextHintWrapper;
            mOriginatingPackage = originatingPackage;
        }

        public Builder(
                @NonNull ContextHint contextHint,
                @NonNull String originatingPackage) {
            this(new ContextHintWrapper(contextHint), originatingPackage);
        }

        /** Sets the RenderToken. */
        public Builder setRenderToken(RenderToken renderToken) {
            mRenderToken = renderToken;
            return this;
        }

        /** Adds an attribution hint. */
        public Builder addAttributionHint(ContextHintWithSignature hint) {
            mAttributionHints.add(hint);
            return this;
        }

        /** Adds multiple attribution hint. */
        public Builder addAttributionHints(Collection<ContextHintWithSignature> hints) {
            mAttributionHints.addAll(hints);
            return this;
        }

        /** Signs the data pieces and builds an instance of {@link ContextHintWithSignature}. */
        public ContextHintWithSignature buildAndSign(SecretKeySpec secretKey)
                throws GeneralSecurityException {
            // Build the raw data byte[].
            final byte[] rawData;
            final Parcel scratch = Parcel.obtain();
            try {
                scratch.writeParcelable(mContextHintWrapper, 0);
                scratch.writeParcelableList(mAttributionHints, 0);
                scratch.writeString(mOriginatingPackage);
                scratch.writeParcelable(mRenderToken, 0);

                rawData = scratch.marshall();
            } finally {
                scratch.recycle();
            }

            // Generate the signature.
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            final byte[] signature = mac.doFinal(rawData);

            // Build the new instance.
            return new ContextHintWithSignature(
                    rawData,
                    signature,
                    mContextHintWrapper,
                    mAttributionHints,
                    mOriginatingPackage,
                    mRenderToken);
        }
    }
}
