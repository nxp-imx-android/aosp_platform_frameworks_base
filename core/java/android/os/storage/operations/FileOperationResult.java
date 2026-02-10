/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.storage.operations;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.storage.FileManager;
import android.os.storage.operations.sources.OperationSource;
import android.os.storage.operations.targets.OperationTarget;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents the result of a file operation. */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public final class FileOperationResult implements Parcelable {

    /** Unknown status. */
    public static final int STATUS_UNKNOWN = 0;

    /** The operation is queued and waiting to start. */
    public static final int STATUS_QUEUED = 1;

    /** The operation is currently running. */
    public static final int STATUS_IN_PROGRESS = 2;

    /** The operation finished successfully. */
    public static final int STATUS_FINISHED = 3;

    /** The operation failed. */
    public static final int STATUS_FAILED = 4;

    /** @hide */
    @IntDef(
            prefix = {"STATUS_"},
            value = {
                STATUS_UNKNOWN,
                STATUS_QUEUED,
                STATUS_IN_PROGRESS,
                STATUS_FINISHED,
                STATUS_FAILED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /** No error or unknown error. */
    public static final int ERROR_UNKNOWN = 0;

    /** The system is too busy to handle the request. */
    public static final int ERROR_BUSY = 1;

    /** The system cannot fulfill the request as configured. */
    public static final int ERROR_INVALID_REQUEST = 2;

    /** The source of the operation is unsupported or invalid. */
    public static final int ERROR_UNSUPPORTED_SOURCE = 3;

    /** The target of the operation is unsupported or invalid. */
    public static final int ERROR_UNSUPPORTED_TARGET = 4;

    /** The operation failed due to missing permissions. */
    public static final int ERROR_PERMISSION_DENIED = 5;

    /** The operation failed because the disk is full. */
    public static final int ERROR_DISK_FULL = 6;

    /** @hide */
    @IntDef(
            prefix = {"ERROR_"},
            value = {
                ERROR_UNKNOWN,
                ERROR_BUSY,
                ERROR_INVALID_REQUEST,
                ERROR_UNSUPPORTED_SOURCE,
                ERROR_UNSUPPORTED_TARGET,
                ERROR_PERMISSION_DENIED,
                ERROR_DISK_FULL,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    private final String mRequestId;
    @NonNull private final OperationSource mSource;
    @Nullable private final OperationTarget mTarget;
    private final int mStatus;
    private final int mErrorCode;
    @Nullable private final String mErrorMessage;
    @NonNull private final List<String> mFailedPaths;

    private FileOperationResult(Builder builder) {
        mRequestId = Objects.requireNonNull(builder.mRequestId);
        mSource = Objects.requireNonNull(builder.mSource);
        mTarget = builder.mTarget;
        mStatus = builder.mStatus;
        mErrorCode = builder.mErrorCode;
        mErrorMessage = builder.mErrorMessage;
        mFailedPaths = Collections.unmodifiableList(new ArrayList<>(builder.mFailedPaths));
    }

    private FileOperationResult(Parcel in) {
        mRequestId = Objects.requireNonNull(in.readString8());
        mSource =
                Objects.requireNonNull(
                        in.readParcelable(
                                OperationSource.class.getClassLoader(), OperationSource.class));
        mTarget = in.readParcelable(OperationTarget.class.getClassLoader(), OperationTarget.class);
        mStatus = in.readInt();
        mErrorCode = in.readInt();
        mErrorMessage = in.readString8();
        List<String> failures = new ArrayList<>();
        in.readStringList(failures);
        mFailedPaths = Collections.unmodifiableList(failures);
    }

    /** Returns the unique ID of the request. */
    @NonNull
    public String getRequestId() {
        return mRequestId;
    }

    /**
     * Returns the {@link OperationSource} that was configured in the original {@link
     * FileOperationRequest}.
     *
     * <p>For example, if an operation was created using a {@link AppDataFileSource } that object
     * would be returned here.
     */
    @NonNull
    public OperationSource getSource() {
        return mSource;
    }

    /** Returns the target of the file operation. */
    @Nullable
    public OperationTarget getTarget() {
        return mTarget;
    }

    /** Returns the current status of the operation. */
    @Status
    public int getStatus() {
        return mStatus;
    }

    /** Returns the error code if the operation failed. */
    @ErrorCode
    public int getErrorCode() {
        return mErrorCode;
    }

    /** Returns the error message if the operation failed. */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Returns the list of failures.
     *
     * <p>This list is only populated when the operation reaches a terminal state ({@link
     * #STATUS_FINISHED} or {@link #STATUS_FAILED}).
     *
     * <p>Note: Due to binder transaction limits, this list can truncate the total number of
     * reported failures. If failures occurred, only the first {@link
     * android.os.storage.FileManager#getMaxReportedFailures} are reported.
     */
    @NonNull
    public List<String> getFailedPaths() {
        return mFailedPaths;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @SuppressWarnings("AndroidFrameworkEfficientParcelable")
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mRequestId);
        // We use writeParcelable (which includes the class name) rather than writeTypedObject
        // to correctly handle polymorphic types for OperationSource and OperationTarget.
        dest.writeParcelable(mSource, flags);
        dest.writeParcelable(mTarget, flags);
        dest.writeInt(mStatus);
        dest.writeInt(mErrorCode);
        dest.writeString8(mErrorMessage);
        dest.writeStringList(mFailedPaths);
    }

    @NonNull
    public static final Creator<FileOperationResult> CREATOR =
            new Creator<FileOperationResult>() {
                @Override
                public FileOperationResult createFromParcel(Parcel in) {
                    return new FileOperationResult(in);
                }

                @Override
                public FileOperationResult[] newArray(int size) {
                    return new FileOperationResult[size];
                }
            };

    /** @hide */
    public static final class Builder {
        private final String mRequestId;
        private final OperationSource mSource;
        private final OperationTarget mTarget;
        private int mStatus;
        private int mErrorCode;
        @Nullable private String mErrorMessage;
        private List<String> mFailedPaths = new ArrayList<>();

        /** @hide */
        public Builder(@NonNull String requestId, @NonNull FileOperationRequest request) {
            mRequestId = requestId;
            mSource = request.getSource();
            mTarget = request.getTarget();
            mStatus = STATUS_UNKNOWN;
            mErrorCode = ERROR_UNKNOWN;
        }

        /** @hide */
        @NonNull
        public Builder setStatus(@Status int status) {
            mStatus = status;
            return this;
        }

        /** @hide */
        @NonNull
        public Builder setErrorCode(@ErrorCode int errorCode) {
            mErrorCode = errorCode;
            return this;
        }

        /** @hide */
        @NonNull
        public Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /** @hide */
        @NonNull
        public Builder setFailedPaths(@NonNull List<String> paths) {
            if (paths.size() > FileManager.getMaxReportedFailures()) {
                throw new IllegalStateException(
                        "Due to binder transaction limits, setFailedPaths cannot provide more than "
                                + FileManager.getMaxReportedFailures()
                                + "failures to the client.");
            }
            mFailedPaths = paths;
            return this;
        }

        /** @hide */
        @NonNull
        public FileOperationResult build() {
            return new FileOperationResult(this);
        }
    }
}
