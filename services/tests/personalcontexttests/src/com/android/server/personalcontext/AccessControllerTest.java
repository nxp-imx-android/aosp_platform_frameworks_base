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

package com.android.server.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

/**
 * Tests covering the functionality of {@link AccessController}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AccessControllerTest {
    @Test
    public void testHintPublishingPackage() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.hasAccess(allowedPackage, AccessController.ACCESS_PUBLISH_HINTS))
                .isTrue();

        assertThat(controller.hasAccess(deniedPackage, AccessController.ACCESS_PUBLISH_HINTS))
                .isFalse();
    }

    @Test
    public void testHintReceivingPackage() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.hasAccess(allowedPackage, AccessController.ACCESS_RECEIVE_HINTS))
                .isTrue();

        assertThat(controller.hasAccess(deniedPackage, AccessController.ACCESS_RECEIVE_HINTS))
                .isFalse();
    }

    @Test
    public void testsInsightPublishingPackage() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedInsightPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.hasAccess(allowedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS))
                .isTrue();

        assertThat(controller.hasAccess(deniedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS))
                .isFalse();
    }

    @Test
    public void testsInsightReceivingPackage() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedInsightReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.hasAccess(allowedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS))
                .isTrue();

        assertThat(controller.hasAccess(deniedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS))
                .isFalse();
    }

    @Test
    public void testMultipleAllowedPackage() {
        final String allowedPackage = "com.foo.baz";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintReceivers(Set.of(allowedPackage))
                .setAllowedInsightPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.hasAccess(allowedPackage,
                AccessController.ACCESS_PUBLISH_INSIGHTS
                        | AccessController.ACCESS_RECEIVE_HINTS))
                .isTrue();

        assertThat(controller.hasAccess(allowedPackage,
                AccessController.ACCESS_PUBLISH_INSIGHTS
                        | AccessController.ACCESS_RECEIVE_HINTS
                        | AccessController.ACCESS_RECEIVE_INSIGHTS))
                .isFalse();
    }

    private static class AccessControllerBuilder {
        private final Context mContext = mock(Context.class);
        private final Resources mResources = mock(Resources.class);

        AccessControllerBuilder() {
            when(mContext.getResources()).thenReturn(mResources);

            setAllowedHintPublishers(Collections.emptySet());
            setAllowedHintReceivers(Collections.emptySet());
            setAllowedInsightPublishers(Collections.emptySet());
            setAllowedInsightReceivers(Collections.emptySet());
        }

        private void setStringsToResource(Set<String> strings, int resourceId) {
            final String[] stringArray = new String[strings.size()];
            strings.toArray(stringArray);
            when(mResources.getStringArray(eq(resourceId))).thenReturn(stringArray);
        }

        public AccessControllerBuilder setAllowedHintPublishers(Set<String> publishers) {
            setStringsToResource(publishers,
                    R.array.config_allowlistPersonalContextHintPublishing);

            return this;
        }

        public AccessControllerBuilder setAllowedHintReceivers(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextHintReceiving);

            return this;
        }

        public AccessControllerBuilder setAllowedInsightPublishers(Set<String> publishers) {
            setStringsToResource(publishers,
                    R.array.config_allowlistPersonalContextInsightPublishing);

            return this;
        }

        public AccessControllerBuilder setAllowedInsightReceivers(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextInsightReceiving);

            return this;
        }

        public AccessController build() {
            return new AccessController(mContext);
        }
    }
}
