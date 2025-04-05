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

package androidx.window.extensions.bubble;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.window.IMultitaskingController;
import android.window.IMultitaskingDelegate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The interface that apps use to request control over Bubbles - special windowing feature that
 * allows intermittent multi-tasking. The supported operations include abilities to create,
 * expand/collapse, update flyout message or remove the containers.
 */
public class BubbleContainerManager {
    private static final String TAG = BubbleContainerManager.class.getSimpleName();

    private static BubbleContainerManager sInstance;

    @NonNull
    private final IMultitaskingDelegate mClientInterface;

    private BubbleContainerManager(@NonNull IMultitaskingDelegate clientInterface) {
        Objects.requireNonNull(clientInterface);
        mClientInterface = clientInterface;
    }

    /**
     * Obtain an instance of the class to make requests related to Bubbles system multi-tasking
     * feature control.
     * @return a new instance of an interface if it's available, {@code null} otherwise.
     */
    @RequiresPermission(Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)
    @Nullable
    public static BubbleContainerManager getInstance() {
        if (!com.android.window.flags.Flags.enableExperimentalBubblesController()) {
            Log.d(TAG, "ExperimentalBubblesController flag is off, "
                    + "returning null BubbleContainerManager.");
            return null;
        }
        if (sInstance != null) {
            return sInstance;
        }
        try {
            IMultitaskingController controller = ActivityTaskManager.getService()
                    .getWindowOrganizerController().getMultitaskingController();
            IMultitaskingDelegate clientInterface = controller.getClientInterface();
            sInstance = new BubbleContainerManager(clientInterface);
            return sInstance;
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception getting an instance of BubbleContainerManager",
                    new RuntimeException(e));
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Exception getting an instance of BubbleContainerManager", e);
            return null;
        }
    }

    /**
     * Request to create a new Bubble.
     * @param token a token uniquely identifying a bubble.
     * @param intent an Intent used to launch an Activity into a Bubble.
     * @param collapsed initial Bubble state.
     */
    public void createBubble(IBinder token, Intent intent, boolean collapsed) {
        try {
            mClientInterface.createBubble(token, intent, collapsed);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception creating a Bubble", new RuntimeException(e));
        }
    }

    // TODO(b/407149510): Handle the case when the user removes the Bubble after it was created.
    /**
     * Update the state of an existing Bubble to either collapse or expand it.
     * @param token a token uniquely identifying a bubble.
     * @param collapsed new Bubble state.
     */
    public void updateBubbleState(IBinder token, boolean collapsed) {
        try {
            mClientInterface.updateBubbleState(token, collapsed);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception updating Bubble state", new RuntimeException(e));
        }
    }

    /**
     * Update the flyout message for an existing Bubble.
     * @param token a token uniquely identifying a bubble.
     * @param message new Bubble message.
     */
    public void updateBubbleMessage(IBinder token, String message) {
        try {
            mClientInterface.updateBubbleMessage(token, message);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception updating Bubble message", new RuntimeException(e));
        }
    }

    /**
     * Remove an existing Bubble.
     * @param token a token uniquely identifying a bubble.
     */
    public void removeBubble(IBinder token) {
        try {
            mClientInterface.removeBubble(token);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception removing Bubble", new RuntimeException(e));
        }
    }
}
