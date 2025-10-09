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

package com.android.extensions.computercontrol;

import android.annotation.CallbackExecutor;
import android.annotation.IntRange;
import android.app.Activity;
import android.app.Notification;
import android.companion.virtual.computercontrol.ComputerControlSession.Action;
import android.companion.virtual.computercontrol.ComputerControlSession.SessionBlockReason;
import android.companion.virtual.computercontrol.ComputerControlSession.SessionCloseReason;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.Image;
import android.util.Size;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Computer control sessions are used to allow a computer of some sort to control applications on
 * the Android device.
 *
 * <p>Applications can be launched in the computer control session and can then be controlled by
 * injecting input events into the session.</p>
 *
 * <p>When the session is no longer used it should be closed by calling {@link #close()} to clean up
 * resources and reduce the system-wide impact computer control sessions can have.</p>
 */
public final class ComputerControlSession implements AutoCloseable {
    private final android.companion.virtual.computercontrol.ComputerControlSession mSession;

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    ComputerControlSession(
            @NonNull android.companion.virtual.computercontrol.ComputerControlSession session) {
        mSession = session;
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the package does not have a launcher activity.
     * @see Params#getTargetPackageNames()
     */
    public void launchApplication(@NonNull String packageName) {
        mSession.launchApplication(packageName);
    }

    /**
     * Launches an application's launcher activity in the computer control session.
     *
     * @throws IllegalArgumentException if the component is not a launcher activity.
     * @see Params#getTargetPackageNames()
     */
    public void launchApplication(@NonNull ComponentName component) {
        mSession.launchApplication(component);
    }

    /**
     * Hand over full control of the automation session to the user.
     *
     * <p>All of the applications currently automated in the session are moved from the session's
     * display to the user's default display. No further automation is possible on these tasks,
     * although the session remains active and new applications may be launched via
     * {@link #launchApplication(String)}</p>
     */
    public void handOverApplications() {
        mSession.handOverApplications();
    }

    /**
     * Screenshot the current display content.
     *
     * <p>The behavior is similar to {@link android.media.ImageReader#acquireLatestImage}, meaning
     * that any previously acquired images should be released before attempting to acquire new ones.
     * </p>
     *
     * @return A screenshot of the current display content, or {@code null} if no screenshot is
     *   currently available.
     */
    @Nullable
    public Image getScreenshot() {
        return mSession.getScreenshot();
    }

    /**
     * Sends a tap event to the computer control session at the given location.
     */
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        mSession.tap(x, y);
    }

    /**
     * Sends a swipe event to the computer control session for the given coordinates.
     *
     * <p>To avoid misinterpreting the swipe as a fling, the individual touches are throttled, so
     * the entire action will take ~500ms. However, this is done in the background and this method
     * returns immediately. Any ongoing swipe will be canceled if a new swipe is requested.</p>
     */
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int toY) {
        mSession.swipe(fromX, fromY, toX, toY);
    }

    /**
     * Sends a long press event to the computer control session for the given coordinates.
     */
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
        mSession.longPress(x, y);
    }

    /**
     * Inserts provided text into the currently active text field on the display associated with
     * the {@link ComputerControlSession}.
     *
     * <p> This method expects a text field to be in focus with an active {@link InputConnection}.
     * It inserts text at the current cursor position in the text field and moves the cursor to
     * the end of inserted text. </p>
     *
     * @param text to be inserted
     * @param replaceExisting whether the current text in the text field needs to be overwritten
     * @param commit whether the text should be submitted after insertion
     */
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        mSession.insertText(text, replaceExisting, commit);
    }

    /**
     * Perform provided action on the display associated with the {@link ComputerControlSession}.
     */
    public void performAction(@Action int actionCode) {
        mSession.performAction(actionCode);
    }

    /**
     * Returns all windows on the display associated with the {@link ComputerControlSession}.
     */
    @NonNull
    public List<AccessibilityWindowInfo> getAccessibilityWindows() {
        return mSession.getAccessibilityWindows();
    }

    /**
     * Returns an {@link InteractiveMirror} which mirrors this {@link ComputerControlSession}.
     */
    public InteractiveMirror createInteractiveMirror() {
        return mSession.createInteractiveMirror();
    }

    /**
     * Get the size of the session's display.
     */
    @NonNull
    public Size getDisplaySize() {
        return mSession.getDisplaySize();
    }

    /**
     * Sets a {@link StabilityListener} to be notified when the computer control session is
     * potentially stable.
     *
     * @throws IllegalStateException if a listener was previously set.
     */
    public void setStabilityListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull StabilityListener listener) {
        mSession.setStabilityListener(executor, listener::onSessionStable);
    }

    /**
     * Clears any {@link StabilityListener} that was previously set using
     * {@link #setStabilityListener(Executor, StabilityListener)}.
     */
    public void clearStabilityListener() {
        mSession.clearStabilityListener();
    }

    /**
     * Sets a {@link LifecycleCallback} to be notified about the computer control session lifecycle
     * changes.
     *
     * @throws IllegalStateException if a callback was previously set.
     */
    public void setLifecycleCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull LifecycleCallback callback) {
        Objects.requireNonNull(callback);
        mSession.setLifecycleCallback(executor,
                new android.companion.virtual.computercontrol.ComputerControlSession
                        .LifecycleCallback() {
                    @Override
                    public void onActive() {
                        callback.onActive();
                    }

                    @Override
                    public void onBlocked(@SessionBlockReason int initialBlockReason) {
                        callback.onBlocked(initialBlockReason);
                    }

                    @Override
                    public void onClosed(@SessionCloseReason int reason) {
                        callback.onClosed(reason);
                    }
                });
    }

    /**
     * Clears any {@link LifecycleCallback} that was previously set using
     * {@link #setLifecycleCallback(Executor, LifecycleCallback)}.
     *
     * @throws IllegalStateException if a callback was not previously set.
     */
    public void clearLifecycleCallback() {
        mSession.clearLifecycleCallback();
    }

    /**
     * Attaches notification information to the session, to make the notification non-dismissible.
     *
     * <p>This must be called before posting the notification.</p>
     *
     * <p>The caller must still call {@link Notification.Builder#setOngoing(boolean)}
     * with {@code true}, to make the notification non-dismissible.</p>
     *
     * @param notificationId id of the notification, as per
     * {@link android.app.NotificationManager#notify(String, int, Notification)}
     * @param notificationTag tag of the notification, as per
     * {@link android.app.NotificationManager#notify(String, int, Notification)}
     *
     * @throws IllegalStateException if a notification was already attached.
     */
    public void attachNotificationInfo(int notificationId, @Nullable String notificationTag) {
        mSession.attachNotificationInfo(notificationId, notificationTag);
    }

    /**
     * Closes the ComputerControl session and release resources. The session can no longer be used
     * after calling this method.
     */
    @Override
    public void close() {
        mSession.close();
    }

    public static class Params {
        @NonNull private final Context mContext;
        @NonNull private final String mName;
        @NonNull private final List<String> mTargetPackageNames;

        private Params(@NonNull Context context, @NonNull String name,
                @NonNull List<String> targetPackageNames) {
            mContext = context;
            mName = name;
            mTargetPackageNames = targetPackageNames;
        }

        /**
         * Returns the context used to construct the ComputerControlSession.
         */
        @NonNull
        public Context getContext() {
            return mContext;
        }

        /**
         * Returns the name of the computer control session
         *
         * @see Builder#setName(String)
         */
        @NonNull
        public String getName() {
            return mName;
        }

        /**
         * Returns the package names of the applications that may be automated during this session.
         *
         * @see Builder#setTargetPackageNames(List)
         */
        @NonNull
        public List<String> getTargetPackageNames() {
            return mTargetPackageNames;
        }

        /**
         * Builder for {@link ComputerControlSession}.
         */
        public static class Builder {
            @NonNull private final Context mContext;
            private String mName;
            private List<String> mTargetPackageNames = new ArrayList<>();

            /**
             * Create a new Builder.
             */
            public Builder(@NonNull Context context) {
                mContext = context;
            }

            /**
             * Set the name of the session. Only used internally and not shown to users.
             */
            @NonNull
            public Builder setName(@NonNull String name) {
                mName = name;
                return this;
            }

            /**
             * Set the package names of all applications that may be automated during this session.
             *
             * <p>All package names specified in the list must meet the following requirements:
             * <ol>
             *     <li>The package name has a valid launcher Intent.</li>
             *     <li>The package name is not the device permission controller.</li>
             * </ol>
             */
            @NonNull
            public Builder setTargetPackageNames(@NonNull List<String> targetPackageNames) {
                mTargetPackageNames = targetPackageNames;
                return this;
            }

            /**
             * Build a computer control session.
             */
            @NonNull
            public Params build() {
                return new Params(mContext, mName, mTargetPackageNames);
            }
        }
    }

    /** Callback for computer control session events. */
    public interface Callback {

        /**
         * Called when the session request needs to approved by the user.
         *
         * <p>Applications should launch the {@link Activity} "encapsulated" in {@code intentSender}
         * {@link IntentSender} object by calling
         * {@link Activity#startIntentSenderForResult(IntentSender, int, Intent, int, int, int)} or
         * {@link Context#startIntentSender(IntentSender, Intent, int, int, int)}
         *
         * @param intentSender an {@link IntentSender} which applications should use to launch
         *   the UI for the user to allow the creation of the session.
         */
        void onSessionPending(@NonNull IntentSender intentSender);

        /** Called when the session has been successfully created. */
        void onSessionCreated(@NonNull ComputerControlSession session);

        /** Called when the session failed to be created. */
        void onSessionCreationFailed(int errorCode);

        /**
         * Called when the session has been closed.
         *
         * @deprecated use {@link LifecycleCallback} instead
         * @see ComputerControlSession#setLifecycleCallback(Executor, LifecycleCallback)
         */
        @Deprecated
        void onSessionClosed();
    }

    /**
     * Listener to be notified of signals indicating that the computer control session is
     * potentially stable.
     *
     * <p>These signals indicate that the session's display content is currently stable, such as the
     * app under automation being idle and no UI animations being under progress. These are useful
     * for tasks that should only run on a static UI, such as taking screenshots to determine the
     * next step.
     */
    public interface StabilityListener {
        /** Called when the computer control session is considered stable. */
        void onSessionStable();
    }

    /**
     * Callback to be notified about the computer control session lifecycle changes.
     *
     * <p>When the callback is first added, implementers of the callback should not make any
     * assumptions about the starting state of the session. The callback will be notified of the
     * starting state after being added.
     *
     * @see #setLifecycleCallback(Executor, LifecycleCallback)
     */
    public interface LifecycleCallback {

        /**
         * Called when the computer control session enters the active state.
         *
         * <p>When the session is active, the following will apply:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is allowed.
         * </ul>
         */
        void onActive();

        /**
         * Called when the computer control session enters the blocked state.
         *
         * <p>When the session is blocked, the application will generally not be able to interact
         * with or access the content of the session:
         *
         * <ul>
         *   <li>Interactions with the session (e.g. {@link #tap(int, int)} are NOT allowed.
         *   <li>Taking screenshots of the content using {@link #getScreenshot()} is NOT allowed.
         *   <li>Getting Accessibility windows for the session using
         *     {@link #getAccessibilityWindows()} is NOT allowed.
         * </ul>
         *
         * <p>However, users can still interact with the contents of the session using the
         * interactive mirror. The application may choose to guide users to take over the session
         * using either the {@link #handOverApplications()} API or the interactive mirror.
         *
         * @param reason the reason that the session initially entered the blocked
         *               state.
         */
        // TODO: b/441475896: Block interactions and screenshots for
        //  BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH. Until then, a dialog indicating the blockage
        //  will show up in the session, and the agent must dismiss it by interacting with the
        //  session to exit the blocked state.
        void onBlocked(@SessionBlockReason int reason);

        /**
         * Called when the computer control session is closed. This marks the end of the session's
         * lifecycle, and no further lifecycle updates will take place.
         */
        void onClosed(@SessionCloseReason int reason);
    }
}
