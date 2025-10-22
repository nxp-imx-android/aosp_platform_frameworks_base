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
import android.annotation.IntDef;
import android.annotation.TestApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for insights. Subclasses will provide concrete implementations. The context
 * engine flow will produce these insights, which will ultimately make their way to insight
 * renderers, where they will be rendered as UI to the user.
 *
 * Users of this class can use instanceof to determine the type of the insight.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextInsight {
    private static final String TAG = "ContextInsight";

    private static final String KEY_INSIGHT_ID = "key_insight_id";
    private static final String KEY_INSIGHT_TYPE = "key_insight_type";
    private static final String KEY_ORIGIN_HINTS = "key_origin_hints";

    /**
     * Bundle key used to store the data from the insight implementation, retrieved through
     * {@link #toBundleImpl()}.
     */
    static final String KEY_INSIGHT_DATA = "key_insight_data";

    /**
     * Enumeration of insight types.
     *
     * @hide
     */
    @IntDef(
            prefix = {"INSIGHT_TYPE_"},
            value = {INSIGHT_TYPE_ERROR, INSIGHT_TYPE_BUNDLE, INSIGHT_TYPE_ACTIONABLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface InsightType {}

    /** Type identifier for an error insight (to return when there is an unparceling error). */
    static final int INSIGHT_TYPE_ERROR = -1;

    /**
     * Type identifier for {@link BundleInsight}.
     *
     * @hide
     */
    @VisibleForTesting
    public static final int INSIGHT_TYPE_BUNDLE = 1;

    /** Type identifier for {@link ActionableInsight}. */
    static final int INSIGHT_TYPE_ACTIONABLE = 2;

    /**
     * Object returned when there is an unparcelling error.
     * @hide
     */
    @NonNull
    private static final ContextInsight ERROR_INSIGHT = new ContextInsight(List.of()) {
        @Override
        @InsightType public int getInsightType() {
            return INSIGHT_TYPE_ERROR;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            return new Bundle();
        }
    };

    private final UUID mId;
    private final List<ContextHint> mOriginHints;

    /**
     * Internal constructor only for use by {@link #createInsightFromBundle(Bundle)}. This should be
     * called by subclasses in their private constructors used for
     * {@link #createInsightFromBundle(Bundle)}.
     *
     * @hide
     */
    ContextInsight(@NonNull Bundle b) {
        mId = UUID.fromString(b.getString(KEY_INSIGHT_ID));
        mOriginHints = Collections.unmodifiableList(
                ContextHintWrapper.unwrapList(
                        Objects.requireNonNull(b.getParcelableArrayList(
                                KEY_ORIGIN_HINTS, ContextHintWrapper.class))));
    }

    /**
     * Internal constructor for generating a new insight. This should be called by subclasses in
     * their public constructors.
     *
     * @hide
     */
    ContextInsight(List<ContextHint> originHints) {
        mId = UUID.randomUUID();
        mOriginHints = Collections.unmodifiableList(originHints);
    }

    /**
     * Returns the {@link InsightType} of this hint.
     * @hide
     */
    @InsightType
    public abstract int getInsightType();

    /**
     * Returns the unique identifier for this insight.
     */
    @NonNull
    public final UUID getInsightId() {
        return mId;
    }

    /**
     * Returns the list of {@link ContextHint}s that were used to generate this insight.
     */
    @NonNull
    public final List<ContextHint> getOriginHints() {
        return mOriginHints;
    }

    @NonNull abstract Bundle toBundleImpl();

    /**
     * Return the {@link Bundle} representation of this insight's data.
     * @hide
     */
    @TestApi
    @NonNull
    public Bundle toBundle() {
        final Bundle b = new Bundle();
        b.putInt(KEY_INSIGHT_TYPE, getInsightType());
        b.putString(KEY_INSIGHT_ID, mId.toString());
        b.putParcelableList(KEY_ORIGIN_HINTS, ContextHintWrapper.wrapList(mOriginHints));
        b.putBundle(KEY_INSIGHT_DATA, toBundleImpl());
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextInsight)) return false;

        final ContextInsight other = (ContextInsight) o;
        return Objects.equals(mId, other.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    /**
     * Unbundles an insight into the correct subclass of insight based on the insight type.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public static ContextInsight createInsightFromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return ERROR_INSIGHT;
        }
        final int type = bundle.getInt(KEY_INSIGHT_TYPE, INSIGHT_TYPE_ERROR);
        try {
            return switch (type) {
                case INSIGHT_TYPE_BUNDLE -> new BundleInsight(bundle);
                case INSIGHT_TYPE_ACTIONABLE -> new ActionableInsight(bundle);
                default -> ERROR_INSIGHT;
            };
        } catch (Exception e) {
            Log.e(TAG, "Error creating insight", e);
            return ERROR_INSIGHT;
        }
    }
}
