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

package android.service.personalcontext.embedded;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.window.InputTransferToken;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.UUID;

/**
 * Contains information about an {@link InsightSurfaceClient}, which is an object to be instantiated
 * by apps that want to receive embedded surfaces from the personal context engine.
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightSurfaceClientInfo implements Parcelable {
    private static final String TAG = "InsightSrfcClientInfo";

    private final UUID mId;
    private final InputTransferToken mInputTransferToken;
    private final int mDisplayId;
    private final int mWidthMeasureSpec;
    private final int mHeightMeasureSpec;
    private final Configuration mConfiguration;
    private final IEmbeddedInsightSurfaceCallback mCallback;

    /**
     * Create a new insight surface client info object.
     *
     * @param inputTransferToken an {@link InputTransferToken} for the client surface
     * @param displayId The client app's {@link android.view.Display#getDisplayId}
     * @param widthMeasureSpec the width MeasureSpec of the client surface
     * @param heightMeasureSpec the height MeasureSpec of the client surface
     * @param configuration resource configuration from the client's local context
     * @param callback callback used to pass surfaces and insights back to the client
     *
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo(
            @Nullable InputTransferToken inputTransferToken,
            int displayId,
            int widthMeasureSpec,
            int heightMeasureSpec,
            @NonNull Configuration configuration,
            @NonNull IEmbeddedInsightSurfaceCallback callback) {
        mId = UUID.randomUUID();
        mInputTransferToken = inputTransferToken;
        mDisplayId = displayId;
        mWidthMeasureSpec = widthMeasureSpec;
        mHeightMeasureSpec = heightMeasureSpec;
        mConfiguration = configuration;
        mCallback = callback;
    }

    private InsightSurfaceClientInfo(Parcel in) {
        mId = UUID.fromString(in.readString());
        mInputTransferToken = in.readParcelable(
                InputTransferToken.class.getClassLoader(), InputTransferToken.class);
        mDisplayId = in.readInt();
        mWidthMeasureSpec = in.readInt();
        mHeightMeasureSpec = in.readInt();
        mConfiguration =
                in.readParcelable(Configuration.class.getClassLoader(), Configuration.class);
        mCallback = IEmbeddedInsightSurfaceCallback.Stub.asInterface(in.readStrongBinder());
    }

    /**
     * Get the client's unique id.
     *
     * @return the client's {@link UUID}
     */
    @NonNull
    public UUID getId() {
        return mId;
    }

    /**
     * Get the client's {@link InputTransferToken}.
     *
     * @return the client's {@link InputTransferToken}
     */
    @NonNull
    public InputTransferToken getInputTransferToken() {
        return mInputTransferToken;
    }

    /**
     * Get the client's display id.
     *
     * @return the client's {@link android.view.Display} id
     */
    @IntRange(from = 0)
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Get the client's width MeasureSpec.
     *
     * @return the client's width {@link android.view.View.MeasureSpec}
     */
    public int getWidthMeasureSpec() {
        return mWidthMeasureSpec;
    }

    /**
     * Get the client's height MeasureSpec.
     *
     * @return the client's height {@link android.view.View.MeasureSpec}
     */
    public int getHeightMeasureSpec() {
        return mHeightMeasureSpec;
    }

    /**
     * Get the client's {@link Configuration}.
     *
     * @return the client's {@link Configuration}
     */
    @NonNull
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Sends the given {@link SurfaceControlViewHost.SurfacePackage} to the client.
     *
     * @param surfacePackage the created {@link SurfaceControlViewHost.SurfacePackage}
     */
    public void onSurfaceCreated(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            mCallback.onSurfaceCreated(surfacePackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending SurfaceView to client", e);
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends the given {@link ContextInsight} to the client. This is used to egress data from the
     * embedded surface to the client (e.g. the user has tapped a button in the embedded surface,
     * resulting in data being sent to the embedding client based on the state of the embedded
     * surface).
     *
     * @param insight the {@link ContextInsight} to send to the client
     */
    public void onReceiveInsight(@NonNull ContextInsight insight) {
        try {
            mCallback.onReceiveInsight(new ContextInsightWrapper(insight));
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending insight to client", e);
            e.rethrowFromSystemServer();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId.toString());
        dest.writeParcelable(mInputTransferToken, flags);
        dest.writeInt(mDisplayId);
        dest.writeInt(mWidthMeasureSpec);
        dest.writeInt(mHeightMeasureSpec);
        dest.writeParcelable(mConfiguration, flags);
        dest.writeStrongInterface(mCallback);
    }

    @NonNull
    public static final Creator<InsightSurfaceClientInfo> CREATOR =
            new Creator<>() {
                @Override
                public InsightSurfaceClientInfo createFromParcel(@NonNull Parcel source) {
                    return new InsightSurfaceClientInfo(source);
                }

                @Override
                public InsightSurfaceClientInfo[] newArray(int size) {
                    return new InsightSurfaceClientInfo[size];
                }
            };
}
