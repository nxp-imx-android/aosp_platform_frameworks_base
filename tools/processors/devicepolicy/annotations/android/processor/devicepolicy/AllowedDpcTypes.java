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

package android.processor.devicepolicy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes the DPC types that are allowed to set this policy.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface AllowedDpcTypes {
    /**
     * Value used indicate that DPCs of the given type are allowed to set the policy.
     */
    public static final int ALLOWED = 1;
    /**
     * Value used indicate that DPCs of the given type are not allowed to set the policy.
     */
    public static final int DISALLOWED = 2;

    public int defaultDeviceOwner();
    public int financedDeviceOwner();
    public int profileOwnerOfOrganizationOwnedDevice();
    public int profileOwnerOnUser0();
    /**
     * When set to {@link #ALLOWED}, the policy can be set by a profile owner.
     * Note that this only applies to profile owners that fall not in any of the other
     * profile owner categories, for example affiliated profile owners are not covered by this.
     */
    public int profileOwner();
    public int profileOwnerOnUser();
    public int affiliatedProfileOwnerOnUser();
}
