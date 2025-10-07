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
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A piece of input data into the personal context engine.
 *
 * Hints may describe some current state of the device or represent an event that may be of use for
 * kicking off an understanding flow.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextHint {
    private static final String TAG = "ContextHint";

    /**
     * Enumeration of hint types.
     *
     * @hide
     */
    @IntDef(prefix = {"HINT_TYPE_"}, value = {HINT_TYPE_ERROR, HINT_TYPE_BUNDLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HintType {
    }

    /**
     * Hint type indicating an error when unparceling.
     */
    public static final int HINT_TYPE_ERROR = -1;

    /**
     * Hint type for {@link BundleHint}.
     */
    public static final int HINT_TYPE_BUNDLE = 1;

    /**
     * Object returned when there is an unparceling error.
     *
     * @hide
     */
    private static final @NonNull ContextHint ERROR_HINT = new ContextHint() {
        @Override
        public int getHintType() {
            return HINT_TYPE_ERROR;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            return new Bundle();
        }
    };

    // Bundle keys for data stored in the base ContextHint.
    private static final String KEY_HINT_TYPE = "key_hint_type";
    private static final String KEY_HINT_ID = "key_hint_id";
    private static final String KEY_ATTRIBUTION_HINTS = "key_attribution_hints";
    private static final String KEY_RENDER_TOKEN = "key_render_token";

    /**
     * Bundle key used to store the data from the hint implementation, retrieved through
     * {@link #toBundleImpl()}.
     */
    static final String KEY_HINT_DATA = "key_hint_data";

    /**
     * Unique identifier for this hint.
     */
    private final UUID mId;

    /**
     * List of hints this hint was derived from.
     */
    private List<ContextHint> mAttributionHints;

    /**
     * Optional token indicating that insights generated from this hint should go straight to a
     * specific renderer.
     */
    private @Nullable RenderToken mRenderToken;

    /**
     * Internal constructor only for use by {@link #createHintFromBundle(Bundle)}. This should be
     * called by subclasses in their private constructors used for
     * {@link #createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    ContextHint(@NonNull Bundle bundle) {
        mId = UUID.fromString(bundle.getString(KEY_HINT_ID));
        mAttributionHints = Arrays.stream(
                bundle.getParcelableArray(KEY_ATTRIBUTION_HINTS, Bundle.class)).map(
                ContextHint::createHintFromBundle).toList();
        mRenderToken = bundle.getParcelable(KEY_RENDER_TOKEN, RenderToken.class);
    }

    /**
     * Internal constructor for generating a new hint. This should be called by subclasses in their
     * public constructors.
     *
     * @hide
     */
    ContextHint() {
        mId = UUID.randomUUID();
        mRenderToken = null;
        mAttributionHints = new ArrayList<>();
    }

    /**
     * Returns the {@link HintType} of this hint.
     */
    @HintType
    public abstract int getHintType();

    /**
     * Returns the unique ID of this hint.
     */
    public final @NonNull UUID getHintId() {
        return mId;
    }

    /**
     * Returns the list of hints that were used to derive this hint.
     */
    public final @NonNull List<ContextHint> getAttributionHints() {
        return mAttributionHints;
    }

    /**
     * Returns the {@link RenderToken} associated with this hint, if any. Insights generated from
     * this hint should only be sent to a specific renderer indicated by the token.
     */
    public final @Nullable RenderToken getRenderToken() {
        return mRenderToken;
    }

    /**
     * Sets the {@link RenderToken} associated with this hint.
     *
     * @hide
     */
    @TestApi
    public final void setRenderToken(@Nullable RenderToken token) {
        mRenderToken = token;
    }

    /**
     * Sets the set of hints that were used to derive this hint.
     *
     * @hide
     */
    @TestApi
    public final void setAttributionHints(@NonNull List<ContextHint> attributionHints) {
        mAttributionHints = attributionHints;
    }

    @NonNull
    abstract Bundle toBundleImpl();

    /**
     * Return the {@link Bundle} representation of this hint's data for writing to a
     * {@link ContextHintWrapper}.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public Bundle toBundle() {
        final Bundle b = new Bundle();
        b.putInt(KEY_HINT_TYPE, getHintType());
        b.putString(KEY_HINT_ID, mId.toString());
        b.putParcelableArray(KEY_ATTRIBUTION_HINTS,
                mAttributionHints.stream().map(ContextHint::toBundle).toArray(Bundle[]::new));
        b.putParcelable(KEY_RENDER_TOKEN, mRenderToken);
        b.putBundle(KEY_HINT_DATA, toBundleImpl());
        return b;
    }

    /**
     * Unbundles a hint into the correct subclass of hint based on the hint type.
     * @hide
     */
    @TestApi
    @NonNull
    public static ContextHint createHintFromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return ERROR_HINT;
        }
        try {
            return switch (bundle.getInt(KEY_HINT_TYPE, HINT_TYPE_ERROR)) {
                case HINT_TYPE_BUNDLE -> new BundleHint(bundle);
                default -> ERROR_HINT;
            };
        } catch (Exception e) {
            Log.e(TAG, "Error creating hint", e);
            return ERROR_HINT;
        }
    }
}
