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

package android.media;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Captures information about the change in media routing for logging purposes.
 *
 * @hide
 */
public final class RoutingChangeInfo implements Parcelable {

    // Indicates the start point of a media session.
    private final @EntryPoint int mEntryPoint;

    // Indicates that the route was a suggested route.
    private final boolean mIsSuggested;

    /**
     * Unspecified default value.
     *
     * @hide
     */
    public static final int ENTRY_POINT_UNSPECIFIED = 0;

    /**
     * Indicates that a routing session started as the result of selecting a route from the output
     * switcher.
     *
     * @hide
     */
    public static final int ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER = 1;

    /**
     * Indicates that a routing session started as the result of selecting the device suggestion
     * pill in the system media controls.
     *
     * @hide
     */
    public static final int ENTRY_POINT_SYSTEM_MEDIA_CONTROLS = 2;

    /**
     * Indicates that a routing session was started from a local media router instance where the
     * entry point was not specified.
     *
     * <p>This entry point is marked when {@link MediaRouter2#transferTo(MediaRoute2Info)} is called
     * on a local media router instance.
     *
     * @hide
     */
    public static final int ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED = 3;

    /**
     * Indicates that a routing session was started from a proxy media router instance where the
     * entry point was not specified.
     *
     * <p>This entry point is marked when {@link MediaRouter2#transferTo(MediaRoute2Info)} is called
     * on a proxy media router instance.
     *
     * @hide
     */
    public static final int ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED = 4;

    /**
     * Indicates that a routing session started as the result of selecting a route from the output
     * switcher in TV.
     *
     * @hide
     */
    public static final int ENTRY_POINT_TV_OUTPUT_SWITCHER = 5;

    /** @hide */
    @IntDef(
            prefix = "ENTRY_POINT",
            value = {
                ENTRY_POINT_UNSPECIFIED,
                ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                ENTRY_POINT_SYSTEM_MEDIA_CONTROLS,
                ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED,
                ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED,
                ENTRY_POINT_TV_OUTPUT_SWITCHER
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EntryPoint {}

    @NonNull
    public static final Creator<RoutingChangeInfo> CREATOR =
            new Creator<>() {
                @Override
                public RoutingChangeInfo createFromParcel(Parcel in) {
                    return new RoutingChangeInfo(in);
                }

                @Override
                public RoutingChangeInfo[] newArray(int size) {
                    return new RoutingChangeInfo[size];
                }
            };

    public RoutingChangeInfo(@EntryPoint int entryPoint, boolean isSuggested) {
        mEntryPoint = entryPoint;
        mIsSuggested = isSuggested;
    }

    private RoutingChangeInfo(Parcel in) {
        mEntryPoint = in.readInt();
        mIsSuggested = in.readBoolean();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@androidx.annotation.NonNull Parcel dest, int flags) {
        dest.writeInt(mEntryPoint);
        dest.writeBoolean(mIsSuggested);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof RoutingChangeInfo other)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return other.getEntryPoint() == this.getEntryPoint()
                && other.isSuggested() == this.mIsSuggested;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEntryPoint, mIsSuggested);
    }

    public @EntryPoint int getEntryPoint() {
        return mEntryPoint;
    }

    public boolean isSuggested() {
        return mIsSuggested;
    }
}
