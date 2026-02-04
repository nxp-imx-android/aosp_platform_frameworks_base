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

package com.android.server.security.authenticationpolicy.agent;

/** Internal interface for the AgentAuthService. */
public interface AgentAuthServiceInternal {

    /**
     * Checks if the agent can perform automation at this time.
     *
     * @param userId user id
     * @param associationId CDM association id of the agent connection
     * @return current status
     */
    boolean isAgentAuthorized(int userId, int associationId);
}
