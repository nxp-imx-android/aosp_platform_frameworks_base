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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.RemoteAction;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Contains the details of the action to be performed, either as an {@link Intent} or a {@link
 * RemoteAction}.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightActionDetails implements Parcelable {

    /** @hide */
    @IntDef(
            prefix = {"ACTION_TYPE_"},
            value = {ACTION_TYPE_INTENT, ACTION_TYPE_REMOTE_ACTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionType {}

    /** The action details contain an {@link Intent}. */
    public static final int ACTION_TYPE_INTENT = 1 << 0;

    /** The action details contain a {@link RemoteAction}. */
    public static final int ACTION_TYPE_REMOTE_ACTION = 1 << 1;

    private final int mActionTypes;
    private final Intent mActionIntent;
    private final RemoteAction mRemoteAction;

    private InsightActionDetails(
            @Nullable Intent actionIntent, @Nullable RemoteAction remoteAction) {
        mActionIntent = actionIntent;
        mRemoteAction = remoteAction;

        int actionTypes = 0;
        if (actionIntent != null) {
            actionTypes |= ACTION_TYPE_INTENT;
        }
        if (remoteAction != null) {
            actionTypes |= ACTION_TYPE_REMOTE_ACTION;
        }
        mActionTypes = actionTypes;
    }

    private InsightActionDetails(@NonNull Parcel in) {
        mActionTypes = in.readInt();
        mActionIntent = in.readTypedObject(Intent.CREATOR);
        mRemoteAction = in.readTypedObject(RemoteAction.CREATOR);
    }

    /** Returns the valid action types contained in this action details. */
    public int getActionTypes() {
        return mActionTypes;
    }

    /**
     * Returns whether the action details has the given action type.
    */
    public boolean hasActionType(@ActionType int actionType) {
        return (mActionTypes & actionType) == actionType;
    }

    /**
     * Returns the intent to be invoked when the insight triggers.
     */
    @Nullable
    public Intent createActionIntent() {
        return mActionIntent != null ? new Intent(mActionIntent) : null;
    }

    /**
     * Returns the remote action to be invoked when the insight triggers.
     */
    @Nullable
    public RemoteAction getRemoteAction() {
        return mRemoteAction;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final InsightActionDetails that = (InsightActionDetails) o;
        final boolean intentsEqual = (mActionIntent == null && that.mActionIntent == null)
                || (mActionIntent != null && mActionIntent.filterEquals(that.mActionIntent));

        return intentsEqual && Objects.equals(mRemoteAction, that.mRemoteAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mActionIntent, mRemoteAction);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, @WriteFlags int flags) {
        dest.writeInt(mActionTypes);
        dest.writeTypedObject(mActionIntent, flags);
        dest.writeTypedObject(mRemoteAction, flags);
    }

    @Override
    public String toString() {
        return "InsightActionDetails{"
                + "mActionIntent="
                + mActionIntent
                + ", mRemoteAction="
                + mRemoteAction
                + '}';
    }

    @NonNull
    public static final Creator<InsightActionDetails> CREATOR =
            new Creator<>() {
                @Override
                public InsightActionDetails createFromParcel(@NonNull Parcel in) {
                    return new InsightActionDetails(in);
                }

                @Override
                public InsightActionDetails[] newArray(int size) {
                    return new InsightActionDetails[size];
                }
            };

    /** Builder for {@link InsightActionDetails}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private Intent mActionIntent;
        private RemoteAction mRemoteAction;

        /**
         * Creates a new builder for the insight action details.
         */
        public Builder() {
        }

        /**
         * Sets the {@link Intent} for the insight action details.
         *
         * @param intent the {@link Intent} to set
         */
        @NonNull
        @SuppressWarnings("MissingGetterMatchingBuilder") // getter is createActionIntent
        public Builder setIntent(@NonNull Intent intent) {
            Objects.requireNonNull(intent, "intent is null");
            mActionIntent = intent;
            return this;
        }

        /**
         * Sets the {@link RemoteAction} for the insight action details.
         *
         * @param remoteAction the {@link RemoteAction} to set
         */
        @NonNull
        public Builder setRemoteAction(@NonNull RemoteAction remoteAction) {
            Objects.requireNonNull(remoteAction, "remoteAction is null");
            mRemoteAction = remoteAction;
            return this;
        }

        /**
         * Builds the {@link InsightActionDetails}.
         *
         * @return the {@link InsightActionDetails}.
         */
        @NonNull
        public InsightActionDetails build() {
            return new InsightActionDetails(mActionIntent, mRemoteAction);
        }
    }
}
