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
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.UUID;

/**
 * A piece of input data into the personal context engine.
 *
 * Hints may describe some current state of the device or represent an event that may be of use for
 * kicking off an understanding flow.
 *
 * Users of this class can use instanceof to determine the type of the hint.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextHint {
    private static final String TAG = "ContextHint";

    /**
     * Enumeration of hint types.
     *
     * @hide
     */
    @IntDef(prefix = {"HINT_TYPE_"}, value = {HINT_TYPE_ERROR, HINT_TYPE_BUNDLE,
            HINT_TYPE_NOTIFICATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HintType {
    }

    /**
     * Hint type indicating an error when unparceling.
     */
    static final int HINT_TYPE_ERROR = -1;

    /**
     * Hint type for {@link BundleHint}.
     *
     * @hide
     */
    @VisibleForTesting
    public static final int HINT_TYPE_BUNDLE = 1;

    /**
     * Hint type for {@link NotificationHint}.
     */
    static final int HINT_TYPE_NOTIFICATION = 2;

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
     * Internal constructor only for use by {@link #createHintFromBundle(Bundle)}. This should be
     * called by subclasses in their private constructors used for
     * {@link #createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    ContextHint(@NonNull Bundle bundle) {
        mId = UUID.fromString(bundle.getString(KEY_HINT_ID));
    }

    /**
     * Internal constructor for generating a new hint. This should be called by subclasses in their
     * public constructors.
     *
     * @hide
     */
    ContextHint() {
        mId = UUID.randomUUID();
    }

    /**
     * Returns the {@link HintType} of this hint.
     *
     * @hide
     */
    @HintType
    public abstract int getHintType();

    /**
     * Returns the unique ID of this hint.
     */
    public final @NonNull UUID getHintId() {
        return mId;
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
        b.putBundle(KEY_HINT_DATA, toBundleImpl());
        return b;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + mId + "}";
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
                case HINT_TYPE_NOTIFICATION -> new NotificationHint(bundle);
                default -> ERROR_HINT;
            };
        } catch (Exception e) {
            Log.e(TAG, "Error creating hint", e);
            return ERROR_HINT;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextHint that)) return false;
        return Objects.deepEquals(mId, that.mId)
                && Objects.deepEquals(getHintType(), that.getHintType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, getHintType());
    }
}
