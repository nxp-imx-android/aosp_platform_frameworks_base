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

package android.app.appfunctions;

import android.annotation.NonNull;
import android.content.pm.SignedPackage;

import java.util.List;

/**
 * @hide
 */
public interface AppFunctionAccessServiceInterface {

    /** check access */
    boolean checkAppFunctionAccess(@NonNull String agentPackageName, int agentUserId,
            @NonNull String targetPackageName, int targetUserId);

    /** check access, but also informs if access is invalid */
    @AppFunctionManager.AppFunctionAccessState
    int getAppFunctionAccessRequestState(@NonNull String agentPackageName, int agentUserId,
            @NonNull String targetPackageName, int targetUserId);

    /** get flags for a given target and agent */
    @AppFunctionManager.AppFunctionAccessFlags
    int getAppFunctionAccessFlags(@NonNull String agentPackageName, int agentUserId,
            @NonNull String targetPackageName, int targetUserId);

    /** update flags for a given target and agent */
    boolean updateAppFunctionAccessFlags(@NonNull String agentPackageName, int agentUserId,
            @NonNull String targetPackageName, int targetUserId,
            @AppFunctionManager.AppFunctionAccessFlags int flagMask,
            @AppFunctionManager.AppFunctionAccessFlags int flags) throws IllegalArgumentException;

    /** update the agent allowlist */
    void setAgentAllowlist(@NonNull List<SignedPackage> agentAllowlist);
}
