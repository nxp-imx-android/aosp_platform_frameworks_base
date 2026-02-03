/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.insight.interaction;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FeedbackRequestTest {

    @Test
    public void testBasics() {
        final FeedbackRequest feedback = new FeedbackRequest(List.of(
                new FeedbackRequest.FeedbackField(
                        FeedbackRequest.FEEDBACK_TYPE_UNKNOWN, "a", "A", null),
                new FeedbackRequest.FeedbackField(
                        FeedbackRequest.FEEDBACK_TYPE_APPROVAL, "b", "B", null),
                new FeedbackRequest.FeedbackField(
                        FeedbackRequest.FEEDBACK_TYPE_DESCRIPTION, "c", "C", null)));

        assertThat(feedback.getFields()).hasSize(3);
        assertThat(feedback.getFields().get(0).getKey()).isEqualTo("a");
        assertThat(feedback.getFields().get(1).getKey()).isEqualTo("b");
        assertThat(feedback.getFields().get(2).getKey()).isEqualTo("c");
    }
}
