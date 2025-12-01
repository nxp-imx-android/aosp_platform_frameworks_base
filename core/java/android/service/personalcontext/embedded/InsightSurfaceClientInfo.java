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
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.UUID;

/**
 * Contains the properties of an {@link InsightSurfaceClient} that will be passed through the
 * personal context engine to the visualizer that will be providing a surface to embed in the
 * client. This class will be instantiated by {@link InsightSurfaceClient}.
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightSurfaceClientInfo implements Parcelable {
    private static final String TAG = "InsightSrfcClientInfo";

    private final UUID mId;
    private final int mDisplayId;
    private final int mMeasureSpecWidth;
    private final int mMeasureSpecHeight;
    private final Color mBackgroundColor;
    private final int mNestedScrollAxes;
    private final boolean mNestedScrollAxisLocked;
    private final Configuration mConfiguration;
    private final IEmbeddedInsightSurfaceCallback mCallback;

    /**
     * Create a new insight surface client info object.
     *
     * @param displayId The client app's {@link android.view.Display#getDisplayId}
     * @param measureSpecWidth the width MeasureSpec of the client surface
     * @param measureSpecHeight the height MeasureSpec of the client surface
     * @param configuration resource configuration from the client's local context
     * @param callback callback used to pass surfaces and insights back to the client
     *
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo(
            int displayId,
            int measureSpecWidth,
            int measureSpecHeight,
            @NonNull Color backgroundColor,
            int nestedScrollAxes,
            boolean nestedScrollAxisLocked,
            @NonNull Configuration configuration,
            @NonNull IEmbeddedInsightSurfaceCallback callback) {
        mId = UUID.randomUUID();
        mDisplayId = displayId;
        mMeasureSpecWidth = measureSpecWidth;
        mMeasureSpecHeight = measureSpecHeight;
        mBackgroundColor = backgroundColor;
        mNestedScrollAxes = nestedScrollAxes;
        mNestedScrollAxisLocked = nestedScrollAxisLocked;
        mConfiguration = configuration;
        mCallback = callback;
    }

    private InsightSurfaceClientInfo(Parcel in) {
        mId = UUID.fromString(in.readString());
        mDisplayId = in.readInt();
        mMeasureSpecWidth = in.readInt();
        mMeasureSpecHeight = in.readInt();
        mBackgroundColor = Color.valueOf(in.readInt());
        mNestedScrollAxes = in.readInt();
        mNestedScrollAxisLocked = in.readBoolean();
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
    public int getMeasureSpecWidth() {
        return mMeasureSpecWidth;
    }

    /**
     * Get the client's height MeasureSpec.
     *
     * @return the client's height {@link android.view.View.MeasureSpec}
     */
    public int getMeasureSpecHeight() {
        return mMeasureSpecHeight;
    }

    /**
     * Get the background color of the client over which the embedded surface will be rendered.
     *
     * @return the client's background color
     */
    @NonNull
    public Color getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * Get the bitfield indicating the nested scroll axes supported by the client. This ensures that
     * an embedded surface will only send these nested scroll events back to the client. Possible
     * values are {@link android.view.View#SCROLL_AXIS_HORIZONTAL},
     * {@link android.view.View#SCROLL_AXIS_VERTICAL},
     * or {@link android.view.View#SCROLL_AXIS_NONE}.
     */
    public int getNestedScrollAxes() {
        return mNestedScrollAxes;
    }

    /**
     * Return whether an embedded surface should report a specific axis when a nested scroll gesture
     * is detected, and whether that axis should be locked such that subsequent nested scroll events
     * are only reported for that axis. A value of {@code true} is typical for Android UIs where
     * scroll axes are locked during a gesture, while a value of {@code false} can be used to give
     * the illusion of a 2D canvas. Only applicable when nested scroll axes is set to
     * {@link android.view.View#SCROLL_AXIS_HORIZONTAL} or
     * {@link android.view.View#SCROLL_AXIS_VERTICAL}.
     */
    public boolean getNestedScrollAxisLocked() {
        return mNestedScrollAxisLocked;
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
     * Get the client's {@link IEmbeddedInsightSurfaceCallback}.
     *
     * @return the client's {@link IEmbeddedInsightSurfaceCallback}
     *
     * @hide
     */
    @NonNull
    public IEmbeddedInsightSurfaceCallback getCallback() {
        return mCallback;
    }

    /**
     * The given {@link SurfaceControlViewHost.SurfacePackage} has been created.
     *
     * @param surfacePackage the created {@link SurfaceControlViewHost.SurfacePackage}
     */
    public void onSurfaceCreated(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            mCallback.onSurfaceCreated(surfacePackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error creating SurfacePackage", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * The given {@link SurfaceControlViewHost.SurfacePackage} has been released.
     *
     * @param surfacePackage the released {@link SurfaceControlViewHost.SurfacePackage}
     */
    public void onSurfaceReleased(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            mCallback.onSurfaceReleased(surfacePackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error releasing SurfacePackage", e);
            throw e.rethrowFromSystemServer();
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
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId.toString());
        dest.writeInt(mDisplayId);
        dest.writeInt(mMeasureSpecWidth);
        dest.writeInt(mMeasureSpecHeight);
        dest.writeInt(mBackgroundColor.toArgb());
        dest.writeInt(mNestedScrollAxes);
        dest.writeBoolean(mNestedScrollAxisLocked);
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
