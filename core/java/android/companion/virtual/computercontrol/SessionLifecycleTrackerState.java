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

package android.companion.virtual.computercontrol;

/**
 * State definitions for {@link SessionLifecycleTracker}.
 * @hide
 */
public sealed interface SessionLifecycleTrackerState {

    /** The active state, where the agent can see and interact with the session contents. */
    final class Active implements SessionLifecycleTrackerState {
        private Active() {
        }
    }

    /** Singleton object for the Active state. */
    SessionLifecycleTrackerState ACTIVE = new Active();

    /** State indicating the session has been closed. */
    final class Closed implements SessionLifecycleTrackerState {
        public final @ComputerControlSession.SessionCloseReason int reason;

        Closed(@ComputerControlSession.SessionCloseReason int reason) {
            this.reason = reason;
        }
    }
}
