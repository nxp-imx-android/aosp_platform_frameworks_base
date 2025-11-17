/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.hint;

import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.util.Size;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AutofillInlineRequestHintTest {

    private static final InlinePresentationSpec INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();

    @Test
    public void testAutofillInlineRequestHint_parcelUnparcel() {
        final int sessionId = 6;
        final int taskId = 7;
        final Instant requestTimestamp = Instant.ofEpochSecond(500);
        final ComponentName activityComponent = new ComponentName("test_package", "class");
        final AutofillId focusedId = new AutofillId(3);
        final AutofillValue autofillValue = AutofillValue.forText("test");
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(INLINE_PRESENTATION_SPEC)).build();

        final AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder()
                        .setSessionId(sessionId)
                        .setTaskId(taskId)
                        .setRequestTimestamp(requestTimestamp)
                        .setActivityComponent(activityComponent)
                        .setFocusedId(focusedId)
                        .setAutofillValue(autofillValue)
                        .setInlineSuggestionsRequest(inlineSuggestionsRequest)
                        .build();

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(AutofillInlineRequestHint.class);

        final AutofillInlineRequestHint outputAutofillHint = (AutofillInlineRequestHint) outputHint;
        assertThat(outputAutofillHint.getSessionId()).isEqualTo(sessionId);
        assertThat(outputAutofillHint.getTaskId()).isEqualTo(taskId);
        assertThat(outputAutofillHint.getRequestTimestamp()).isEqualTo(requestTimestamp);
        assertThat(outputAutofillHint.getActivityComponent()).isEqualTo(activityComponent);
        assertThat(outputAutofillHint.getFocusedId()).isEqualTo(focusedId);
        assertThat(outputAutofillHint.getAutofillValue()).isEqualTo(autofillValue);
        assertThat(outputAutofillHint.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest);

        assertThat(outputAutofillHint).isEqualTo(hint);
        // TODO(b/459608398): enable testing hashCode once issue is fixed
        // assertThat(outputAutofillHint.hashCode()).isEqualTo(hint.hashCode());
    }
}
