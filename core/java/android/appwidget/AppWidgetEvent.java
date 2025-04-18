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

package android.appwidget;

import static android.appwidget.flags.Flags.FLAG_ENGAGEMENT_METRICS;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.usage.UsageStatsManager;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.ArraySet;

import java.util.Arrays;

/**
 * An immutable class that describes the event data for an app widget interaction event.
 *
 * @hide
 */
@FlaggedApi(FLAG_ENGAGEMENT_METRICS)
public class AppWidgetEvent implements Parcelable {
    /**
     * Max number of clicked and scrolled IDs stored per event.
     */
    public static final int MAX_NUM_ITEMS = 10;

    private final int mAppWidgetId;
    private final long mDurationMs;
    @Nullable
    private final Rect mPosition;
    @Nullable
    private final int[] mClickedIds;
    @Nullable
    private final int[] mScrolledIds;

    /**
     * The app widget ID of the widget that generated this event.
     */
    public int getAppWidgetId() {
        return mAppWidgetId;
    }

    /**
     * This contains a long that represents the duration of time in milliseconds during which the
     * widget was visible.
     */
    public long getDurationMs() {
        return mDurationMs;
    }

    /**
     * This rect with describes the global coordinates of the widget at the end of the interaction
     * event.
     */
    @Nullable
    public Rect getPosition() {
        return mPosition;
    }

    /**
     * This describes which views have been clicked during a single impression of the widget.
     */
    @Nullable
    public int[] getClickedIds() {
        return mClickedIds;
    }

    /**
     * This describes which views have been scrolled during a single impression of the widget.
     */
    @Nullable
    public int[] getScrolledIds() {
        return mScrolledIds;
    }

    private AppWidgetEvent(int appWidgetId, long durationMs,
            @Nullable Rect position, @Nullable int[] clickedIds,
            @Nullable int[] scrolledIds) {
        mAppWidgetId = appWidgetId;
        mDurationMs = durationMs;
        mPosition = position;
        mClickedIds = clickedIds;
        mScrolledIds = scrolledIds;
    }

    /**
     * Unflatten the AppWidgetEvent from a parcel.
     */
    private AppWidgetEvent(Parcel in) {
        mAppWidgetId = in.readInt();
        mDurationMs = in.readLong();
        mPosition = in.readTypedObject(Rect.CREATOR);
        mClickedIds = in.createIntArray();
        mScrolledIds = in.createIntArray();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mAppWidgetId);
        out.writeLong(mDurationMs);
        out.writeTypedObject(mPosition, flags);
        out.writeIntArray(mClickedIds);
        out.writeIntArray(mScrolledIds);
    }

    /**
     * Parcelable.Creator that instantiates AppWidgetEvent objects
     */
    public static final @android.annotation.NonNull Parcelable.Creator<AppWidgetEvent> CREATOR =
            new Parcelable.Creator<>() {
                public AppWidgetEvent createFromParcel(Parcel parcel) {
                    return new AppWidgetEvent(parcel);
                }

                public AppWidgetEvent[] newArray(int size) {
                    return new AppWidgetEvent[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create a PersistableBundle that represents this event.
     */
    @NonNull
    public PersistableBundle toBundle() {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(UsageStatsManager.EXTRA_EVENT_ACTION,
                AppWidgetManager.EVENT_TYPE_WIDGET_INTERACTION);
        extras.putString(UsageStatsManager.EXTRA_EVENT_CATEGORY,
                AppWidgetManager.EVENT_CATEGORY_APPWIDGET);
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        extras.putLong(AppWidgetManager.EXTRA_EVENT_DURATION_MS, mDurationMs);
        if (mPosition != null) {
            extras.putIntArray(AppWidgetManager.EXTRA_EVENT_POSITION_RECT,
                new int[]{mPosition.left, mPosition.top, mPosition.right, mPosition.bottom});
        }
        if (mClickedIds != null && mClickedIds.length > 0) {
            extras.putIntArray(AppWidgetManager.EXTRA_EVENT_CLICKED_VIEWS, mClickedIds);
        }
        if (mScrolledIds != null && mScrolledIds.length > 0) {
            extras.putIntArray(AppWidgetManager.EXTRA_EVENT_SCROLLED_VIEWS, mScrolledIds);
        }
        return extras;
    }

    @Override
    public String toString() {
        return TextUtils.formatSimple("AppWidgetEvent(appWidgetId=%d, durationMs=%d, position=%s,"
                + " clickedIds=%s, scrolledIds=%s)", mAppWidgetId, mDurationMs, mPosition,
            Arrays.toString(mClickedIds), Arrays.toString(mScrolledIds));
    }

    /**
     * Builder class to construct AppWidgetEvent objects.
     *
     * @hide
     */
    public static class Builder {
        @NonNull
        private final ArraySet<Integer> mClickedIds = new ArraySet<>(MAX_NUM_ITEMS);
        @NonNull
        private final ArraySet<Integer> mScrolledIds = new ArraySet<>(MAX_NUM_ITEMS);
        private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        private long mDurationMs = 0;
        @Nullable
        private Rect mPosition = null;

        public Builder() {
        }

        public Builder setAppWidgetId(int appWidgetId) {
            mAppWidgetId = appWidgetId;
            return this;
        }

        public Builder addDurationMs(long durationMs) {
            mDurationMs += durationMs;
            return this;
        }

        public Builder setPosition(@Nullable Rect position) {
            mPosition = position;
            return this;
        }

        public Builder addClickedId(int id) {
            if (mClickedIds.size() < MAX_NUM_ITEMS) {
                mClickedIds.add(id);
            }
            return this;
        }

        public Builder addScrolledId(int id) {
            if (mScrolledIds.size() < MAX_NUM_ITEMS) {
                mScrolledIds.add(id);
            }
            return this;
        }

        /**
         * Merge the given event's data into this event's data.
         */
        public void merge(@Nullable AppWidgetEvent event) {
            if (event == null) {
                return;
            }

            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                setAppWidgetId(event.getAppWidgetId());
            } else if (mAppWidgetId != event.getAppWidgetId()) {
                throw new IllegalArgumentException("Trying to merge events with different app "
                    + "widget IDs: " + mAppWidgetId + " != " + event.getAppWidgetId());
            }
            addDurationMs(event.getDurationMs());
            setPosition(event.getPosition());
            addAllUntilMax(mClickedIds, event.getClickedIds());
            addAllUntilMax(mScrolledIds, event.getScrolledIds());
        }

        /**
         * Returns true if the app widget ID has not been set, or if no data has been added to this
         * event yet.
         */
        public boolean isEmpty() {
            return mAppWidgetId <= 0 || mDurationMs == 0L;
        }

        /**
         * Resets the event data fields.
         */
        public void clear() {
            mDurationMs = 0;
            mPosition = null;
            mClickedIds.clear();
            mScrolledIds.clear();
        }

        public AppWidgetEvent build() {
            return new AppWidgetEvent(mAppWidgetId, mDurationMs, mPosition, toIntArray(mClickedIds),
                toIntArray(mScrolledIds));
        }

        private static void addAllUntilMax(@NonNull ArraySet<Integer> set, @Nullable int[] toAdd) {
            if (toAdd == null) {
                return;
            }
            for (int i = 0; i < toAdd.length && set.size() < MAX_NUM_ITEMS; i++) {
                set.add(toAdd[i]);
            }
        }

        @Nullable
        private static int[] toIntArray(@NonNull ArraySet<Integer> set) {
            if (set.isEmpty()) return null;
            int[] array = new int[set.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = set.valueAt(i);
            }
            return array;
        }
    }
}
