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

package com.android.server.devicepolicy.handlers;

import android.app.admin.PolicyIdentifier;

import com.android.server.devicepolicy.PolicyDefinition;

import java.util.ArrayList;
import java.util.List;

/** {@code PolicyHandlerFactory} constructs all the supported {@link PolicyHandler} instances. */
public class PolicyHandlerFactory {

    public static List<PolicyHandler<?>> build() {
        List<PolicyHandler<?>> handlers = new ArrayList<>();

        // Keep the policy handlers sorted, first on type and then on PolicyIdentifier.
        // go/keep-sorted start ignore_prefixes='handlers.add(new '

        // Note: If you are adding a new `EnumStoredAsBooleanPolicyHandler` *for a user restriction*
        //       that has to go in
        //       {@code DevicePolicyManagerService.createPolicyHandlersDependingOnDpms()}.
        handlers.add(
                new EnumStoredAsBooleanPolicyHandler(
                        PolicyIdentifier.SCREEN_CAPTURE,
                        PolicyDefinition.SCREEN_CAPTURE_DISABLED,
                        /* trueValue= */ PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED,
                        /* falseValue= */ PolicyIdentifier.SCREEN_CAPTURE_ALLOWED));
        handlers.add(new PolicyHandler<Integer>(PolicyIdentifier.AUTO_TIME));
        handlers.add(new PolicyHandler<Integer>(PolicyIdentifier.AUTO_TIME_ZONE));
        handlers.add(
                new PolicyHandler<Integer>(PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_POLICY));
        handlers.add(new PolicyHandler<List<String>>(PolicyIdentifier.CONTENT_RESTRICTION_APPS));
        handlers.add(new PolicyHandler<String>(PolicyIdentifier.LOCKSCREEN_MESSAGE));
        // go/keep-sorted end

        return handlers;
    }
}
