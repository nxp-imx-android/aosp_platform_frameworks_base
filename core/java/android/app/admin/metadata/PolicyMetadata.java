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

package android.app.admin.metadata;

import android.app.admin.PolicyIdentifier;

import java.util.Set;

/**
 * Class that contains static information about a policy.
 *
 * @param <T> The type for this policy.
 * @hide
 */
public abstract class PolicyMetadata<T> {
    private final PolicyIdentifier<T> mId;
    private final Set<Integer> mAllowedScopes;
    private final int mAffectedResource;

    public PolicyMetadata(
            PolicyIdentifier<T> id,
            Set<Integer> allowedScopes,
            int affectedResource) {
        this.mId = id;
        this.mAllowedScopes = allowedScopes;
        this.mAffectedResource = affectedResource;
    }

    public PolicyIdentifier<T> getId() {
        return mId;
    }

    public Set<Integer> getAllowedScopes() {
        return mAllowedScopes;
    }

    public int getAffectedResource() {
        return mAffectedResource;
    }

    protected String toAttributes() {
        return "mId=" + mId
                + ", mAllowedScopes=" + mAllowedScopes
                + ", mAffectedResource=" + mAffectedResource;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + toAttributes()
                + '}';
    }
}
