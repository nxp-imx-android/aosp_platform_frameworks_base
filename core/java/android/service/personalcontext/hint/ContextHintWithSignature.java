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
import android.annotation.TestApi;
import android.os.Parcel;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.ContextInsight;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Wrapper for a {@link ContextHint} that includes verified information about provenance. Instances
 * will be provided to {@link android.service.personalcontext.refiner.HintRefinerService}, and
 * {@link android.service.personalcontext.understander.ContextUnderstanderService} service
 * implementations. Instances will also be available from {@link ContextInsight#getOriginHints()}.
 *
 * <p>When a {@link ContextHint} is published,
 * {@link com.android.server.personalcontext.PersonalContextManagerService} generates a
 * {@link ContextHintWithSignature}. Using the contents of the hint to create a byte-level
 * signature,  {@link ContextHintWithSignature} guarantees that the content has not been manipulated
 * when passed between various PersonalContext components, which only work with
 * {@link ContextHintWithSignature} instances. The signature is ultimately checked when an
 * {@link ContextInsight} is published with the hint. The insight will not be delivered to the
 * renderers if the signature is invalid.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ContextHintWithSignature {
    /** @hide */
    @TestApi
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    private final @NonNull byte[] mHash;
    private final @NonNull ContextHintWrapper mContextHintWrapper;
    private final @NonNull List<ContextHintWithSignature> mAttributionHints;
    private final @Nullable String mOriginatingPackageName;
    private final @NonNull Set<RenderToken> mRenderTokens;

    private ContextHintWithSignature(
            @NonNull byte[] hash,
            @NonNull ContextHintWrapper contextHint,
            @NonNull List<ContextHintWithSignature> attributionHints,
            @Nullable String originatingPackageName,
            @Nullable Set<RenderToken> renderTokens) {
        mHash = hash;
        mContextHintWrapper = contextHint;
        mAttributionHints = attributionHints;
        mOriginatingPackageName = originatingPackageName;
        mRenderTokens = renderTokens;
    }

    /**
     * Used by {@link ContextHintWithSignatureWrapper#CREATOR}.
     * @hide
     */
    public ContextHintWithSignature(Parcel source) {
        mHash = Objects.requireNonNull(source.createByteArray());
        mContextHintWrapper = Objects.requireNonNull(
                source.readParcelable(/* loader= */ null, ContextHintWrapper.class));

        mAttributionHints = Collections.unmodifiableList(
                ContextHintWithSignatureWrapper.unwrapList(source.readParcelableList(
                        new ArrayList<>(),
                        /* loader= */ null,
                        ContextHintWithSignatureWrapper.class)));

        mOriginatingPackageName = source.readString8();

        final ArrayList<RenderToken> renderTokens = new ArrayList<>();
        source.readParcelableList(renderTokens, /* leader= */ null, RenderToken.class);

        mRenderTokens = new HashSet<>(renderTokens);
    }

    /** Returns the {@link ContextHint} contained in this wrapper. */
    @NonNull
    public ContextHint getContextHint() {
        return mContextHintWrapper.getContextHint();
    }

    /** Returns the {@link ContextHintWithSignature} hints that were used to create this hint. */
    @NonNull
    public Set<ContextHintWithSignature> getAttributionHints() {
        // Note that order matters for signing the data. We continue to internally store the
        // attribution hints as a list to guarantee the signature stays intact and instead create
        // a copy to expose it externally as a set.
        return new HashSet<>(mAttributionHints);
    }

    /**
     * Returns the package that originated this hint.
     *
     * <p>This value may be {@code null} provided if the hint originated from the system instead of
     * from an external component. Code that cares about the origin or correctness of the hint can
     * assume that the hint was generated by the system, and not be some other APK, and that the
     * hint contents have not been tampered with.
     *
     * @return the package that created the hint or null when the originating package is unknown
     */
    @Nullable
    public String getOriginatingPackage() {
        return mOriginatingPackageName;
    }

    /** Returns the {@link RenderToken}s that are associated with this hint. */
    @NonNull
    public Set<RenderToken> getRenderTokens() {
        return mRenderTokens;
    }

    /**
     * Checks that the data hasn't been tampered with.
     * @hide
     */
    @TestApi
    public boolean isSignatureValid(@NonNull SecretKeySpec secretKey)
            throws GeneralSecurityException {
        return Arrays.equals(mHash, signData(
                mContextHintWrapper,
                mAttributionHints,
                mOriginatingPackageName,
                mRenderTokens,
                secretKey));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final ContextHintWithSignature that = (ContextHintWithSignature) o;
        return Objects.equals(mContextHintWrapper, that.mContextHintWrapper)
                && Objects.equals(mAttributionHints, that.mAttributionHints)
                && Objects.equals(mOriginatingPackageName, that.mOriginatingPackageName)
                && Objects.equals(mRenderTokens, that.mRenderTokens)
                && Objects.deepEquals(mHash, that.mHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mContextHintWrapper,
                mAttributionHints,
                mOriginatingPackageName,
                mRenderTokens,
                Arrays.hashCode(mHash));
    }

    @Override
    public String toString() {
        return "ContextHintWithSignature{"
                + "contextHint=" + mContextHintWrapper.getContextHint()
                + ", originatingPackageName='" + mOriginatingPackageName + '\''
                + ", renderTokens=" + mRenderTokens
                + '}';
    }

    /**
     * Used by {@link ContextHintWithSignatureWrapper#writeToParcel(Parcel, int)}.
     *
     * @hide
     */
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(mHash);
        dest.writeParcelable(mContextHintWrapper, 0);
        dest.writeParcelableList(ContextHintWithSignatureWrapper.wrapList(mAttributionHints), 0);
        dest.writeString8(mOriginatingPackageName);
        dest.writeParcelableList(new ArrayList<>(mRenderTokens), 0);
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignature} into a list of
     * {@link ContextHint}.
     *
     * @hide
     */
    @NonNull
    public static List<ContextHint> unwrapList(
            @NonNull Collection<ContextHintWithSignature> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignature} into a collection
     * of {@link ContextHint}.
     *
     * @hide
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

    private static byte[] signData(
            @NonNull ContextHintWrapper contextHintWrapper,
            @NonNull List<ContextHintWithSignature> attributionHints,
            @Nullable String originatingPackageName,
            @Nullable Set<RenderToken> renderTokens,
            @NonNull SecretKeySpec secretKey) throws GeneralSecurityException {
        final Parcel scratch = Parcel.obtain();
        try {
            contextHintWrapper.getContextHint().writeToSignatureParcel(scratch);
            scratch.writeParcelableList(
                    ContextHintWithSignatureWrapper.wrapList(attributionHints), 0);
            scratch.writeString(originatingPackageName);
            for (RenderToken renderToken : orderRenderTokens(renderTokens)) {
                scratch.writeParcelable(renderToken, 0);
            }

            // Generate the signature.
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(scratch.marshall());
        } finally {
            scratch.recycle();
        }
    }

    private static List<RenderToken> orderRenderTokens(Collection<RenderToken> renderTokens) {
        final List<RenderToken> result = new ArrayList<>(renderTokens);
        Collections.sort(result);
        return result;
    }

    /**
     * Builder for {@link ContextHintWithSignature}.
     * @hide
     */
    @TestApi
    public static final class Builder {
        private final @NonNull ContextHintWrapper mContextHintWrapper;
        private final @NonNull List<ContextHintWithSignature> mAttributionHints = new ArrayList<>();
        private final @NonNull SecretKeySpec mSecretKey;
        private @Nullable String mOriginatingPackageName;
        private final @Nullable Set<RenderToken> mRenderTokens = new HashSet<>();

        /** @hide */
        public Builder(
                @NonNull ContextHintWrapper contextHintWrapper, @NonNull SecretKeySpec secretKey) {
            mContextHintWrapper = contextHintWrapper;
            mSecretKey = secretKey;
        }

        /** Used by CTS tests to create an instance of {@link ContextHintWithSignature}. */
        public Builder(@NonNull ContextHint contextHint, @NonNull SecretKeySpec secretKey) {
            this(new ContextHintWrapper(contextHint), secretKey);
        }

        /** Sets the originating package of the ContextHint. */
        @NonNull
        public Builder setOriginatingPackage(@Nullable String originatingPackageName) {
            mOriginatingPackageName = originatingPackageName;
            return this;
        }

        /** Sets the RenderToken. */
        @NonNull
        public Builder addRenderTokens(@NonNull Collection<RenderToken> renderTokens) {
            mRenderTokens.addAll(renderTokens);
            return this;
        }

        /** Adds an attribution hint. */
        @NonNull
        public Builder addAttributionHint(@NonNull ContextHintWithSignature hint) {
            mAttributionHints.add(hint);
            return this;
        }

        /** Adds multiple attribution hint. */
        @NonNull
        public Builder addAttributionHints(@NonNull Collection<ContextHintWithSignature> hints) {
            mAttributionHints.addAll(hints);
            return this;
        }

        /** Signs the data pieces and builds an instance of {@link ContextHintWithSignature}. */
        @NonNull
        public ContextHintWithSignature build() throws GeneralSecurityException {
            // Build the new instance.
            return new ContextHintWithSignature(
                    signData(
                            mContextHintWrapper,
                            mAttributionHints,
                            mOriginatingPackageName,
                            mRenderTokens,
                            mSecretKey),
                    mContextHintWrapper,
                    mAttributionHints,
                    mOriginatingPackageName,
                    mRenderTokens);
        }
    }
}
