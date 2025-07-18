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

package android.app.admin;

import static java.util.function.Predicate.not;

import android.annotation.Nullable;
import android.app.role.RoleManager;

import java.util.List;
import java.util.stream.Stream;

/**
 * Class that contains information about the admins that are enforcing a specific policy.
 *
 * @hide
 */
public class PolicyEnforcementInfo {
    // Contains all admins who has enforced the policy. The supervision admin will be first on
    // the list, if there's any.
    private final List<EnforcingAdmin> mAllAdmins;

    /**
     * @hide
     */
    public PolicyEnforcementInfo(List<EnforcingAdmin> enforcingAdmins) {
        // Add any supervisor admins first.
        Stream<EnforcingAdmin> supervisorAdmins = enforcingAdmins.stream().filter(
                PolicyEnforcementInfo::isSupervisionRole);
        // Add all other admins afterwards. Only supervisor admin will be added first, for others
        // the order doesn't matter.
        Stream<EnforcingAdmin> otherAdmins = enforcingAdmins.stream().filter(
                not(PolicyEnforcementInfo::isSupervisionRole));
        mAllAdmins = Stream.concat(supervisorAdmins, otherAdmins).toList();
    }

    /**
     * @hide
     */
    public List<EnforcingAdmin> getAllAdmins() {
        return mAllAdmins;
    }


    /**
     * @hide
     */
    public boolean isOnlyEnforcedBySystem() {
        return mAllAdmins.stream().allMatch(PolicyEnforcementInfo::isSystemAuthority);
    }

    /**
     * Returns one EnforcingAdmin from all admins that enforced the policy. If there is a
     * supervision admin, returns that admin as supervision admins have higher priority due to
     * regulations (b/392057517). If there are no admins enforcing the particular policy on device,
     * will return null.
     *
     * @hide
     */
    @Nullable
    public EnforcingAdmin getMostImportantEnforcingAdmin() {
        // Returns the first admin if the list is not empty.
        return mAllAdmins.isEmpty() ? null : mAllAdmins.getFirst();
    }

    private static boolean isSystemAuthority(EnforcingAdmin enforcingAdmin) {
        return enforcingAdmin.getAuthority() instanceof SystemAuthority;
    }

    private static boolean isSupervisionRole(EnforcingAdmin enforcingAdmin) {
        if (!(enforcingAdmin.getAuthority() instanceof RoleAuthority)) {
            return false;
        }
        return ((RoleAuthority) enforcingAdmin.getAuthority()).getRoles().contains(
                RoleManager.ROLE_SYSTEM_SUPERVISION);
    }
}
