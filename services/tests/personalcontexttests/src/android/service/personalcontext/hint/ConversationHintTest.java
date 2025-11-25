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

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.ActivityId;
import android.content.ComponentName;
import android.os.Binder;
import android.service.personalcontext.hint.ConversationEvent.ConversationEnterEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationExitEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationProcessingEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationUpdateEvent;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ConversationHintTest {
    private static final String CONVERSATION_SESSION_ID = "session_id";
    private static final AutofillId AUTOFILL_ID = new AutofillId(1);
    private static final String CONTENT_DESCRIPTION = "content description";
    private static final Instant REFERENCE_TIME = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final ChatMessageData CHAT_MESSAGE_DATA =
            new ChatMessageData.Builder()
                    .setOutgoingMessage(true)
                    .setText("text")
                    .setAuthor("author")
                    .setReferenceTime(REFERENCE_TIME)
                    .setAutofillId(AUTOFILL_ID)
                    .setTimeText("12:00 PM")
                    .setDateText("Today")
                    .setContentDescription(CONTENT_DESCRIPTION)
                    .build();

    @Test
    public void testConversationHint_enterEvent_parcelUnparcel() {
        final Instant enterTimestamp = REFERENCE_TIME;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(CONVERSATION_SESSION_ID, enterTimestamp, enterTimestamp);
        final ConversationHint hint = new ConversationHint.Builder(enterEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationEnterEvent.class);

        final ConversationEnterEvent outputEnterEvent = (ConversationEnterEvent) outputEvent;
        assertThat(outputEnterEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputEnterEvent.getConversationEnterTimestamp()).isEqualTo(enterTimestamp);
    }

    @Test
    public void testConversationHint_exitEvent_parcelUnparcel() {
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(CONVERSATION_SESSION_ID, REFERENCE_TIME);
        final ConversationHint hint = new ConversationHint.Builder(exitEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationExitEvent.class);

        final ConversationExitEvent outputExitEvent = (ConversationExitEvent) outputEvent;
        assertThat(outputExitEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
    }

    @Test
    public void testConversationHint_processingEvent_parcelUnparcel() {
        final Instant processingTimestamp = REFERENCE_TIME;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        processingTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ConversationHint hint = new ConversationHint.Builder(processingEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationProcessingEvent.class);

        final ConversationProcessingEvent outputProcessingEvent =
                (ConversationProcessingEvent) outputEvent;
        assertThat(outputProcessingEvent.getStartProcessingTimestamp())
                .isEqualTo(processingTimestamp);
        assertThat(outputProcessingEvent.getMessageAutofillId()).isEqualTo(messageAutofillId);
        assertThat(outputProcessingEvent.getConversationSessionId())
                .isEqualTo(CONVERSATION_SESSION_ID);
    }

    @Test
    public void testConversationHint_updateEvent_parcelUnparcel() {
        final ActivityId activityId = new ActivityId(1, null);
        final Instant processingStartTimestamp = REFERENCE_TIME;
        final Instant processingEndTimestamp = REFERENCE_TIME;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setHasNewMessage(true)
                        .setChatMessages(List.of(CHAT_MESSAGE_DATA))
                        .setActivityId(activityId)
                        .build();
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID, REFERENCE_TIME, conversationData);
        final ConversationHint hint = new ConversationHint.Builder(updateEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationUpdateEvent.class);

        final ConversationUpdateEvent outputUpdateEvent = (ConversationUpdateEvent) outputEvent;
        assertThat(outputUpdateEvent.getConversationData()).isEqualTo(conversationData);
        assertThat(outputUpdateEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
    }

    @Test
    public void testHintSignature() throws GeneralSecurityException {
        final ActivityId activityId = new ActivityId(1, new Binder());
        final Instant processingStartTimestamp = REFERENCE_TIME;
        final Instant processingEndTimestamp = REFERENCE_TIME;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(List.of(CHAT_MESSAGE_DATA))
                        .setActivityId(activityId)
                        .setHasNewMessage(true)
                        .build();
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID, REFERENCE_TIME, conversationData);
        final ConversationHint hint = new ConversationHint.Builder(updateEvent).build();

        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ContextHintWithSignature hintWithSignature =
                new ContextHintWithSignature.Builder(hint, key).build();

        assertThat(hintWithSignature.getContextHint()).isEqualTo(hint);
    }
}
